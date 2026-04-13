package ratscrewd.players;

import ratscrewd.model.Card;
import ratscrewd.model.CardValue;
import java.util.List;

/**
 * Provides read-only game state to player AI logic.
 * Carries the current round number so AI can scale its behaviour accordingly.
 */
public class GameContext {
    private final List<Card> discardPile;
    private final int        currentRound;

    public GameContext(List<Card> discardPile, int currentRound) {
        this.discardPile  = discardPile;
        this.currentRound = currentRound;
    }

    /** @return top card of the discard pile, or null if empty. */
    public Card topDiscard() {
        return discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1);
    }

    /**
     * Whether a card may legally be played under the faces-and-aces rule.
     * If the top card is a face or ace (not a Joker), only another face or
     * ace may be played in response.
     */
    public boolean isValidPlay(Card c) {
        Card top = topDiscard();
        if (top == null) return true;
        boolean topIsFaceOrAce = top.getValue().ordinal() >= CardValue.JACK.ordinal()
                              && top.getValue() != CardValue.JOKER;
        if (topIsFaceOrAce) {
            return c.getValue().ordinal() >= CardValue.JACK.ordinal()
                && c.getValue() != CardValue.JOKER;
        }
        return true;
    }

    /** @return the current round number (1-indexed). */
    public int getCurrentRound() { return currentRound; }
}