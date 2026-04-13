package ratscrewd.ui;

import ratscrewd.game.GameController;
import ratscrewd.game.GameController.GameState;
import ratscrewd.model.Card;
import ratscrewd.players.Player;
import ratscrewd.util.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Main game rendering panel.
 *
 * <h3>New in this revision</h3>
 * <ul>
 *   <li>{@link #drawTiebreakerOverlay}   — 3 face-up cards each, scores, winner.</li>
 *   <li>{@link #drawRoundTransitionOverlay} — "Round X complete!", next-round AI difficulty.</li>
 *   <li>{@link #drawGameOverOverlay}     — final game-over screen.</li>
 *   <li>{@link #drawWarningBanner}       — pulsing 1–5 cycle warning (unchanged).</li>
 *   <li>AI think-time, slap accuracy, and double-aggression all scale with round.</li>
 *   <li>ESC or P resumes from pause; main menu exit is only available via the pause menu.</li>
 * </ul>
 */
public class GamePanel extends JPanel implements KeyListener, MouseListener {

    private final GameController controller;
    private int selectedCardIndex  = -1;
    private Timer pendingAISlapTimer = null;
    private Timer pendingAITurnTimer = null;
    private Timer dealingAnimationTimer = null;

    // ── Store interaction ──────────────────────────────────────────────────
    private int         storeSelectedIndex  = -1;
    private int         storePackSelectedIndex = -1;   // which pack slot is highlighted
    private Rectangle[] storeCardRects      = new Rectangle[0];
    private Rectangle[] storePackRects      = new Rectangle[2];
    private Rectangle   storeRerollRect     = null;
    private Rectangle   storeContinueRect   = null;

    // ── Pack opening state machine ─────────────────────────────────────────
    private enum PackPhase { NONE, OPENING, SELECTING, CLOSING }
    private PackPhase     packPhase          = PackPhase.NONE;
    private List<Card>    openedPackCards    = new ArrayList<>();
    private final java.util.LinkedHashSet<Integer> packSelectedSet = new java.util.LinkedHashSet<>();
    private Rectangle[]   packCardPickRects  = new Rectangle[4];
    private Rectangle     packConfirmRect    = null;
    private int           packTooltipIndex   = -1;   // card in selection UI showing tooltip
    private float         packAnimProgress   = 0f;
    private Timer         packAnimTimer      = null;

    // ── Pause menu ─────────────────────────────────────────────────────────
    private boolean paused           = false;
    private String  saveStatusMsg    = "";   // shown inside the pause overlay
    private boolean saveStatusOk     = true; // true = green, false = red
    private Rectangle pauseResumeRect   = null;
    private Rectangle pauseSaveRect     = null;
    private Rectangle pauseMenuRect     = null;

    // ── Dealing animation ──────────────────────────────────────────────────
    private final List<AnimatedCard> animatedCards = new ArrayList<>();
    private int dealingSequence = 0;
    private static final int DEAL_ANIMATION_STEPS = 20;
    private static final int DEAL_DELAY_MS        = 15;

    // ── Warning-banner pulse ───────────────────────────────────────────────
    private float warningPulse          = 1.0f;
    private float warningPulseDirection = -0.03f;
    private Timer warningPulseTimer     = null;

    // ══════════════════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════════════════

    /** Standard constructor — initialises a brand-new game. */
    public GamePanel(GameController controller) {
        this.controller = controller;
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setLayout(null);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        controller.initRun();
        startDealingAnimation();
        startWarningPulse();
    }

    /**
     * Constructor used when continuing a saved game.
     * The controller is already fully restored by loadFromFile() — skip initRun()
     * and the dealing animation since cards are already in players' hands.
     *
     * @param controller a controller restored via GameController.loadFromFile()
     * @param fromSave   must be true; distinguishes this overload at the call site
     */
    public GamePanel(GameController controller, boolean fromSave) {
        this.controller = controller;
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setLayout(null);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        // No initRun(), no dealing animation — state is already live
        startWarningPulse();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Dealing animation
    // ══════════════════════════════════════════════════════════════════════

    private class AnimatedCard {
        int startX, startY, endX, endY, currentStep, totalSteps;

        AnimatedCard(int sx, int sy, int ex, int ey, int steps) {
            startX = sx; startY = sy; endX = ex; endY = ey; totalSteps = steps;
        }

        int getCurrentX() {
            float p = (float) currentStep / totalSteps;
            return (int) (startX + (endX - startX) * p);
        }

        int getCurrentY() {
            float p = (float) currentStep / totalSteps;
            return (int) (startY + (endY - startY) * p);
        }

        boolean isComplete() { return currentStep >= totalSteps; }
        void    step()       { currentStep++; }
    }

    private void startDealingAnimation() {
        dealingAnimationTimer = new Timer(DEAL_DELAY_MS, evt -> {
            animatedCards.forEach(AnimatedCard::step);
            animatedCards.removeIf(card -> {
                if (card.isComplete()) {
                    controller.dealNextCard();
                    ratscrewd.util.SoundManager.playSound("cardplay.wav");
                    return true;
                }
                return false;
            });
            if (animatedCards.isEmpty() && controller.isDealing()) {
                startNextCardAnimation();
            }
            if (!controller.isDealing() && animatedCards.isEmpty()) {
                dealingAnimationTimer.stop();
                dealingAnimationTimer = null;
            }
            repaint();
        });
        dealingAnimationTimer.start();
    }

    private void startNextCardAnimation() {
        int playerIndex = dealingSequence % 2;
        dealingSequence++;
        int destX, destY;
        int m = Constants.SIDE_MARGIN;
        int startX = m + Constants.CARD_WIDTH + m;
        int endX   = Constants.WINDOW_WIDTH - m - Constants.CARD_WIDTH - m;

        if (playerIndex == 0) {
            int pos     = controller.getPlayers().get(0).getHand().size();
            int spacing = (pos > 0) ? (endX - startX) / (pos * 2) : 0;
            destX = startX + pos * spacing;
            destY = Constants.HAND_Y_OFFSET;
        } else {
            int pos     = controller.getPlayers().get(1).getHand().size();
            int spacing = (pos > 0) ? (endX - startX) / (pos * 2) : 0;
            destX = startX + pos * spacing;
            destY = Constants.AI_HAND_Y_OFFSET;
        }
        animatedCards.add(new AnimatedCard(
            Constants.CENTER_PILE_X, Constants.CENTER_PILE_Y, destX, destY,
            DEAL_ANIMATION_STEPS));
    }

    /** Stops any active dealing timer and kicks off a fresh one for a new round. */
    private void triggerNextRound() {
        cancelPendingAISlap();
        cancelPendingAITurn();
        if (dealingAnimationTimer != null && dealingAnimationTimer.isRunning()) {
            dealingAnimationTimer.stop();
            dealingAnimationTimer = null;
        }
        animatedCards.clear();
        dealingSequence = 0;
        controller.beginNextRound();   // increments currentRound, resets all state
        startDealingAnimation();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Warning-banner pulse
    // ══════════════════════════════════════════════════════════════════════

    private void startWarningPulse() {
        warningPulseTimer = new Timer(30, evt -> {
            warningPulse += warningPulseDirection;
            if (warningPulse <= 0.55f) { warningPulse = 0.55f; warningPulseDirection =  0.03f; }
            if (warningPulse >= 1.00f) { warningPulse = 1.00f; warningPulseDirection = -0.03f; }
            if (controller.getWarningCyclesRemaining() > 0) repaint();
        });
        warningPulseTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Painting
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_SPEED);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── Game board (always rendered underneath overlays) ───────────────
        g2.setColor(new Color(0, 100, 0));
        g2.fillRect(0, 0, getWidth(), getHeight());
        drawCenterDeck(g2);
        drawDiscardTop(g2);
        drawAIHands(g2);
        drawHumanHand(g2);
        drawPoints(g2);
        drawAnimatedCards(g2);

        // ── Overlays (mutually exclusive by state) ─────────────────────────
        GameState state = controller.getGameState();
        if (state == GameState.PLAYING) {
            drawWarningBanner(g2);
        } else if (state == GameState.TIEBREAKER) {
            drawTiebreakerOverlay(g2);
        } else if (state == GameState.ROUND_TRANSITION) {
            drawRoundTransitionOverlay(g2);
        } else if (state == GameState.STORE) {
            drawStoreOverlay(g2);
            // Pack animation sits on top of the store when active
            if (packPhase != PackPhase.NONE) {
                drawPackOpeningOverlay(g2);
            }
        // Pause sits on top of all other states except GAME_OVER
        }
        if (paused && state != GameState.GAME_OVER) {
            drawPauseOverlay(g2);
        } else if (state == GameState.GAME_OVER) {
            drawGameOverOverlay(g2);
        }
    }

    // ── Board components ───────────────────────────────────────────────────

    private void drawAnimatedCards(Graphics2D g) {
        for (AnimatedCard card : animatedCards) drawCardBack(g, card.getCurrentX(), card.getCurrentY());
    }

    private void drawCenterDeck(Graphics2D g) {
        if (controller.getCenterDeckSize() > 0) {
            drawCardBack(g, Constants.CENTER_PILE_X, Constants.CENTER_PILE_Y);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(Constants.CENTER_PILE_X, Constants.CENTER_PILE_Y,
                       Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
        }
    }

    private void drawDiscardTop(Graphics2D g) {
        Card top = controller.getDiscardPileTop();
        int x = Constants.CENTER_PILE_X + Constants.CARD_WIDTH + Constants.SIDE_MARGIN;
        int y = Constants.CENTER_PILE_Y;
        if (top != null) {
            drawCardFace(g, top, x, y);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(x, y, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
        }
        if (controller.isSlappable()) {
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(3));
            g.drawRect(x - 2, y - 2, Constants.CARD_WIDTH + 4, Constants.CARD_HEIGHT + 4);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawAIHands(Graphics2D g) {
        List<Player> players = controller.getPlayers();
        int m      = Constants.SIDE_MARGIN;
        int startX = m + Constants.CARD_WIDTH + m;
        int endX   = Constants.WINDOW_WIDTH - m - Constants.CARD_WIDTH - m;
        for (int idx = 1; idx < players.size(); idx++) {
            Player p    = players.get(idx);
            List<Card> hand = p.getHand();
            int size = hand.size();
            if (size == 0) { drawPersonalPileOutline(g, endX + m, Constants.AI_HAND_Y_OFFSET, p); continue; }
            int availW  = endX - startX;
            int spacing = size > 1 ? availW / ((size - 1) * 2) : 0;
            for (int j = 0; j < size; j++) drawCardBack(g, startX + j * spacing, Constants.AI_HAND_Y_OFFSET);
            drawPersonalPile(g, endX + m, Constants.AI_HAND_Y_OFFSET, p);
        }
    }

    private void drawHumanHand(Graphics2D g) {
        Player human    = controller.getPlayers().get(0);
        List<Card> hand = human.getHand();
        int size    = hand.size();
        int m       = Constants.SIDE_MARGIN;
        int startX  = m + Constants.CARD_WIDTH + m;
        int endX    = Constants.WINDOW_WIDTH - m - Constants.CARD_WIDTH - m;
        int availW  = endX - startX;
        int spacing = size > 1 ? availW / ((size - 1) * 2) : 0;

        // How many pixels the selected card rises above the rest of the hand.
        final int LIFT = 16;

        for (int i = 0; i < size; i++) {
            int x = startX + i * spacing;
            boolean selected = (i == selectedCardIndex);
            int y = selected ? Constants.HAND_Y_OFFSET - LIFT : Constants.HAND_Y_OFFSET;
            drawCardFace(g, hand.get(i), x, y);

            if (selected) {
                // ── Blue highlight box (aligned to the lifted card) ───────────
                g.setColor(Color.BLUE);
                g.setStroke(new BasicStroke(3));
                g.drawRect(x - 2, y - 2, Constants.CARD_WIDTH + 4, Constants.CARD_HEIGHT + 4);
                g.setStroke(new BasicStroke(1));

                // ── Tooltip ───────────────────────────────────────────────────
                Card card     = hand.get(i);
                String val    = card.getValue().name();
                String suit   = card.getSuit().name();
                int    pts    = card.getValue().getPoints();
                String ptsStr = pts == 1 ? "1 point" : pts + " points";
                // Capitalise first letter only for a cleaner look
                val  = val.charAt(0) + val.substring(1).toLowerCase();
                suit = suit.charAt(0) + suit.substring(1).toLowerCase();
                String line1 = val + " of " + suit;
                String line2 = "Worth " + ptsStr;

                Font tipFont = new Font("SansSerif", Font.BOLD, 12);
                g.setFont(tipFont);
                FontMetrics fm = g.getFontMetrics();
                int tw   = Math.max(fm.stringWidth(line1), fm.stringWidth(line2));
                int boxW = tw + 16;   // 8px padding on each side
                int boxH = fm.getHeight() * 2 + 10;   // two lines + vertical padding
                int tx   = x + (Constants.CARD_WIDTH - boxW) / 2;
                int ty   = y - boxH - 6;   // 6px gap above the card

                // Keep tooltip on screen horizontally
                tx = Math.max(m, Math.min(tx, Constants.WINDOW_WIDTH - m - boxW));

                // Background — 50% opacity black, rounded corners
                g.setColor(new Color(0, 0, 0, 128));
                g.fillRoundRect(tx, ty, boxW, boxH, 10, 10);

                // Text — white, two lines centred in the box
                g.setColor(Color.WHITE);
                g.drawString(line1, tx + (boxW - fm.stringWidth(line1)) / 2,
                             ty + fm.getAscent() + 4);
                g.drawString(line2, tx + (boxW - fm.stringWidth(line2)) / 2,
                             ty + fm.getAscent() + 4 + fm.getHeight());
            }
        }
        drawPersonalPile(g, endX + m, Constants.HAND_Y_OFFSET, human);
    }

    /** Draws the combined round + points info box, anchored to the centre-right of the screen. */
    private void drawPoints(Graphics2D g) {
        int pts = controller.getPlayers().get(0).getPoints();
        String roundLine  = "Round " + controller.getCurrentRound();
        String pointsLine = "Points: " + pts;

        Font  font = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int padX  = 12;   // horizontal padding inside the box
        int padY  = 8;    // vertical padding inside the box
        int lineGap = 4;  // gap between the two text lines

        int textW = Math.max(fm.stringWidth(roundLine), fm.stringWidth(pointsLine));
        int boxW  = textW + padX * 2;
        int boxH  = fm.getHeight() * 2 + lineGap + padY * 2;

        // Anchor: centre-right, with a fixed buffer from the screen edge
        int buffer = 16;
        int bx = Constants.WINDOW_WIDTH - buffer - boxW;
        int by = (Constants.WINDOW_HEIGHT - boxH) / 2;

        // Background — same style as the card tooltip
        g.setColor(new Color(0, 0, 0, 128));
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);

        // Text — white, each line centred in the box
        g.setColor(Color.WHITE);
        g.drawString(roundLine,
                     bx + (boxW - fm.stringWidth(roundLine))  / 2,
                     by + padY + fm.getAscent());
        g.drawString(pointsLine,
                     bx + (boxW - fm.stringWidth(pointsLine)) / 2,
                     by + padY + fm.getAscent() + fm.getHeight() + lineGap);
    }

    // drawRoundIndicator has been merged into drawPoints above.

    private void drawPersonalPileOutline(Graphics2D g, int x, int y, Player p) {
        if (p.getPersonalDrawPileSize() == 0) {
            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(x, y, Constants.CARD_WIDTH, Constants.CARD_HEIGHT);
        }
    }

    private void drawPersonalPile(Graphics2D g, int x, int y, Player p) {
        if (p.getPersonalDrawPileSize() > 0) drawCardBack(g, x, y);
        else                                  drawPersonalPileOutline(g, x, y, p);
    }

    private void drawCardFace(Graphics g, Card card, int x, int y) {
        g.drawImage(ratscrewd.util.CardImageLoader.getCardImage(card), x, y, null);
    }

    private void drawCardBack(Graphics g, int x, int y) {
        g.drawImage(ratscrewd.util.CardImageLoader.getCardBackImage(), x, y, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Card Store
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Draws the between-round card shop.
     *
     * Layout (all coordinates relative to the overlay box top-left):
     *   Title row     — "Card Shop" + points balance        (~45 px)
     *   Subtitle      — permanent card count hint           (~22 px)
     *   Card row      — up to 6 face-up cards               (card height)
     *   Cost labels   — price below each card               (~22 px)
     *   Tooltip       — above selected card                 (dynamic)
     *   Buttons       — Re-roll and Continue                (~40 px each)
     *
     * Hit rectangles for storeCardRects, storeRerollRect, and storeContinueRect
     * are populated here so that mouseClicked() can reference them without
     * duplicating any layout arithmetic.
     */
    private void drawStoreOverlay(Graphics2D g) {
        List<Card> cards = controller.getStoreCards();
        int cardCount = cards.size();

        final int cw       = Constants.CARD_WIDTH;
        final int ch       = Constants.CARD_HEIGHT;
        final int cardGap  = 14;
        final int maxSlots = 6;
        int rowW  = maxSlots * cw + (maxSlots - 1) * cardGap;
        final int padX = 30;
        final int padY = 18;
        // Pack row extra height: 14 divider gap + 18 header + 8 gap + ch cards + 6 gap + 20 cost + 10 gap
        final int packRowExtra = 14 + 18 + 8 + ch + 6 + 20 + 10;
        int boxW = rowW + padX * 2;
        int boxH = padY + 30 + 6 + 18 + 14
                 + ch + 6 + 20
                 + packRowExtra
                 + 16 + 38 + 14 + 18 + padY;

        int bx = (Constants.WINDOW_WIDTH  - boxW) / 2;
        int by = (Constants.WINDOW_HEIGHT - boxH) / 2;

        // Dim board
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        // Box — deep purple 75% opacity
        g.setColor(new Color(40, 0, 70, 191));
        g.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setColor(new Color(180, 100, 255, 210));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setStroke(new BasicStroke(1));

        // Title row
        int pts = controller.getPlayers().get(0).getPoints();
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(220, 170, 255));
        g.drawString("\uD83C\uDFAA  Card Shop", bx + padX, by + padY + fm.getAscent());
        String ptLabel = "Points: " + pts;
        g.setColor(new Color(255, 220, 100));
        g.drawString(ptLabel, bx + boxW - padX - fm.stringWidth(ptLabel), by + padY + fm.getAscent());

        // Subtitle
        int deckCount = controller.getPermanentCardCount();
        String sub = deckCount == 0 ? "Buy cards to permanently add them to your deck."
                : deckCount + " card" + (deckCount == 1 ? "" : "s") + " added to your deck so far.";
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        g.setColor(new Color(180, 150, 210));
        g.drawString(sub, bx + padX, by + padY + 30 + 6 + fm.getAscent());

        // ── Individual card row ────────────────────────────────────────────
        int cardRowY   = by + padY + 30 + 6 + 18 + 14;
        int cardRowX   = bx + padX;
        int costLabelY = cardRowY + ch + 6;

        storeCardRects = new Rectangle[cardCount];
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        fm = g.getFontMetrics();

        for (int i = 0; i < maxSlots; i++) {
            int slotX = cardRowX + i * (cw + cardGap);
            if (i < cardCount) {
                Card card = cards.get(i);
                storeCardRects[i] = new Rectangle(slotX, cardRowY, cw, ch);
                drawCardFace(g, card, slotX, cardRowY);
                if (i == storeSelectedIndex) {
                    g.setColor(Color.RED);
                    g.setStroke(new BasicStroke(3));
                    g.drawRect(slotX - 2, cardRowY - 2, cw + 4, ch + 4);
                    g.setStroke(new BasicStroke(1));
                }
                int cost = GameController.getCardCost(card);
                String costStr = cost + " pts";
                g.setColor(new Color(255, 220, 100));
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                fm = g.getFontMetrics();
                g.drawString(costStr, slotX + (cw - fm.stringWidth(costStr)) / 2,
                             costLabelY + fm.getAscent());
            } else {
                g.setColor(new Color(255, 255, 255, 30));
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                            1, new float[]{4, 4}, 0));
                g.drawRect(slotX, cardRowY, cw, ch);
                g.setStroke(new BasicStroke(1));
            }
        }

        // Tooltip above selected individual card
        drawStoreCardTooltip(g, cards, cardRowX, cardRowY, bx, boxW, cw, cardGap);

        // ── Card pack section ──────────────────────────────────────────────
        int packSectionY = costLabelY + 20 + 14;

        // Divider line + header
        g.setColor(new Color(180, 100, 255, 80));
        g.drawLine(bx + padX, packSectionY, bx + boxW - padX, packSectionY);
        g.setColor(new Color(200, 150, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        fm = g.getFontMetrics();
        int packsLeft = controller.getPacksRemainingThisVisit();
        String packHeader = "Card Packs  (" + packsLeft + "/2 remaining this visit)";
        g.drawString(packHeader, bx + padX, packSectionY + 6 + fm.getAscent());

        // Two pack slots — right-aligned
        int packCardY    = packSectionY + 6 + 18 + 8;
        int packSlotW    = cw + 20;
        int packSlotH    = ch;
        int packSlotGap  = 14;
        int packTotalW   = 2 * packSlotW + packSlotGap;
        int packStartX   = bx + boxW - padX - packTotalW;
        int packCostLblY = packCardY + packSlotH + 6;

        for (int i = 0; i < 2; i++) {
            int px       = packStartX + i * (packSlotW + packSlotGap);
            boolean used = (i >= packsLeft);
            boolean canAffordPack = controller.getPlayers().get(0).getPoints() >= controller.getPackCost();
            boolean sel  = (i == storePackSelectedIndex);

            Color packBg     = used ? new Color(40, 40, 40, 180) : new Color(50, 0, 90, 230);
            Color packBorder = used ? new Color(80, 80, 80)
                             : sel  ? new Color(255, 200, 50)
                                    : new Color(170, 80, 255);
            g.setColor(packBg);
            g.fillRoundRect(px, packCardY, packSlotW, packSlotH, 8, 8);
            g.setColor(packBorder);
            g.setStroke(sel ? new BasicStroke(3) : new BasicStroke(1.5f));
            g.drawRoundRect(px, packCardY, packSlotW, packSlotH, 8, 8);
            g.setStroke(new BasicStroke(1));

            if (!used) {
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                fm = g.getFontMetrics();
                String star = "\u2728";
                g.setColor(new Color(220, 180, 255));
                g.drawString(star, px + (packSlotW - fm.stringWidth(star)) / 2, packCardY + 30);
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                fm = g.getFontMetrics();
                g.setColor(new Color(200, 160, 255));
                String l1 = "CARD", l2 = "PACK";
                g.drawString(l1, px + (packSlotW - fm.stringWidth(l1)) / 2, packCardY + 50);
                g.drawString(l2, px + (packSlotW - fm.stringWidth(l2)) / 2, packCardY + 63);
                g.setFont(new Font("SansSerif", Font.PLAIN, 9));
                fm = g.getFontMetrics();
                g.setColor(new Color(160, 130, 200));
                String sub2 = "4 random cards";
                g.drawString(sub2, px + (packSlotW - fm.stringWidth(sub2)) / 2, packCardY + 78);
            } else {
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                fm = g.getFontMetrics();
                g.setColor(new Color(120, 120, 120));
                String usedTxt = "USED";
                g.drawString(usedTxt, px + (packSlotW - fm.stringWidth(usedTxt)) / 2,
                             packCardY + packSlotH / 2 + fm.getAscent() / 2);
            }

            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            fm = g.getFontMetrics();
            String packCostStr = used ? "---" : controller.getPackCost() + " pts";
            g.setColor(used ? new Color(100, 100, 100) : new Color(255, 220, 100));
            g.drawString(packCostStr, px + (packSlotW - fm.stringWidth(packCostStr)) / 2,
                         packCostLblY + fm.getAscent());

            storePackRects[i] = new Rectangle(px, packCardY, packSlotW, packSlotH);
        }

        // Tooltip above selected pack slot
        if (storePackSelectedIndex >= 0 && storePackSelectedIndex < 2) {
            int pi = storePackSelectedIndex;
            int px = packStartX + pi * (packSlotW + packSlotGap);
            boolean packAvail = (pi < packsLeft);
            String tl1 = "Card Pack";
            String tl2 = packAvail ? "4 random cards - choose 2 to keep" : "Already purchased";
            String tl3 = packAvail ? "Cost: " + controller.getPackCost() + " pts" : "";
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics tfm = g.getFontMetrics();
            int lines = tl3.isEmpty() ? 2 : 3;
            int tw    = Math.max(tfm.stringWidth(tl1),
                        Math.max(tfm.stringWidth(tl2), tfm.stringWidth(tl3)));
            int tbW   = tw + 16;
            int tbH   = tfm.getHeight() * lines + 10;
            int tx    = px + (packSlotW - tbW) / 2;
            int ty    = packCardY - tbH - 6;
            tx = Math.max(bx + 4, Math.min(tx, bx + boxW - 4 - tbW));
            g.setColor(new Color(0, 0, 0, 128));
            g.fillRoundRect(tx, ty, tbW, tbH, 10, 10);
            int baseY = ty + tfm.getAscent() + 4;
            g.setColor(Color.WHITE);
            g.drawString(tl1, tx + (tbW - tfm.stringWidth(tl1)) / 2, baseY);
            g.setColor(new Color(200, 200, 200));
            g.drawString(tl2, tx + (tbW - tfm.stringWidth(tl2)) / 2, baseY + tfm.getHeight());
            if (!tl3.isEmpty()) {
                g.setColor(new Color(255, 220, 100));
                g.drawString(tl3, tx + (tbW - tfm.stringWidth(tl3)) / 2, baseY + tfm.getHeight() * 2);
            }
        }

        // ── Action buttons ─────────────────────────────────────────────────
        int btnY    = packCostLblY + 20 + 16;
        int btnW    = 220;
        int btnH    = 38;
        int btnReX  = bx + (boxW / 2) - btnW - 10;
        int btnConX = bx + (boxW / 2) + 10;

        // Re-roll
        int rerollCost    = controller.getRerollCost();
        boolean canReroll = controller.getPlayers().get(0).getPoints() >= rerollCost;
        g.setColor(canReroll ? new Color(80, 20, 120, 210) : new Color(50, 50, 50, 180));
        g.fillRoundRect(btnReX, btnY, btnW, btnH, 10, 10);
        g.setColor(canReroll ? new Color(180, 100, 255) : new Color(100, 100, 100));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnReX, btnY, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String rerollLabel = "Re-roll  (" + rerollCost + " pts)";
        g.setColor(canReroll ? Color.WHITE : new Color(140, 140, 140));
        g.drawString(rerollLabel, btnReX + (btnW - fm.stringWidth(rerollLabel)) / 2,
                     btnY + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        storeRerollRect = new Rectangle(btnReX, btnY, btnW, btnH);

        // Continue
        g.setColor(new Color(20, 80, 30, 210));
        g.fillRoundRect(btnConX, btnY, btnW, btnH, 10, 10);
        g.setColor(new Color(80, 200, 100));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnConX, btnY, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        String contLabel = "Continue  \u2192";
        g.drawString(contLabel, btnConX + (btnW - fm.stringWidth(contLabel)) / 2,
                     btnY + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        storeContinueRect = new Rectangle(btnConX, btnY, btnW, btnH);

        // Hint
        g.setColor(new Color(150, 130, 170));
        g.setFont(new Font("SansSerif", Font.ITALIC, 12));
        fm = g.getFontMetrics();
        String hint = "Click a card to select it, press ENTER to buy.  Click a pack to open it.  Press P to pause.";
        g.drawString(hint, bx + (boxW - fm.stringWidth(hint)) / 2,
                     btnY + btnH + 14 + fm.getAscent());
    }

    /** Tooltip helper for individual store cards. */
    private void drawStoreCardTooltip(Graphics2D g, List<Card> cards,
                                      int cardRowX, int cardRowY,
                                      int bx, int boxW, int cw, int cardGap) {
        if (storeSelectedIndex < 0 || storeSelectedIndex >= cards.size()) return;
        Card sel   = cards.get(storeSelectedIndex);
        int  slotX = cardRowX + storeSelectedIndex * (cw + cardGap);
        String val  = sel.getValue().name();
        String suit = sel.getSuit().name();
        int    p2   = sel.getValue().getPoints();
        String pStr = p2 == 1 ? "1 point" : p2 + " points";
        val  = val.charAt(0)  + val.substring(1).toLowerCase();
        suit = suit.charAt(0) + suit.substring(1).toLowerCase();
        String l1 = val + " of " + suit;
        String l2 = "Worth " + pStr;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics tfm = g.getFontMetrics();
        int tw  = Math.max(tfm.stringWidth(l1), tfm.stringWidth(l2));
        int tbW = tw + 16;
        int tbH = tfm.getHeight() * 2 + 10;
        int tx  = slotX + (cw - tbW) / 2;
        int ty  = cardRowY - tbH - 6;
        tx = Math.max(bx + 4, Math.min(tx, bx + boxW - 4 - tbW));
        g.setColor(new Color(0, 0, 0, 128));
        g.fillRoundRect(tx, ty, tbW, tbH, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(l1, tx + (tbW - tfm.stringWidth(l1)) / 2, ty + tfm.getAscent() + 4);
        g.drawString(l2, tx + (tbW - tfm.stringWidth(l2)) / 2, ty + tfm.getAscent() + 4 + tfm.getHeight());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Pack opening animation
    // ══════════════════════════════════════════════════════════════════════

    private static final int PACK_BOX_W       = 480;
    private static final int PACK_BOX_H       = 320;
    private static final int PACK_ANIM_FRAMES = 22;

    private void startPackOpenAnimation(List<Card> packCards) {
        openedPackCards = new ArrayList<>(packCards);
        packSelectedSet.clear();
        packTooltipIndex = -1;
        packAnimProgress = 0f;
        packPhase = PackPhase.OPENING;
        packAnimTimer = new Timer(16, evt -> {
            packAnimProgress += 1f / PACK_ANIM_FRAMES;
            if (packAnimProgress >= 1f) {
                packAnimProgress = 1f;
                packPhase = PackPhase.SELECTING;
                packAnimTimer.stop();
                packAnimTimer = null;
            }
            repaint();
        });
        packAnimTimer.start();
    }

    private void confirmPackSelection() {
        if (packSelectedSet.size() < 2) return;
        controller.addSelectedPackCards(openedPackCards, new ArrayList<>(packSelectedSet));
        ratscrewd.util.SoundManager.playSound("shopbuy.wav");
        packPhase = PackPhase.CLOSING;
        packAnimProgress = 1f;
        packAnimTimer = new Timer(16, evt -> {
            packAnimProgress -= 1f / PACK_ANIM_FRAMES;
            if (packAnimProgress <= 0f) {
                packAnimProgress = 0f;
                packPhase = PackPhase.NONE;
                openedPackCards.clear();
                packSelectedSet.clear();
                packTooltipIndex = -1;
                storePackSelectedIndex = -1;
                packAnimTimer.stop();
                packAnimTimer = null;
            }
            repaint();
        });
        packAnimTimer.start();
    }

    private void drawPackOpeningOverlay(Graphics2D g) {
        int cx = Constants.WINDOW_WIDTH  / 2;
        int cy = Constants.WINDOW_HEIGHT / 2;

        int dimAlpha = packPhase == PackPhase.SELECTING ? 180 : (int)(packAnimProgress * 180);
        g.setColor(new Color(0, 0, 0, dimAlpha));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        if (packPhase == PackPhase.OPENING || packPhase == PackPhase.CLOSING) {
            float raw  = packPhase == PackPhase.CLOSING ? 1f - packAnimProgress : packAnimProgress;
            float ease = raw * raw * (3f - 2f * raw);
            int bw = (int)(30 + (PACK_BOX_W - 30) * ease);
            int bh = (int)(30 + (PACK_BOX_H - 30) * ease);
            int bx = cx - bw / 2;
            int by = cy - bh / 2;

            g.setColor(new Color(160, 60, 255, (int)(ease * 140)));
            g.fillRoundRect(bx - 6, by - 6, bw + 12, bh + 12, 18, 18);
            g.setColor(new Color(25, 8, 55, 245));
            g.fillRoundRect(bx, by, bw, bh, 14, 14);
            g.setColor(new Color(200, 140, 255, (int)(ease * 230)));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(bx, by, bw, bh, 14, 14);
            g.setStroke(new BasicStroke(1));

            if (ease > 0.45f && packPhase == PackPhase.OPENING) {
                float tf = Math.min(1f, (ease - 0.45f) / 0.55f);
                int fontSize = (int)(14 + 14 * ease);
                g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(new Color(220, 170, 255, (int)(tf * 255)));
                String txt = "\u2728  CARD PACK!  \u2728";
                g.drawString(txt, cx - fm.stringWidth(txt) / 2, cy + fm.getAscent() / 2);
            }
        } else if (packPhase == PackPhase.SELECTING) {
            drawPackSelectionUI(g, cx, cy);
        }
    }

    private void drawPackSelectionUI(Graphics2D g, int cx, int cy) {
        int bx = cx - PACK_BOX_W / 2;
        int by = cy - PACK_BOX_H / 2;
        int cw = Constants.CARD_WIDTH;
        int ch = Constants.CARD_HEIGHT;
        int gap = 16;
        int rowW = 4 * cw + 3 * gap;
        int cardStartX = bx + (PACK_BOX_W - rowW) / 2;
        int cardY = by + 80;

        g.setColor(new Color(160, 60, 255, 120));
        g.fillRoundRect(bx - 6, by - 6, PACK_BOX_W + 12, PACK_BOX_H + 12, 18, 18);
        g.setColor(new Color(25, 8, 55, 248));
        g.fillRoundRect(bx, by, PACK_BOX_W, PACK_BOX_H, 14, 14);
        g.setColor(new Color(200, 140, 255, 230));
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(bx, by, PACK_BOX_W, PACK_BOX_H, 14, 14);
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(220, 170, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        String title = "\u2728  Card Pack!  \u2728";
        g.drawString(title, bx + (PACK_BOX_W - fm.stringWidth(title)) / 2, by + 36);

        int selCount = packSelectedSet.size();
        int need = 2 - selCount;
        String instr = need > 0
                ? "Select " + need + " more card" + (need == 1 ? "" : "s") + " to keep"
                : "2 cards selected - press ENTER or click Confirm";
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        g.setColor(new Color(180, 180, 220));
        g.drawString(instr, bx + (PACK_BOX_W - fm.stringWidth(instr)) / 2, by + 60);

        packCardPickRects = new Rectangle[4];
        for (int i = 0; i < 4 && i < openedPackCards.size(); i++) {
            int x = cardStartX + i * (cw + gap);
            drawCardFace(g, openedPackCards.get(i), x, cardY);
            packCardPickRects[i] = new Rectangle(x, cardY, cw, ch);
            if (packSelectedSet.contains(i)) {
                g.setColor(new Color(0, 210, 80));
                g.setStroke(new BasicStroke(3));
                g.drawRect(x - 2, cardY - 2, cw + 4, ch + 4);
                g.setStroke(new BasicStroke(1));
            }
        }

        // Tooltip for focused pack card
        if (packTooltipIndex >= 0 && packTooltipIndex < openedPackCards.size()) {
            Card sel  = openedPackCards.get(packTooltipIndex);
            int  tx0  = cardStartX + packTooltipIndex * (cw + gap);
            String val  = sel.getValue().name();
            String suit = sel.getSuit().name();
            int    p2   = sel.getValue().getPoints();
            String pStr = p2 == 1 ? "1 point" : p2 + " points";
            val  = val.charAt(0) + val.substring(1).toLowerCase();
            suit = suit.charAt(0) + suit.substring(1).toLowerCase();
            String tl1 = val + " of " + suit;
            String tl2 = "Worth " + pStr;
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics tfm = g.getFontMetrics();
            int tw  = Math.max(tfm.stringWidth(tl1), tfm.stringWidth(tl2));
            int tbW = tw + 16;
            int tbH = tfm.getHeight() * 2 + 10;
            int tx  = tx0 + (cw - tbW) / 2;
            int ty  = cardY - tbH - 6;
            tx = Math.max(bx + 4, Math.min(tx, bx + PACK_BOX_W - 4 - tbW));
            g.setColor(new Color(0, 0, 0, 128));
            g.fillRoundRect(tx, ty, tbW, tbH, 10, 10);
            g.setColor(Color.WHITE);
            g.drawString(tl1, tx + (tbW - tfm.stringWidth(tl1)) / 2, ty + tfm.getAscent() + 4);
            g.drawString(tl2, tx + (tbW - tfm.stringWidth(tl2)) / 2, ty + tfm.getAscent() + 4 + tfm.getHeight());
        }

        // Confirm button
        boolean canConfirm = selCount == 2;
        int btnW = 210, btnH = 36;
        int btnX = bx + (PACK_BOX_W - btnW) / 2;
        int btnY = cardY + ch + 18;
        g.setColor(canConfirm ? new Color(30, 110, 30, 230) : new Color(50, 50, 50, 180));
        g.fillRoundRect(btnX, btnY, btnW, btnH, 10, 10);
        g.setColor(canConfirm ? new Color(80, 210, 80) : new Color(100, 100, 100));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        fm = g.getFontMetrics();
        String confirmLabel = canConfirm ? "Keep These Cards" : "Select 2 Cards First";
        g.setColor(canConfirm ? Color.WHITE : new Color(120, 120, 120));
        g.drawString(confirmLabel, btnX + (btnW - fm.stringWidth(confirmLabel)) / 2,
                     btnY + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        packConfirmRect = new Rectangle(btnX, btnY, btnW, btnH);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Pause menu
    // ══════════════════════════════════════════════════════════════════════

    private void drawPauseOverlay(Graphics2D g) {
        // Dim everything behind the menu
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        int boxW = 340;
        int boxH = saveStatusMsg.isEmpty() ? 230 : 262;
        int bx   = (Constants.WINDOW_WIDTH  - boxW) / 2;
        int by   = (Constants.WINDOW_HEIGHT - boxH) / 2;

        // Box background
        g.setColor(new Color(20, 20, 45, 240));
        g.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setColor(new Color(100, 100, 200, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setStroke(new BasicStroke(1));

        // Title
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        String title = "PAUSED";
        g.drawString(title, bx + (boxW - fm.stringWidth(title)) / 2, by + 46);

        // Shared button dimensions
        int btnW = 250;
        int btnH = 40;
        int btnX = bx + (boxW - btnW) / 2;

        // Resume button
        g.setColor(new Color(30, 100, 30, 220));
        g.fillRoundRect(btnX, by + 66, btnW, btnH, 10, 10);
        g.setColor(new Color(80, 200, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnX, by + 66, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        String resumeLabel = "Resume  (P)";
        g.drawString(resumeLabel,
                     btnX + (btnW - fm.stringWidth(resumeLabel)) / 2,
                     by + 66 + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        pauseResumeRect = new Rectangle(btnX, by + 66, btnW, btnH);

        // Save Game button
        g.setColor(new Color(30, 50, 130, 220));
        g.fillRoundRect(btnX, by + 120, btnW, btnH, 10, 10);
        g.setColor(new Color(100, 140, 255));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnX, by + 120, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        String saveLabel = "Save Game";
        g.setColor(Color.WHITE);
        g.drawString(saveLabel,
                     btnX + (btnW - fm.stringWidth(saveLabel)) / 2,
                     by + 120 + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        pauseSaveRect = new Rectangle(btnX, by + 120, btnW, btnH);

        // Exit to Menu button
        g.setColor(new Color(130, 25, 25, 220));
        g.fillRoundRect(btnX, by + 174, btnW, btnH, 10, 10);
        g.setColor(new Color(220, 80, 80));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(btnX, by + 174, btnW, btnH, 10, 10);
        g.setStroke(new BasicStroke(1));
        String menuLabel = "Exit to Main Menu";
        g.setColor(Color.WHITE);
        g.drawString(menuLabel,
                     btnX + (btnW - fm.stringWidth(menuLabel)) / 2,
                     by + 174 + (btnH - fm.getHeight()) / 2 + fm.getAscent());
        pauseMenuRect = new Rectangle(btnX, by + 174, btnW, btnH);

        // Save status message (shown after a save attempt)
        if (!saveStatusMsg.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            fm = g.getFontMetrics();
            g.setColor(saveStatusOk ? new Color(80, 220, 80) : new Color(255, 100, 100));
            g.drawString(saveStatusMsg,
                         bx + (boxW - fm.stringWidth(saveStatusMsg)) / 2,
                         by + 226 + fm.getAscent());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Warning banner
    // ══════════════════════════════════════════════════════════════════════

    private void drawWarningBanner(Graphics2D g) {
        int cyclesLeft = controller.getWarningCyclesRemaining();
        if (cyclesLeft == 0) return;

        Color base;
        if      (cyclesLeft <= 1) base = new Color(210, 30,  30);
        else if (cyclesLeft <= 3) base = new Color(220, 100,  0);
        else                      base = new Color(190, 160,  0);

        int alpha = Math.min(255, (int)(warningPulse * 220));
        Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);

        int bw = 560, bh = 66;
        int bx = (Constants.WINDOW_WIDTH  - bw) / 2;
        int by = (Constants.WINDOW_HEIGHT - bh) / 2;

        g.setColor(new Color(0, 0, 0, (int)(alpha * 0.5f)));
        g.fillRoundRect(bx + 3, by + 3, bw, bh, 14, 14);
        g.setColor(fill);
        g.fillRoundRect(bx, by, bw, bh, 14, 14);
        g.setColor(new Color(255, 255, 255, alpha));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(bx, by, bw, bh, 14, 14);
        g.setStroke(new BasicStroke(1));

        g.setColor(Color.WHITE);
        String cycle = cyclesLeft == 1 ? "1 cycle" : cyclesLeft + " cycles";
        String line1 = "\u26A0  WARNING: " + cycle + " remaining before the round ends!";
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(line1, bx + (bw - fm.stringWidth(line1)) / 2, by + 24);

        String line2 = "No player has gained 5+ cards to their personal pile this cycle.";
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        g.drawString(line2, bx + (bw - fm.stringWidth(line2)) / 2, by + 46);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Tiebreaker
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Shows both players' 3 tiebreaker cards face-up with point totals and
     * the result. Player presses ENTER/SPACE to continue.
     */
    private void drawTiebreakerOverlay(Graphics2D g) {
        // ── Dim board ─────────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        // ── Box ───────────────────────────────────────────────────────────
        int boxW = 600, boxH = 400;
        int bx   = (Constants.WINDOW_WIDTH  - boxW) / 2;
        int by   = (Constants.WINDOW_HEIGHT - boxH) / 2;

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(bx + 5, by + 5, boxW, boxH, 24, 24);
        g.setColor(new Color(15, 15, 45, 248));
        g.fillRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setColor(new Color(255, 215, 0, 220));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setStroke(new BasicStroke(1));

        // ── Title ─────────────────────────────────────────────────────────
        g.setColor(new Color(255, 215, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        String title = "\u2694  TIEBREAKER  \u2694";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, bx + (boxW - fm.stringWidth(title)) / 2, by + 40);

        // ── Card rows ──────────────────────────────────────────────────────
        int cw      = Constants.CARD_WIDTH;
        int ch      = Constants.CARD_HEIGHT;
        int gap     = 12;
        int rowW    = 3 * cw + 2 * gap;
        int cardLeft = bx + (boxW - rowW) / 2;

        // Human cards
        drawTiebreakerRow(g, controller.getTiebreakerHumanCards(), cardLeft, by + 58, cw, ch, gap);

        // AI cards
        drawTiebreakerRow(g, controller.getTiebreakerAICards(), cardLeft, by + 210, cw, ch, gap);

        // ── Score labels ───────────────────────────────────────────────────
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        fm = g.getFontMetrics();

        String yourLabel = "Your Cards";
        g.setColor(new Color(160, 220, 160));
        g.drawString(yourLabel, bx + (boxW - fm.stringWidth(yourLabel)) / 2, by + 57);

        String aiLabel = "AI's Cards";
        g.setColor(new Color(220, 160, 160));
        g.drawString(aiLabel, bx + (boxW - fm.stringWidth(aiLabel)) / 2, by + 209);

        int hs = controller.getTiebreakerHumanScore();
        int as = controller.getTiebreakerAIScore();

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        fm = g.getFontMetrics();
        String hScore = "Your total: " + hs + " pts";
        String aScore = "AI total:   " + as + " pts";
        g.setColor(new Color(200, 240, 200));
        g.drawString(hScore, bx + (boxW - fm.stringWidth(hScore)) / 2, by + 162);
        g.setColor(new Color(240, 200, 200));
        g.drawString(aScore, bx + (boxW - fm.stringWidth(aScore)) / 2, by + 314);

        // ── Divider ────────────────────────────────────────────────────────
        g.setColor(new Color(255, 215, 0, 100));
        g.drawLine(bx + 30, by + 330, bx + boxW - 30, by + 330);

        // ── Winner line ────────────────────────────────────────────────────
        boolean humanWon = controller.isTiebreakerHumanWon();
        String  verdict  = humanWon ? "You win the tiebreaker!" : "AI wins the tiebreaker!";
        Color   vColor   = humanWon ? new Color(80, 255, 120) : new Color(255, 80, 80);
        g.setColor(vColor);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        fm = g.getFontMetrics();
        g.drawString(verdict, bx + (boxW - fm.stringWidth(verdict)) / 2, by + 358);

        // ── Prompt ─────────────────────────────────────────────────────────
        g.setColor(new Color(160, 160, 160));
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        fm = g.getFontMetrics();
        String prompt = "Press ENTER or SPACE to continue";
        g.drawString(prompt, bx + (boxW - fm.stringWidth(prompt)) / 2, by + 386);
    }

    /** Draws a row of 3 face-up cards for the tiebreaker overlay. */
    private void drawTiebreakerRow(Graphics2D g, List<Card> cards, int x, int y,
                                   int cw, int ch, int gap) {
        for (int i = 0; i < cards.size(); i++) {
            drawCardFace(g, cards.get(i), x + i * (cw + gap), y + 12);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Round transition (human wins a round)
    // ══════════════════════════════════════════════════════════════════════

    private void drawRoundTransitionOverlay(Graphics2D g) {
        // ── Dim board ─────────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        // ── Box ───────────────────────────────────────────────────────────
        int boxW = 540, boxH = 280;
        int bx   = (Constants.WINDOW_WIDTH  - boxW) / 2;
        int by   = (Constants.WINDOW_HEIGHT - boxH) / 2;

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(bx + 5, by + 5, boxW, boxH, 24, 24);
        g.setColor(new Color(10, 30, 10, 248));
        g.fillRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setColor(new Color(80, 200, 80, 220));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setStroke(new BasicStroke(1));

        // ── "Round X Complete!" ────────────────────────────────────────────
        g.setColor(new Color(80, 230, 120));
        g.setFont(new Font("SansSerif", Font.BOLD, 44));
        FontMetrics fm = g.getFontMetrics();
        String header = "Round " + controller.getCurrentRound() + " Complete!";
        g.drawString(header, bx + (boxW - fm.stringWidth(header)) / 2, by + 58);

        // ── Reason ────────────────────────────────────────────────────────
        g.setColor(new Color(200, 240, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        fm = g.getFontMetrics();
        String reason = controller.getRoundEndReason();
        if (!reason.isEmpty()) {
            g.drawString(reason, bx + (boxW - fm.stringWidth(reason)) / 2, by + 88);
        }

        // ── Divider ────────────────────────────────────────────────────────
        g.setColor(new Color(80, 200, 80, 80));
        g.drawLine(bx + 30, by + 106, bx + boxW - 30, by + 106);

        // ── Next round info ────────────────────────────────────────────────
        int nextRound = controller.getCurrentRound() + 1;
        g.setColor(new Color(220, 220, 220));
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        fm = g.getFontMetrics();
        String nextLine = "Up Next \u2192  Round " + nextRound;
        g.drawString(nextLine, bx + (boxW - fm.stringWidth(nextLine)) / 2, by + 140);

        // ── Difficulty label ───────────────────────────────────────────────
        String diffLabel = controller.getNextRoundDifficultyLabel();
        Color  diffColor = getDifficultyColor(diffLabel);

        // Measure both segments with their own fonts before drawing either.
        Font plainFont15 = new Font("SansSerif", Font.PLAIN, 15);
        Font boldFont15  = new Font("SansSerif", Font.BOLD,  15);
        g.setFont(plainFont15);
        int prefixW = g.getFontMetrics().stringWidth("AI Difficulty: ");
        g.setFont(boldFont15);
        int labelW  = g.getFontMetrics().stringWidth(diffLabel);
        int lineX   = bx + (boxW - prefixW - labelW) / 2;

        g.setFont(plainFont15);
        g.setColor(new Color(180, 180, 180));
        g.drawString("AI Difficulty: ", lineX, by + 170);
        g.setFont(boldFont15);
        g.setColor(diffColor);
        g.drawString(diffLabel, lineX + prefixW, by + 170);

        // ── Divider ────────────────────────────────────────────────────────
        g.setColor(new Color(80, 200, 80, 80));
        g.drawLine(bx + 30, by + 188, bx + boxW - 30, by + 188);

        // ── Prompt ─────────────────────────────────────────────────────────
        g.setColor(new Color(150, 150, 150));
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        fm = g.getFontMetrics();
        String prompt = "Press ENTER or SPACE to begin Round " + nextRound;
        g.drawString(prompt, bx + (boxW - fm.stringWidth(prompt)) / 2, by + 258);
    }

    private Color getDifficultyColor(String label) {
        switch (label) {
            case "Novice":   return new Color( 80, 220,  80);
            case "Amateur":  return new Color(160, 220,  60);
            case "Skilled":  return new Color(220, 210,  40);
            case "Expert":   return new Color(240, 140,  20);
            case "Veteran":  return new Color(220,  70,  20);
            default:         return new Color(200,  30,  30);  // Master
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Overlay: Game over
    // ══════════════════════════════════════════════════════════════════════

    private void drawGameOverOverlay(Graphics2D g) {
        // ── Dim board ─────────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, 185));
        g.fillRect(0, 0, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);

        // ── Box ───────────────────────────────────────────────────────────
        int boxW = 520, boxH = 250;
        int bx   = (Constants.WINDOW_WIDTH  - boxW) / 2;
        int by   = (Constants.WINDOW_HEIGHT - boxH) / 2;

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(bx + 5, by + 5, boxW, boxH, 24, 24);
        g.setColor(new Color(30, 10, 10, 248));
        g.fillRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setColor(new Color(200, 40, 40, 220));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(bx, by, boxW, boxH, 24, 24);
        g.setStroke(new BasicStroke(1));

        // ── "GAME OVER" ────────────────────────────────────────────────────
        g.setColor(new Color(255, 70, 70));
        g.setFont(new Font("SansSerif", Font.BOLD, 58));
        FontMetrics fm = g.getFontMetrics();
        String header = "GAME OVER";
        g.drawString(header, bx + (boxW - fm.stringWidth(header)) / 2, by + 72);

        // ── Reason ────────────────────────────────────────────────────────
        String reason = controller.getRoundEndReason();
        if (!reason.isEmpty()) {
            g.setColor(new Color(220, 200, 200));
            g.setFont(new Font("SansSerif", Font.PLAIN, 15));
            fm = g.getFontMetrics();
            g.drawString(reason, bx + (boxW - fm.stringWidth(reason)) / 2, by + 108);
        }

        // ── Score ─────────────────────────────────────────────────────────
        int pts = controller.getPlayers().get(0).getPoints();
        g.setColor(new Color(200, 200, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        fm = g.getFontMetrics();
        String score = "Final score: " + pts + " points";
        g.drawString(score, bx + (boxW - fm.stringWidth(score)) / 2, by + 136);

        // ── Divider ────────────────────────────────────────────────────────
        g.setColor(new Color(200, 40, 40, 80));
        g.drawLine(bx + 30, by + 155, bx + boxW - 30, by + 155);

        // ── Prompt ─────────────────────────────────────────────────────────
        g.setColor(new Color(150, 150, 150));
        g.setFont(new Font("SansSerif", Font.ITALIC, 14));
        fm = g.getFontMetrics();
        String prompt = "Press ENTER or SPACE to return to the main menu";
        g.drawString(prompt, bx + (boxW - fm.stringWidth(prompt)) / 2, by + 225);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AI scheduling
    // ══════════════════════════════════════════════════════════════════════

    private void cancelPendingAISlap() {
        if (pendingAISlapTimer != null && pendingAISlapTimer.isRunning()) {
            pendingAISlapTimer.stop();
            pendingAISlapTimer = null;
        }
    }

    private void cancelPendingAITurn() {
        if (pendingAITurnTimer != null && pendingAITurnTimer.isRunning()) {
            pendingAITurnTimer.stop();
            pendingAITurnTimer = null;
        }
    }

    private void scheduleAISlap() {
        cancelPendingAISlap();
        if (controller.getGameState() != GameState.PLAYING) return;
        if (!controller.isSlappable()) return;   // no slap condition — nothing to schedule

        // Always fire the timer when the pile IS slappable.
        // The accuracy roll happens inside the callback so the window is guaranteed to close.
        int delay = controller.getAISlapDelay();
        pendingAISlapTimer = new Timer(delay, evt -> {
            if (controller.getGameState() == GameState.PLAYING) {
                if (controller.shouldAISlap()) {
                    // AI slaps successfully
                    controller.attemptSlap(1);
                    controller.markAISlapped();
                } else {
                    // AI missed its window — close the slap window so play can resume
                    controller.markAISlapped();
                    controller.closeSlapWindow();
                }
                repaint();
                scheduleAITurnIfNeeded();
            }
            pendingAISlapTimer = null;
        });
        pendingAISlapTimer.setRepeats(false);
        pendingAISlapTimer.start();
    }

    private void scheduleAITurnIfNeeded() {
        cancelPendingAITurn();
        if (controller.getGameState() != GameState.PLAYING) return;
        if (controller.getCurrentPlayerIndex() != 1) return;
        // Never start an AI play turn while a slap opportunity is live
        if (controller.isSlapWindowActive()) return;

        // AI "thinks" faster in later rounds
        int thinkMs = Math.max(400, 1200 - (controller.getCurrentRound() - 1) * 60);

        pendingAITurnTimer = new Timer(thinkMs, evt -> {
            if (controller.getGameState() == GameState.PLAYING) {
                controller.aiPlayTurn();
                repaint();
                if (controller.isSlapWindowActive()) {
                    // AI's card created a slap condition — schedule slap reaction only
                    scheduleAISlap();
                } else {
                    // No slap condition — check for non-slap case and reschedule AI
                    // if it still has the turn (e.g. won a face/ace challenge)
                    scheduleAISlap();
                    scheduleAITurnIfNeeded();
                    if (controller.getGameState() != GameState.PLAYING) repaint();
                }
            }
            pendingAITurnTimer = null;
        });
        pendingAITurnTimer.setRepeats(false);
        pendingAITurnTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation
    // ══════════════════════════════════════════════════════════════════════

    private void returnToMenu() {
        cancelPendingAISlap();
        cancelPendingAITurn();
        if (dealingAnimationTimer != null && dealingAnimationTimer.isRunning()) {
            dealingAnimationTimer.stop();
        }
        if (warningPulseTimer != null) warningPulseTimer.stop();

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.getContentPane().removeAll();
            frame.add(new MenuPanel(frame));
            frame.revalidate();
            frame.repaint();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Input handlers
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        // P toggles pause during gameplay or the store (but not mid-pack-animation)
        boolean pauseable = (controller.getGameState() == GameState.PLAYING && !controller.isDealing())
                         || (controller.getGameState() == GameState.STORE && packPhase == PackPhase.NONE);
        if (key == KeyEvent.VK_P && pauseable) {
            paused = !paused;
            if (!paused) saveStatusMsg = "";
            repaint();
            return;
        }

        // While paused, P or ESC resumes the game; menu exit is done via the pause button
        if (paused) {
            if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_P) {
                paused = false;
                saveStatusMsg = "";
                repaint();
            }
            // All other keys blocked while paused
            return;
        }

        GameState state = controller.getGameState();
        boolean confirm = (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE);

        switch (state) {
            case PLAYING:
                if (controller.isDealing()) return;
                handlePlayingInput(key);
                break;

            case TIEBREAKER:
                if (confirm) {
                    controller.confirmTiebreaker();
                    repaint();
                }
                break;

            case ROUND_TRANSITION:
                if (confirm) {
                    storeSelectedIndex = -1;
                    controller.enterStore();
                    repaint();
                }
                break;

            case STORE:
                // If pack selection UI is open, ENTER confirms the selection
                if (packPhase == PackPhase.SELECTING && key == KeyEvent.VK_ENTER) {
                    confirmPackSelection();
                    break;
                }
                // Block store keyboard actions while pack animation is running
                if (packPhase != PackPhase.NONE) break;

                if (key == KeyEvent.VK_ENTER && storeSelectedIndex >= 0) {
                    boolean bought = controller.purchaseStoreCard(storeSelectedIndex);
                    if (bought) {
                        ratscrewd.util.SoundManager.playSound("shopbuy.wav");
                        storeSelectedIndex = -1;
                    }
                    repaint();
                } else if (key == KeyEvent.VK_ENTER && storePackSelectedIndex >= 0) {
                    // Buy the selected pack slot
                    List<Card> pack = controller.buyPack();
                    if (pack != null) {
                        storePackSelectedIndex = -1;
                        startPackOpenAnimation(pack);
                    }
                    repaint();
                } else if (confirm || key == KeyEvent.VK_C) {
                    triggerNextRound();
                }
                break;

            case GAME_OVER:
                if (confirm || key == KeyEvent.VK_ESCAPE) returnToMenu();
                break;
        }
    }

    /** Handles all keyboard input during active gameplay. */
    private void handlePlayingInput(int key) {
        if (key == KeyEvent.VK_SPACE) {
            cancelPendingAISlap();
            cancelPendingAITurn();
            controller.attemptSlap(0);
            repaint();
            if (controller.getGameState() == GameState.PLAYING) {
                // scheduleAISlap handles the case where a penalty card opened a new window;
                // scheduleAITurnIfNeeded is a no-op if the window is now active
                scheduleAISlap();
                scheduleAITurnIfNeeded();
            } else repaint();

        } else if (key == KeyEvent.VK_ENTER && selectedCardIndex >= 0) {
            // Block card plays while a slap opportunity is live
            if (controller.isSlapWindowActive()) return;
            controller.humanPlay(selectedCardIndex);
            selectedCardIndex = -1;
            repaint();
            if (controller.getGameState() == GameState.PLAYING) {
                if (controller.isSlapWindowActive()) {
                    // The card just played created a slap condition — schedule AI reaction
                    scheduleAISlap();
                } else {
                    scheduleAISlap();
                    scheduleAITurnIfNeeded();
                }
            } else {
                repaint();
            }

        } else if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_7) {
            int idx = key - KeyEvent.VK_1;
            if (idx < controller.getPlayers().get(0).getHand().size()) {
                selectedCardIndex = idx;
                repaint();
            }
        } else if (key >= KeyEvent.VK_NUMPAD1 && key <= KeyEvent.VK_NUMPAD7) {
            int idx = key - KeyEvent.VK_NUMPAD1;
            if (idx < controller.getPlayers().get(0).getHand().size()) {
                selectedCardIndex = idx;
                repaint();
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) return;

        // ── Pause menu interactions (highest priority) ─────────────────────
        if (paused) {
            if (pauseResumeRect != null && pauseResumeRect.contains(e.getPoint())) {
                paused = false;
                saveStatusMsg = "";
                repaint();
            } else if (pauseSaveRect != null && pauseSaveRect.contains(e.getPoint())) {
                try {
                    controller.saveToFile();
                    saveStatusMsg = "Game saved successfully!";
                    saveStatusOk  = true;
                } catch (Exception ex) {
                    saveStatusMsg = "Save failed: " + ex.getMessage();
                    saveStatusOk  = false;
                }
                repaint();
            } else if (pauseMenuRect != null && pauseMenuRect.contains(e.getPoint())) {
                returnToMenu();
            }
            return;
        }

        // ── Store interactions ─────────────────────────────────────────────
        if (controller.getGameState() == GameState.STORE) {
            // ── Pack selection UI (highest priority while open) ────────────
            if (packPhase == PackPhase.SELECTING) {
                // Confirm button
                if (packConfirmRect != null && packConfirmRect.contains(e.getPoint())) {
                    confirmPackSelection();
                    return;
                }
                // Card clicks in the selection grid
                for (int i = 0; i < packCardPickRects.length; i++) {
                    if (packCardPickRects[i] != null && packCardPickRects[i].contains(e.getPoint())) {
                        if (packSelectedSet.contains(i)) {
                            packSelectedSet.remove(i);
                        } else if (packSelectedSet.size() < 2) {
                            packSelectedSet.add(i);
                        }
                        packTooltipIndex = i;
                        repaint();
                        return;
                    }
                }
                return; // swallow clicks outside while selecting
            }
            if (packPhase != PackPhase.NONE) return; // animation in progress — block all clicks

            // ── Pack slot clicks ───────────────────────────────────────────
            for (int i = 0; i < storePackRects.length; i++) {
                if (storePackRects[i] != null && storePackRects[i].contains(e.getPoint())) {
                    if (storePackSelectedIndex == i) {
                        // Second click on same slot = buy it
                        List<Card> pack = controller.buyPack();
                        if (pack != null) {
                            storePackSelectedIndex = -1;
                            storeSelectedIndex     = -1;
                            startPackOpenAnimation(pack);
                        }
                    } else {
                        storePackSelectedIndex = i;
                        storeSelectedIndex     = -1;
                    }
                    repaint();
                    return;
                }
            }

            // ── Individual card clicks ─────────────────────────────────────
            boolean hitCard = false;
            for (int i = 0; i < storeCardRects.length; i++) {
                if (storeCardRects[i] != null && storeCardRects[i].contains(e.getPoint())) {
                    storeSelectedIndex     = (storeSelectedIndex == i) ? -1 : i;
                    storePackSelectedIndex = -1;
                    hitCard = true;
                    repaint();
                    break;
                }
            }
            if (!hitCard) {
                if (storeRerollRect != null && storeRerollRect.contains(e.getPoint())) {
                    if (controller.rerollStore()) {
                        ratscrewd.util.SoundManager.playSound("shopreroll.wav");
                    }
                    storeSelectedIndex     = -1;
                    storePackSelectedIndex = -1;
                    repaint();
                    return;
                }
                if (storeContinueRect != null && storeContinueRect.contains(e.getPoint())) {
                    triggerNextRound();
                    return;
                }
            }
            return;
        }

        // ── Normal hand card selection (gameplay only) ─────────────────────
        if (controller.getGameState() != GameState.PLAYING) return;
        List<Card> hand = controller.getPlayers().get(0).getHand();
        int size    = hand.size();
        int m       = Constants.SIDE_MARGIN;
        int startX  = m + Constants.CARD_WIDTH + m;
        int endX    = Constants.WINDOW_WIDTH - m - Constants.CARD_WIDTH - m;
        int availW  = endX - startX;
        int spacing = size > 1 ? availW / ((size - 1) * 2) : 0;
        selectedCardIndex = -1;
        for (int i = 0; i < size; i++) {
            int x = startX + i * spacing;
            int y = Constants.HAND_Y_OFFSET;
            if (new Rectangle(x, y, Constants.CARD_WIDTH, Constants.CARD_HEIGHT)
                    .contains(e.getPoint())) {
                selectedCardIndex = i;
                break;
            }
        }
        repaint();
    }

    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}
    @Override public void keyReleased(KeyEvent e)     {}
    @Override public void keyTyped(KeyEvent e)        {}
}