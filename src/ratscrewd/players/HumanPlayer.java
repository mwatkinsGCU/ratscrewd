package ratscrewd.players;

import ratscrewd.model.Card;

/**
 * Human player handles selection via UI; AI logic not applied.
 */
public class HumanPlayer extends Player {

    /**
     * For human, choice is driven by UI; placeholder returns first card.
     */
    @Override
    public Card chooseCard(GameContext context) {
        // actual selection handled in GamePanel via mouse click
        return hand.isEmpty() ? null : hand.get(0);
    }

    @Override
    public int getSlapDelayMillis(int round) {
        return 0; // human reaction handled by key press
    }
}
