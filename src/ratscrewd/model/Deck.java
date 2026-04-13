package ratscrewd.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dynamic deck implementation: one 52-card deck plus two Jokers (54 cards).
 */
public class Deck {
    private final List<Card> cards = new ArrayList<>();

    /** Builds and shuffles a fresh 54-card deck. */
    public Deck() {
        for (Suit suit : Suit.values()) {
            if (suit == Suit.JOKER) continue;
            for (CardValue value : CardValue.values()) {
                if (value == CardValue.JOKER) continue;
                cards.add(new Card(suit, value));
            }
        }
        cards.add(new Card(Suit.JOKER, CardValue.JOKER));
        cards.add(new Card(Suit.JOKER, CardValue.JOKER));
        shuffle();
    }

    /** Restores a deck from an exact ordered list of cards (used by save/load). */
    public Deck(List<Card> existingCards) {
        cards.addAll(existingCards);
    }

    public void shuffle() { Collections.shuffle(cards); }

    public Card draw() { return cards.isEmpty() ? null : cards.remove(0); }

    public void addToBottom(List<Card> toAdd) { cards.addAll(toAdd); }

    public boolean isEmpty() { return cards.isEmpty(); }

    public int size() { return cards.size(); }

    /** Returns a snapshot of remaining cards in draw order (for serialisation). */
    public List<Card> getCards() { return new ArrayList<>(cards); }
}