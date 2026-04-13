package ratscrewd.game;

import ratscrewd.model.Card;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialisable snapshot of all game state required to fully restore a session.
 * This class contains only plain data — no timers, no Random, no Swing objects.
 * GameController produces and consumes it via exportSave() / importSave().
 *
 * serialVersionUID bumped to 2L — saves from version 1 are not compatible.
 */
public class GameSave implements Serializable {
    private static final long serialVersionUID = 2L;

    // Top-level state (PLAYING or STORE)
    public final String      savedGameState;

    // Round & turn
    public final int         currentRound;
    public final int         currentPlayerIndex;
    public final int         turnCounter;

    // Player state (index 0 = human, 1 = AI)
    public final int[]         playerPoints;
    public final List<Card>[]  playerHands;
    public final List<Card>[]  playerPersonalPiles;

    // Deck / pile state
    public final List<Card> centerDeckCards;
    public final List<Card> discardPile;
    public final List<Card> permanentExtraCards;

    // Faces-and-aces
    public final boolean faceChallengeActive;
    public final Integer lastFaceAcePlayer;

    // Cycle-limit tracking
    public final boolean centerDepleted;
    public final int     cyclesNoGain;
    public final int     playTurnsAfterDepletion;
    public final int[]   pileSnapshot;   // null until centre depletion

    // Store state (only meaningful when savedGameState == "STORE")
    public final List<Card> storeCards;
    public final int        rerollCost;
    public final int        packsRemainingThisVisit;

    @SuppressWarnings("unchecked")
    public GameSave(
            String savedGameState,
            int currentRound, int currentPlayerIndex, int turnCounter,
            int[] playerPoints,
            List<Card>[] playerHands, List<Card>[] playerPersonalPiles,
            List<Card> centerDeckCards, List<Card> discardPile,
            List<Card> permanentExtraCards,
            boolean faceChallengeActive, Integer lastFaceAcePlayer,
            boolean centerDepleted, int cyclesNoGain,
            int playTurnsAfterDepletion, int[] pileSnapshot,
            List<Card> storeCards, int rerollCost, int packsRemainingThisVisit) {

        this.savedGameState          = savedGameState;
        this.currentRound            = currentRound;
        this.currentPlayerIndex      = currentPlayerIndex;
        this.turnCounter             = turnCounter;
        this.playerPoints            = playerPoints;
        this.playerHands             = playerHands;
        this.playerPersonalPiles     = playerPersonalPiles;
        this.centerDeckCards         = new ArrayList<>(centerDeckCards);
        this.discardPile             = new ArrayList<>(discardPile);
        this.permanentExtraCards     = new ArrayList<>(permanentExtraCards);
        this.faceChallengeActive     = faceChallengeActive;
        this.lastFaceAcePlayer       = lastFaceAcePlayer;
        this.centerDepleted          = centerDepleted;
        this.cyclesNoGain            = cyclesNoGain;
        this.playTurnsAfterDepletion = playTurnsAfterDepletion;
        this.pileSnapshot            = pileSnapshot != null ? pileSnapshot.clone() : null;
        this.storeCards              = new ArrayList<>(storeCards);
        this.rerollCost              = rerollCost;
        this.packsRemainingThisVisit = packsRemainingThisVisit;
    }
}