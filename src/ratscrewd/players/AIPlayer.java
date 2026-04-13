package ratscrewd.players;

import ratscrewd.model.Card;
import ratscrewd.model.CardValue;
import java.util.List;
import java.util.Random;

/**
 * AI player whose difficulty scales with the current round number.
 *
 * <p><b>Early rounds (1–3):</b> slow slap reactions, very passive card play —
 * rarely tries to create doubles, always plays the lowest legal card.</p>
 *
 * <p><b>Mid rounds (4–9):</b> noticeably faster reactions; increasingly willing
 * to engineer doubles when holding fewer cards.</p>
 *
 * <p><b>Late rounds (10+):</b> fast reactions, highly aggressive — seeks doubles
 * even with a comfortable hand, and slaps with near-maximum accuracy.</p>
 *
 * <h3>Scaling parameters</h3>
 * <table>
 *   <tr><th>Round</th><th>Slap delay (ms)</th><th>Slap accuracy</th><th>Double aggression</th></tr>
 *   <tr><td>1</td> <td>~1 400</td> <td>~78 %</td> <td> 4 % / 18 %</td></tr>
 *   <tr><td>5</td> <td>~1 200</td> <td>~90 %</td> <td>18 % / 40 %</td></tr>
 *   <tr><td>10</td><td>~  750</td> <td>~97 %</td> <td>36 % / 63 %</td></tr>
 *   <tr><td>20+</td><td>  300</td> <td>~97 %</td> <td>55 % / 95 %</td></tr>
 * </table>
 * (Double aggression shown as comfortable-hand % / desperate-hand %)
 */
public class AIPlayer extends Player {
    private final Random rng = new Random();

    // ══════════════════════════════════════════════════════════════════════
    // Card selection
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Card chooseCard(GameContext context) {
        List<Card> hand = getHand();
        if (hand.isEmpty()) return null;

        int  round      = context.getCurrentRound();
        Card topDiscard = context.topDiscard();

        // Strategy 1 — try to create a double (probability grows with round)
        if (topDiscard != null && shouldAttemptDouble(round)) {
            Card match = findMatchingCard(hand, topDiscard);
            if (match != null && context.isValidPlay(match)) {
                return match;
            }
        }

        // Strategy 2 — play the lowest-value legal card (protect high cards)
        Card lowest = findLowestValidCard(hand, context);
        if (lowest != null) return lowest;

        // Fallback — play whatever is first
        return hand.get(0);
    }

    /**
     * Whether the AI should attempt to engineer a double on this turn.
     *
     * <p>Two thresholds are maintained:
     * <ul>
     *   <li><em>comfortable</em> — used when the AI has ≥ 6 cards in hand.</li>
     *   <li><em>desperate</em>   — used when the AI has ≤ 3 cards in hand.</li>
     * </ul>
     * A mid-hand size interpolates between the two.</p>
     */
    private boolean shouldAttemptDouble(int round) {
        // Comfortable threshold: 4 % at round 1, caps at ~55 % by round 15
        double comfortable = Math.min(0.55, 0.03 + (round - 1) * 0.037);
        // Desperate threshold: 18 % at round 1, caps at ~95 % by round 15
        double desperate   = Math.min(0.95, 0.15 + (round - 1) * 0.057);

        if      (hand.size() >= 6) return rng.nextDouble() < comfortable;
        else if (hand.size() <= 3) return rng.nextDouble() < desperate;
        else                       return rng.nextDouble() < (comfortable + desperate) / 2.0;
    }

    private Card findMatchingCard(List<Card> hand, Card target) {
        CardValue tv = target.getValue();
        for (Card c : hand) {
            if (c.getValue() == tv) return c;
        }
        return null;
    }

    private Card findLowestValidCard(List<Card> hand, GameContext context) {
        Card lowest = null;
        for (Card c : hand) {
            if (context.isValidPlay(c)) {
                if (lowest == null || c.getValue().getPoints() < lowest.getValue().getPoints()) {
                    lowest = c;
                }
            }
        }
        return lowest;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Slap timing
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Base slap reaction delay in milliseconds.
     * Starts at ~1 400 ms in round 1 and decreases by ~55 ms per round,
     * flooring at 300 ms around round 20.
     */
    @Override
    public int getSlapDelayMillis(int round) {
        return Math.max(300, 1400 - (round - 1) * 55);
    }
}