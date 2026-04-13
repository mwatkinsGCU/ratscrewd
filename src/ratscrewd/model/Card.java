package ratscrewd.model;

import java.io.Serializable;

/**
 * Represents a playing card with a suit and a value.
 */
public class Card implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Suit      suit;
    private final CardValue value;

    public Card(Suit suit, CardValue value) {
        this.suit  = suit;
        this.value = value;
    }

    public Suit      getSuit()  { return suit; }
    public CardValue getValue() { return value; }

    @Override
    public String toString() {
        return value == CardValue.JOKER ? "JOKER" : value.name() + " of " + suit.name();
    }
}