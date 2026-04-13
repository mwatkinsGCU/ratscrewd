package ratscrewd.players;

import ratscrewd.model.Card;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract player containing common hand, draw pile, and point logic.
 */
public abstract class Player {
    protected final List<Card> hand             = new ArrayList<>();
    protected final List<Card> personalDrawPile = new ArrayList<>();
    protected int points = 0;

    /** Plays and removes the card at the given hand index. */
    public Card play(int index) { return hand.remove(index); }

    /** Adds a card to the hand if under max size. */
    public void addToHand(Card card) {
        if (hand.size() < ratscrewd.util.Constants.MAX_HAND_SIZE) hand.add(card);
    }

    /** Collects a pile of cards into the personal draw pile. */
    public void collect(List<Card> pile) { personalDrawPile.addAll(pile); }

    /** Adds a single card directly to the personal draw pile (used by save restore). */
    public void collectDirect(Card card) { personalDrawPile.add(card); }

    /** Read-only view of the personal draw pile (used by save export). */
    public List<Card> getPersonalDrawPile() { return personalDrawPile; }

    /** @return the list of cards currently in hand. */
    public List<Card> getHand() { return hand; }

    /** Adjusts this player's point total by delta.  Points floor at 0. */
    public void incrementPoints(int delta) {
        points += delta;
        if (points < 0) points = 0;
    }

    /** @return this player's current point total. */
    public int getPoints() { return points; }

    /** @return number of cards in the personal draw pile. */
    public int getPersonalDrawPileSize() { return personalDrawPile.size(); }

    /** Draws and removes the top card from the personal draw pile, or null if empty. */
    public Card drawFromPersonalDrawPile() {
        return personalDrawPile.isEmpty() ? null : personalDrawPile.remove(0);
    }

    /**
     * Clears the hand and personal draw pile in preparation for a new round.
     * Points are deliberately preserved — they carry over to the between-round shop.
     */
    public void resetForNewRound() {
        hand.clear();
        personalDrawPile.clear();
    }

    /** Chooses a card to play given the current game context. */
    public abstract Card chooseCard(GameContext context);

    /**
     * @param round the current round number, used for difficulty scaling
     * @return reaction delay in milliseconds before this player slaps
     */
    public abstract int getSlapDelayMillis(int round);
}