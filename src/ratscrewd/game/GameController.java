package ratscrewd.game;

import ratscrewd.model.Card;
import ratscrewd.model.Deck;
import ratscrewd.model.CardValue;
import ratscrewd.players.Player;
import ratscrewd.players.HumanPlayer;
import ratscrewd.players.AIPlayer;
import ratscrewd.players.GameContext;
import ratscrewd.util.Constants;
import ratscrewd.util.SoundManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Orchestrates multi-round game flow with a scaling AI opponent.
 *
 * Round-end conditions:
 *   1. A player's hand AND personal draw pile are both empty.
 *   2. After centre-deck depletion: 10 consecutive cycles pass with no player
 *      gaining 5 or more cards net to their personal pile (warnings shown 1-5 cycles out).
 *
 * After a round ends:
 *   - Human wins  -> state becomes ROUND_TRANSITION, then STORE (card shop).
 *                    beginNextRound() starts a fresh round after the store is dismissed.
 *   - AI wins     -> State becomes GAME_OVER.
 *   - Tie         -> runTiebreaker() deals 3 face-up cards to each player from a fresh
 *                    deck; the higher point total wins; state becomes TIEBREAKER.
 *                    confirmTiebreaker() resolves to ROUND_TRANSITION/STORE or GAME_OVER.
 */
public class GameController {

    // ══════════════════════════════════════════════════════════════════════
    // State machine
    // ══════════════════════════════════════════════════════════════════════

    /** Top-level game state. GamePanel drives screen rendering based on this value. */
    public enum GameState {
        /** Normal gameplay is in progress. */
        PLAYING,
        /** A tie occurred; tiebreaker cards have been dealt and await player confirmation. */
        TIEBREAKER,
        /** Human won the round; awaiting player input to open the card shop. */
        ROUND_TRANSITION,
        /** Between-round card shop is open. */
        STORE,
        /** AI won (directly or via tiebreaker); game is completely over. */
        GAME_OVER
    }

    private GameState gameState = GameState.PLAYING;

    // ── Round tracking ─────────────────────────────────────────────────────
    /** Current round number, 1-indexed. Incremented when {@link #beginNextRound()} is called. */
    private int currentRound = 1;

    /** Play-turn counter within the current round (used for AI timing ramp). */
    private int turnCounter = 0;

    // ── Core game objects ──────────────────────────────────────────────────
    private Deck               centerDeck         = new Deck();
    private final List<Card>   discardPile        = new ArrayList<>();
    private final List<Player> players            = new ArrayList<>();
    private int                currentPlayerIndex = 0;

    // ── Faces-and-aces ─────────────────────────────────────────────────────
    private Integer lastFaceAcePlayer  = null;
    private boolean faceChallengeActive = false;

    // ── Slap tracking ──────────────────────────────────────────────────────
    private final Random rng      = new Random();
    private boolean aiHasSlapped  = false;
    private long    lastSlapTime  = 0;
    private static final long SLAP_GRACE_PERIOD_MS = 500;
    /**
     * True from the moment a played card creates a slappable condition until the
     * pile is collected (successful slap) or explicitly closed (AI missed its window).
     * While active, both humanPlay() and aiPlayTurn() are blocked.
     */
    private boolean slapWindowActive = false;

    // ── Dealing animation handshake ────────────────────────────────────────
    private boolean isDealing       = false;
    private int     dealingPlayerIndex = 0;
    private int     dealingCardCount   = 0;

    // ── Round-end cause ────────────────────────────────────────────────────
    private enum RoundEndCause { NONE, PLAYER_OUT, CYCLE_LIMIT }
    private RoundEndCause roundEndCause    = RoundEndCause.NONE;
    /** Index of the winner (0=human, 1=AI, -2=tie, -1=none yet). */
    private int           roundWinnerIndex = -1;

    // ── Case-2 cycle tracking ──────────────────────────────────────────────
    private static final int MAX_NO_GAIN_CYCLES = 10;
    private static final int GAIN_THRESHOLD     = 5;
    private static final int WARNING_THRESHOLD  = 5;

    private boolean centerDepleted          = false;
    private int     cyclesNoGain            = 0;
    private int     playTurnsAfterDepletion = 0;
    private int[]   pileSnapshot            = null;

