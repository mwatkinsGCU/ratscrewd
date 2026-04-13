package ratscrewd.model;

/**
 * Enumeration of card values with associated point values.
 */
public enum CardValue {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7),
    EIGHT(8), NINE(9), TEN(10), JACK(10), QUEEN(10), KING(10),
    ACE(11), JOKER(0);

    private final int points;

    CardValue(int points) {
        this.points = points;
    }

    /**
     * Returns the point value of this card.
     */
    public int getPoints() {
        return points;
    }
}