    // ── Tiebreaker data ────────────────────────────────────────────────────
    private final List<Card> tiebreakerHumanCards = new ArrayList<>();
    private final List<Card> tiebreakerAICards    = new ArrayList<>();
    private int     tiebreakerHumanScore = 0;
    private int     tiebreakerAIScore    = 0;
    private boolean tiebreakerHumanWon   = false;

    // ── Card store ─────────────────────────────────────────────────────────
    // storeCards shrinks as the player purchases; regenerated on each re-roll.
    private final List<Card> storeCards         = new ArrayList<>();
    private int              rerollCost         = 100;
    private static final int REROLL_BASE_COST   = 100;
    private static final int REROLL_STEP        = 25;

    // ── Card packs ─────────────────────────────────────────────────────────
    private int              packsRemainingThisVisit = 2;
    private static final int PACK_COST              = 200;
    private static final int PACK_CARDS             = 4;
    private static final int PACKS_PER_VISIT        = 2;
    // Cards the player has bought persist across rounds and are shuffled into
    // every subsequent centre deck.
    private final List<Card> permanentExtraCards = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation
    // ══════════════════════════════════════════════════════════════════════

    /** Full reset for a brand-new game session (called once per New Game). */
    public void initRun() {
        players.clear();
        players.add(new HumanPlayer());
        players.add(new AIPlayer());
        currentRound = 1;
        permanentExtraCards.clear();   // fresh game — no carried-over cards
        resetRoundState();
        gameState          = GameState.PLAYING;
        isDealing          = true;
        dealingPlayerIndex = 0;
        dealingCardCount   = 0;
    }

    /** Resets all per-round state. Called by initRun() and beginNextRound(). */
    private void resetRoundState() {
        discardPile.clear();
        centerDeck = new Deck();
        // Shuffle any permanently purchased cards into the new centre deck.
        if (!permanentExtraCards.isEmpty()) {
            centerDeck.addToBottom(new ArrayList<>(permanentExtraCards));
            centerDeck.shuffle();
        }
        for (Player p : players) p.resetForNewRound();

        roundWinnerIndex        = -1;
        roundEndCause           = RoundEndCause.NONE;
        centerDepleted          = false;
        cyclesNoGain            = 0;
        playTurnsAfterDepletion = 0;
        pileSnapshot            = null;
        turnCounter             = 0;
        currentPlayerIndex      = 0;
        faceChallengeActive     = false;
        lastFaceAcePlayer       = null;
        aiHasSlapped            = false;
        lastSlapTime            = 0;
        slapWindowActive        = false;

        tiebreakerHumanCards.clear();
        tiebreakerAICards.clear();
        tiebreakerHumanScore = 0;
        tiebreakerAIScore    = 0;
        tiebreakerHumanWon   = false;
    }

    // ── Dealing animation handshake ────────────────────────────────────────

    /**
     * Deals one card to the next player in sequence.
     * @return {@code true} when the initial deal is completely finished.
     */
    public boolean dealNextCard() {
        if (!isDealing) return true;
        Player p = players.get(dealingPlayerIndex);
        Card   c = centerDeck.draw();
        if (c != null) p.addToHand(c);

        dealingPlayerIndex = (dealingPlayerIndex + 1) % players.size();
        if (dealingPlayerIndex == 0) dealingCardCount++;

        if (dealingCardCount >= Constants.MAX_HAND_SIZE) {
            isDealing = false;
            return true;
        }
        return false;
    }

    public boolean isDealing() { return isDealing; }

    // ══════════════════════════════════════════════════════════════════════
    // Turn execution
    // ══════════════════════════════════════════════════════════════════════

    /** Executes the AI's turn. No-op if gameplay is not active or it is the human's turn. */
    public void aiPlayTurn() {
        if (gameState != GameState.PLAYING) return;
        if (currentPlayerIndex != 1) return;
        // Never play a card while a slap opportunity is live
        if (slapWindowActive) return;

        Player p = players.get(1);
        Card candidate = p.chooseCard(new GameContext(discardPile, currentRound));
        if (candidate != null) {
            Card played = p.play(p.getHand().indexOf(candidate));
            discardPile.add(played);
            SoundManager.playSound("cardplay.wav");
            p.incrementPoints(played.getValue().getPoints());
            replenishHand(p);
            aiHasSlapped = false;
            // Open slap window immediately if the played card creates a slap condition
            if (checkSlappable()) {
                slapWindowActive = true;
                return;   // GamePanel will schedule the slap response; no turn advance yet
            }
            if (enforceFaceAceRule(played)) {
                onPlayTurnComplete();
                return;
            }
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        turnCounter++;
        onPlayTurnComplete();
    }

    /** Executes the human's chosen card play. No-op if gameplay is not active. */
    public void humanPlay(int handIndex) {
        if (gameState != GameState.PLAYING) return;
        // Never play a card while a slap opportunity is live
        if (slapWindowActive) return;
        Player p = players.get(0);
        if (handIndex < 0 || handIndex >= p.getHand().size()) return;

        Card played = p.play(handIndex);
        discardPile.add(played);
        SoundManager.playSound("cardplay.wav");
        p.incrementPoints(played.getValue().getPoints());
        replenishHand(p);
        aiHasSlapped = false;
        // Open slap window immediately if the played card creates a slap condition
        if (checkSlappable()) {
            slapWindowActive = true;
            return;   // GamePanel will schedule the AI slap response; no turn advance yet
        }
        if (enforceFaceAceRule(played)) {
            onPlayTurnComplete();
            return;
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        turnCounter++;
        onPlayTurnComplete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Slap mechanic
    // ══════════════════════════════════════════════════════════════════════

    /** Processes a slap attempt. No-op if gameplay is not active. */
    public void attemptSlap(int playerIndex) {
        if (gameState != GameState.PLAYING) return;
        Player p   = players.get(playerIndex);
        long   now = System.currentTimeMillis();
        boolean inGrace = (now - lastSlapTime) < SLAP_GRACE_PERIOD_MS;

        if (checkSlappable()) {
            // ── Successful slap ─────────────────────────────────────────
            SoundManager.playSound("slap.wav");
            List<Card> pile = new ArrayList<>(discardPile);
            Collections.reverse(pile);
            p.collect(pile);
            discardPile.clear();
            p.incrementPoints(5);
            replenishHand(p);
            faceChallengeActive = false;
            lastFaceAcePlayer   = null;
            lastSlapTime        = now;
            currentPlayerIndex  = playerIndex;
            slapWindowActive    = false;

        } else if (!inGrace) {
            // ── Failed slap (penalty) ───────────────────────────────────
            SoundManager.playSound("penalty.wav");
            p.incrementPoints(-10);
            if (p.getPoints() < 0) p.incrementPoints(-p.getPoints());

            if (!p.getHand().isEmpty()) {
                Card c = p.play(rng.nextInt(p.getHand().size()));
                discardPile.add(c);
                replenishHand(p);
            } else if (p.getPersonalDrawPileSize() > 0) {
                discardPile.add(p.drawFromPersonalDrawPile());
            }
            currentPlayerIndex = (playerIndex + 1) % players.size();

            // A penalty card landing on the pile might create a new slap condition
            if (checkSlappable()) slapWindowActive = true;
        }
        // Slap may have drained a player — check Case 1 immediately
        checkRoundEndConditions();
    }

    private boolean checkSlappable() {
        int sz = discardPile.size();
        if (sz == 0) return false;
        Card top = discardPile.get(sz - 1);
        // A Joker is always slappable, even if it is the only card in the pile
        if (top.getValue() == CardValue.JOKER) return true;
        if (sz < 2) return false;
        Card prev = discardPile.get(sz - 2);
        return top.getValue() == prev.getValue();
    }

    public boolean isSlappable() { return checkSlappable(); }

    /**
     * Closes the slap window without collecting the pile.
     * Called by GamePanel after the AI's reaction timer fires and the AI decided not to slap,
     * so play can resume normally rather than freezing.
     */
    public void closeSlapWindow() {
        if (!slapWindowActive) return;
        slapWindowActive = false;
        // Advance the turn that was suspended when the window opened
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        turnCounter++;
        onPlayTurnComplete();
    }

    /** @return true while a slap opportunity is live and card plays are blocked. */
    public boolean isSlapWindowActive() { return slapWindowActive; }

    // ══════════════════════════════════════════════════════════════════════
    // AI slap helpers
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reaction delay (ms) including ±30 % jitter.
     * Uses {@code currentRound} so difficulty scales across rounds.
     */
    public int getAISlapDelay() {
        AIPlayer ai  = (AIPlayer) players.get(1);
        int base     = ai.getSlapDelayMillis(currentRound);
        int variance = (int) (base * 0.30);
        int offset   = (variance > 0) ? rng.nextInt(variance * 2 + 1) - variance : 0;
        return Math.max(100, base + offset);
    }

    /**
     * Whether the AI will attempt a slap right now.
     *
     * Accuracy (true-positive rate) and false-slap rate both scale with round:
     *   Round 1:   ~78% accurate, ~6%  false-slap rate
     *   Round 10:  ~97% accurate, ~1%  false-slap rate
     *   Round 15+: ~97% accurate, ~1%  false-slap rate
     */
    public boolean shouldAISlap() {
        if (aiHasSlapped) return false;
        boolean slappable   = checkSlappable();
        double  accuracy    = Math.min(0.97, 0.78 + (currentRound - 1) * 0.022);
        double  mistakeRate = Math.max(0.01, 0.06 - (currentRound - 1) * 0.004);
        return slappable ? rng.nextDouble() < accuracy
                         : rng.nextDouble() < mistakeRate;
    }

    public void markAISlapped() { aiHasSlapped = true; }

    // ══════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════

    private void replenishHand(Player p) {
        while (p.getHand().size() < Constants.MAX_HAND_SIZE) {
            Card c = centerDeck.draw();
            if (c != null) {
                p.addToHand(c);
            } else if (p.getPersonalDrawPileSize() > 0) {
                p.addToHand(p.drawFromPersonalDrawPile());
            } else {
                break;
            }
        }
    }

    private boolean enforceFaceAceRule(Card played) {
        boolean faceOrAce = played.getValue().ordinal() >= CardValue.JACK.ordinal()
                         && played.getValue() != CardValue.JOKER;
        if (faceOrAce) {
            lastFaceAcePlayer   = currentPlayerIndex;
            faceChallengeActive = true;
            return false;
        }
        if (faceChallengeActive) {
            Player collector = players.get(lastFaceAcePlayer);
            if (lastFaceAcePlayer == 0) SoundManager.playSound("winfaceace.wav");
            else                        SoundManager.playSound("losefaceace.wav");

            List<Card> pile = new ArrayList<>(discardPile);
            Collections.reverse(pile);
            collector.collect(pile);
            discardPile.clear();
            faceChallengeActive = false;
            currentPlayerIndex  = lastFaceAcePlayer;
            replenishHand(collector);
            return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Round-end detection
    // ══════════════════════════════════════════════════════════════════════

    private void onPlayTurnComplete() {
        checkRoundEndConditions();
        if (gameState == GameState.PLAYING) updateCycleTracking();
    }

    // ── Case 1: player cards exhausted ────────────────────────────────────

    private void checkRoundEndConditions() {
        if (gameState != GameState.PLAYING) return;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.getHand().isEmpty() && p.getPersonalDrawPileSize() == 0) {
                roundEndCause = RoundEndCause.PLAYER_OUT;
                onRoundEnd((i + 1) % players.size());
                return;
            }
        }
    }

    // ── Case 2: cycle limit ────────────────────────────────────────────────

    private void updateCycleTracking() {
        if (!centerDepleted && centerDeck.isEmpty()) {
            centerDepleted = true;
            pileSnapshot   = new int[players.size()];
            for (int i = 0; i < players.size(); i++) {
                pileSnapshot[i] = players.get(i).getPersonalDrawPileSize();
            }
            playTurnsAfterDepletion = 0;
        }
        if (!centerDepleted) return;

        playTurnsAfterDepletion++;
        if (playTurnsAfterDepletion % players.size() == 0) evaluateCycle();
    }

    private void evaluateCycle() {
        boolean anyGained = false;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPersonalDrawPileSize() - pileSnapshot[i] >= GAIN_THRESHOLD) {
                anyGained = true;
                break;
            }
        }
        cyclesNoGain = anyGained ? 0 : cyclesNoGain + 1;
        for (int i = 0; i < players.size(); i++) {
            pileSnapshot[i] = players.get(i).getPersonalDrawPileSize();
        }
        if (cyclesNoGain >= MAX_NO_GAIN_CYCLES) {
            roundEndCause = RoundEndCause.CYCLE_LIMIT;
            onRoundEnd(findPlayerWithMostCards());
        }
    }

    private int findPlayerWithMostCards() {
        int max = -1, winner = -2;
        for (int i = 0; i < players.size(); i++) {
            int total = players.get(i).getHand().size()
                      + players.get(i).getPersonalDrawPileSize();
            if      (total > max)  { max = total; winner = i; }
            else if (total == max) { winner = -2; }
        }
        return winner;
    }

    // ── Central dispatcher ─────────────────────────────────────────────────

    /**
     * Routes to ROUND_TRANSITION (human win), GAME_OVER (AI win),
     * or runs a tiebreaker (tie, winnerIndex == -2).
     */
    private void onRoundEnd(int winnerIndex) {
        roundWinnerIndex = winnerIndex;
        if      (winnerIndex == 0)  gameState = GameState.ROUND_TRANSITION;
        else if (winnerIndex == 1)  gameState = GameState.GAME_OVER;
        else                        runTiebreaker();   // -2 = tie
    }

    // ── Tiebreaker ─────────────────────────────────────────────────────────

    /**
     * Deals 3 cards alternately from a fresh shuffled deck to each player
     * (face-up). Sums point values; highest wins. Re-deals up to 5 times on
     * a persistent tie; on an unresolved tie the human is awarded the win.
     */
    private void runTiebreaker() {
        Deck tbDeck  = new Deck();
        int attempts = 0;
        do {
            tiebreakerHumanCards.clear();
            tiebreakerAICards.clear();
            for (int i = 0; i < 3; i++) {
                Card h = tbDeck.draw();
                Card a = tbDeck.draw();
                if (h != null) tiebreakerHumanCards.add(h);
                if (a != null) tiebreakerAICards.add(a);
            }
            tiebreakerHumanScore = tiebreakerHumanCards.stream()
                                       .mapToInt(c -> c.getValue().getPoints()).sum();
            tiebreakerAIScore    = tiebreakerAICards.stream()
                                       .mapToInt(c -> c.getValue().getPoints()).sum();
            attempts++;
        } while (tiebreakerHumanScore == tiebreakerAIScore && attempts < 5);

        // Persistent tie → award the human (player-favourable house rule)
        tiebreakerHumanWon = tiebreakerHumanScore >= tiebreakerAIScore;
        gameState = GameState.TIEBREAKER;
    }

    // ══════════════════════════════════════════════════════════════════════
    // State transitions (called from GamePanel)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Called when the player confirms the tiebreaker result.
     * Advances to ROUND_TRANSITION (human won) or GAME_OVER (AI won).
     */
    public void confirmTiebreaker() {
        if (gameState != GameState.TIEBREAKER) return;
        gameState = tiebreakerHumanWon ? GameState.ROUND_TRANSITION : GameState.GAME_OVER;
    }

    /**
     * Increments the round counter, resets all per-round state, and prepares
     * a fresh animated deal. Valid from ROUND_TRANSITION or STORE state.
     * GamePanel must restart its dealing animation after calling this.
     */
    public void beginNextRound() {
        if (gameState != GameState.ROUND_TRANSITION && gameState != GameState.STORE) return;
        storeCards.clear();
        currentRound++;
        resetRoundState();
        gameState          = GameState.PLAYING;
        isDealing          = true;
        dealingPlayerIndex = 0;
        dealingCardCount   = 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Card store
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Transitions from ROUND_TRANSITION to STORE, generating a fresh
     * 6-card shop inventory and resetting the re-roll cost for this visit.
     */
    public void enterStore() {
        if (gameState != GameState.ROUND_TRANSITION) return;
        rerollCost = REROLL_BASE_COST;
        packsRemainingThisVisit = PACKS_PER_VISIT;
        generateStoreInventory();
        gameState = GameState.STORE;
    }

    /** Draws 6 random cards from a scratch deck to populate the shop. */
    private void generateStoreInventory() {
        storeCards.clear();
        Deck pool = new Deck();   // independent 54-card pool, not the game deck
        for (int i = 0; i < 6; i++) {
            Card c = pool.draw();
            if (c != null) storeCards.add(c);
        }
    }

    /**
     * Re-rolls the store inventory if the player can afford it.
     * Cost starts at 100 and rises by 25 with each successive re-roll.
     * @return true if the re-roll succeeded, false if the player had insufficient points.
     */
    public boolean rerollStore() {
        if (gameState != GameState.STORE) return false;
        Player human = players.get(0);
        if (human.getPoints() < rerollCost) return false;
        human.incrementPoints(-rerollCost);
        rerollCost += REROLL_STEP;
        generateStoreInventory();
        return true;
    }

    /**
     * Attempts to purchase the store card at the given index.
     * Deducts the point cost, removes the card from the store, and adds it
     * to permanentExtraCards (which are shuffled into every future deck).
     * @return true if the purchase succeeded, false if the player had insufficient points.
     */
    public boolean purchaseStoreCard(int index) {
        if (gameState != GameState.STORE) return false;
        if (index < 0 || index >= storeCards.size()) return false;
        Card card = storeCards.get(index);
        int  cost = getCardCost(card);
        Player human = players.get(0);
        if (human.getPoints() < cost) return false;
        human.incrementPoints(-cost);
        permanentExtraCards.add(card);
        storeCards.remove(index);
        return true;
    }

    /**
     * Point cost to purchase a card from the store.
     *
     * Tier breakdown (balanced against a 250-400 pt winning round):
     *   2 - 5  :  25 pts
     *   6 - 10 :  40 pts
     *   J/Q/K  :  70 pts
     *   Ace    :  90 pts
     *   Joker  : 130 pts
     */
    public static int getCardCost(Card card) {
        switch (card.getValue()) {
            case TWO: case THREE: case FOUR: case FIVE:
                return 25;
            case SIX: case SEVEN: case EIGHT: case NINE: case TEN:
                return 40;
            case JACK: case QUEEN: case KING:
                return 70;
            case ACE:
                return 90;
            case JOKER:
                return 130;
            default:
                return 40;
        }
    }

    // ── Store accessors ────────────────────────────────────────────────────

    /** Returns an unmodifiable view of the current store inventory (may be fewer than 6 after purchases). */
    public List<Card> getStoreCards() {
        return Collections.unmodifiableList(storeCards);
    }

    /** Current re-roll cost in points. */
    public int getRerollCost() { return rerollCost; }

    /** Number of cards permanently added to the player's deck so far this run. */
    public int getPermanentCardCount() { return permanentExtraCards.size(); }

    // ── Card pack accessors ────────────────────────────────────────────────

    /** How many packs the player can still buy this shop visit. */
    public int getPacksRemainingThisVisit() { return packsRemainingThisVisit; }

    /** Cost in points to purchase one card pack. */
    public int getPackCost() { return PACK_COST; }

    /** True if the player can afford a pack and has packs left this visit. */
    public boolean canBuyPack() {
        return packsRemainingThisVisit > 0
            && players.get(0).getPoints() >= PACK_COST;
    }

    /**
     * Deducts PACK_COST, decrements the pack counter, and returns
     * PACK_CARDS random cards drawn from an independent scratch deck.
     * Returns null if the purchase cannot proceed.
     */
    public List<Card> buyPack() {
        if (!canBuyPack()) return null;
        players.get(0).incrementPoints(-PACK_COST);
        packsRemainingThisVisit--;
        Deck pool = new Deck();
        List<Card> pack = new ArrayList<>();
        for (int i = 0; i < PACK_CARDS; i++) {
            Card c = pool.draw();
            if (c != null) pack.add(c);
        }
        return pack;
    }

    /**
     * Adds the cards at the given indices from packCards to the player's
     * permanent deck. Called after the player confirms their 2 selections.
     */
    public void addSelectedPackCards(List<Card> packCards, List<Integer> selectedIndices) {
        for (int idx : selectedIndices) {
            if (idx >= 0 && idx < packCards.size()) {
                permanentExtraCards.add(packCards.get(idx));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Public query API
    // ══════════════════════════════════════════════════════════════════════

    public GameState getGameState()    { return gameState; }
    public int  getCurrentRound()      { return currentRound; }

    /** Convenience: {@code true} whenever the state is not {@code PLAYING}. */
    public boolean isRoundOver()       { return gameState != GameState.PLAYING; }
    public boolean isCenterDepleted()  { return centerDepleted; }

    /**
     * Cycles remaining before game-over warning (1–{@value #WARNING_THRESHOLD}),
     * or 0 when no warning should be shown.
     */
    public int getWarningCyclesRemaining() {
        if (!centerDepleted || gameState != GameState.PLAYING) return 0;
        int remaining = MAX_NO_GAIN_CYCLES - cyclesNoGain;
        return (remaining > 0 && remaining <= WARNING_THRESHOLD) ? remaining : 0;
    }

    // ── Tiebreaker accessors ───────────────────────────────────────────────

    public List<Card> getTiebreakerHumanCards() {
        return Collections.unmodifiableList(tiebreakerHumanCards);
    }
    public List<Card> getTiebreakerAICards() {
        return Collections.unmodifiableList(tiebreakerAICards);
    }
    public int     getTiebreakerHumanScore()  { return tiebreakerHumanScore; }
    public int     getTiebreakerAIScore()     { return tiebreakerAIScore; }
    public boolean isTiebreakerHumanWon()     { return tiebreakerHumanWon; }

    // ── Display strings ────────────────────────────────────────────────────

    /**
     * A short explanation of why the current round ended.
     * Returns an empty string during normal play.
     */
    public String getRoundEndReason() {
        if (gameState == GameState.PLAYING) return "";

        // Lost via tiebreaker
        if (!tiebreakerHumanCards.isEmpty() && !tiebreakerHumanWon
                && gameState == GameState.GAME_OVER) {
            return "The AI won the tiebreaker in Round " + currentRound + ".";
        }
        // Won via tiebreaker
        if (!tiebreakerHumanCards.isEmpty() && tiebreakerHumanWon
                && gameState == GameState.ROUND_TRANSITION) {
            return "You won the tiebreaker!";
        }

        if (roundEndCause == RoundEndCause.PLAYER_OUT) {
            if (roundWinnerIndex == 0) return "The AI ran out of cards!";
            if (roundWinnerIndex == 1) return "You ran out of cards in Round " + currentRound + ".";
        }
        if (roundEndCause == RoundEndCause.CYCLE_LIMIT) {
            if (roundWinnerIndex == 0)
                return "Cycle limit reached \u2014 you had more cards!";
            if (roundWinnerIndex == 1)
                return "Cycle limit reached \u2014 the AI had more cards in Round " + currentRound + ".";
        }
        return "";
    }

    /**
     * Human-readable difficulty label for the <em>next</em> round.
     * Displayed on the round-transition screen.
     */
    public String getNextRoundDifficultyLabel() {
        int next = currentRound + 1;
        if (next <= 2)  return "Novice";
        if (next <= 4)  return "Amateur";
        if (next <= 6)  return "Skilled";
        if (next <= 9)  return "Expert";
        if (next <= 12) return "Veteran";
        return "Master";
    }

    // ── Standard accessors ─────────────────────────────────────────────────

    public List<Player> getPlayers()        { return players; }
    public Card getDiscardPileTop()          {
        return discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1);
    }
    public int getCenterDeckSize()           { return centerDeck.size(); }
    public int getCurrentPlayerIndex()       { return currentPlayerIndex; }
    public int getRoundWinnerIndex()         { return roundWinnerIndex; }

    // ══════════════════════════════════════════════════════════════════════
    // Save / load
    // ══════════════════════════════════════════════════════════════════════

    /** The folder and file used for the single save slot. */
    public static final File SAVE_DIR  = new File("saved game");
    public static final File SAVE_FILE = new File(SAVE_DIR, "ratscrewd_save.dat");

    /**
     * Snapshots the current state into a GameSave object.
     * Valid from PLAYING or STORE states.
     */
    @SuppressWarnings("unchecked")
    public GameSave exportSave() {
        int[] points = new int[players.size()];
        List<Card>[] hands = new List[players.size()];
        List<Card>[] piles = new List[players.size()];
        for (int i = 0; i < players.size(); i++) {
            points[i] = players.get(i).getPoints();
            hands[i]  = new ArrayList<>(players.get(i).getHand());
            piles[i]  = new ArrayList<>();
            Player p  = players.get(i);
            for (int j = 0; j < p.getPersonalDrawPileSize(); j++) {
                piles[i].add(p.getPersonalDrawPile().get(j));
            }
        }
        return new GameSave(
                gameState.name(),
                currentRound, currentPlayerIndex, turnCounter,
                points, hands, piles,
                centerDeck.getCards(), new ArrayList<>(discardPile),
                new ArrayList<>(permanentExtraCards),
                faceChallengeActive, lastFaceAcePlayer,
                centerDepleted, cyclesNoGain, playTurnsAfterDepletion,
                pileSnapshot,
                new ArrayList<>(storeCards), rerollCost, packsRemainingThisVisit);
    }

    /**
     * Restores a GameController to the state described by the save.
     * Called once on a freshly constructed controller (players already added).
     */
    public void importSave(GameSave save) {
        currentRound            = save.currentRound;
        currentPlayerIndex      = save.currentPlayerIndex;
        turnCounter             = save.turnCounter;
        faceChallengeActive     = save.faceChallengeActive;
        lastFaceAcePlayer       = save.lastFaceAcePlayer;
        centerDepleted          = save.centerDepleted;
        cyclesNoGain            = save.cyclesNoGain;
        playTurnsAfterDepletion = save.playTurnsAfterDepletion;
        pileSnapshot            = save.pileSnapshot != null ? save.pileSnapshot.clone() : null;
        permanentExtraCards.clear();
        permanentExtraCards.addAll(save.permanentExtraCards);

        centerDeck = new Deck(save.centerDeckCards);
        discardPile.clear();
        discardPile.addAll(save.discardPile);

        for (int i = 0; i < players.size() && i < save.playerPoints.length; i++) {
            Player p = players.get(i);
            p.resetForNewRound();
            p.incrementPoints(save.playerPoints[i]);
            for (Card c : save.playerHands[i])         p.addToHand(c);
            for (Card c : save.playerPersonalPiles[i]) p.collectDirect(c);
        }

        // Restore store state
        storeCards.clear();
        storeCards.addAll(save.storeCards);
        rerollCost              = save.rerollCost;
        packsRemainingThisVisit = save.packsRemainingThisVisit;

        // Restore the exact game state that was active when saved
        try {
            gameState = GameState.valueOf(save.savedGameState);
        } catch (IllegalArgumentException e) {
            gameState = GameState.PLAYING;
        }
    }

    /**
     * Serialises the current state to SAVE_FILE.
     * Valid from PLAYING or STORE states.
     * Creates the save directory if it does not exist.
     */
    public void saveToFile() throws IOException {
        if (gameState != GameState.PLAYING && gameState != GameState.STORE) {
            throw new IOException("Can only save during active gameplay or the card shop.");
        }
        if (!SAVE_DIR.exists()) SAVE_DIR.mkdirs();
        try (ObjectOutputStream oos =
                new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            oos.writeObject(exportSave());
        }
    }

    /**
     * Deserialises a GameSave from SAVE_FILE and returns a fully restored controller.
     */
    public static GameController loadFromFile() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois =
                new ObjectInputStream(new FileInputStream(SAVE_FILE))) {
            GameSave save = (GameSave) ois.readObject();
            GameController gc = new GameController();
            gc.players.clear();
            gc.players.add(new HumanPlayer());
            gc.players.add(new AIPlayer());
            gc.importSave(save);
            return gc;
        }
    }

    /** Deletes the save file so the Continue button disappears on return to menu. */
    public static void deleteSaveFile() {
        if (SAVE_FILE.exists()) SAVE_FILE.delete();
    }

    /** @return true if a save file is present and readable. */
    public static boolean saveExists() {
        return SAVE_FILE.exists() && SAVE_FILE.isFile();
    }
}