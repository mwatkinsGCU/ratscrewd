# Features and Code

[Back to README](../README.md)

---

## Core Egyptian Rat Screw Gameplay

The base game logic lives in `GameController`. Each turn, the active player puts a card face-up on the center discard pile. After every card is played, the controller checks whether a slap condition is now active.

### Deck Initialization

A standard 54-card deck (52 playing cards plus 2 Jokers) is built in `Deck.java`. Purchased cards from previous rounds are shuffled in at the bottom before the deal starts.

```java
// Deck.java
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
```

And in `GameController`, purchased cards from the store carry over:

```java
// GameController.java - resetRoundState()
centerDeck = new Deck();
if (!permanentExtraCards.isEmpty()) {
    centerDeck.addToBottom(new ArrayList<>(permanentExtraCards));
    centerDeck.shuffle();
}
```

### Slap Detection

After every card play, `checkSlappable()` determines whether a valid slap condition exists. The two valid conditions are a Joker on top, or a matching pair on the top two cards.

```java
// GameController.java
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
```

When a slap condition is detected, `slapWindowActive` is set to `true` and card plays are blocked until the window is closed. This prevents a card from being played over a slappable pile before either player has a chance to react.

### Faces and Aces Rule

When a face card or Ace is played, the opponent must respond with their own face card or Ace. Failing to do so means they forfeit the pile. This is tracked via a `faceChallengeActive` flag and handled in `enforceFaceAceRule()`:

```java
// GameController.java
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
```

---

## AI Opponent

The AI scales across three parameters as rounds increase: slap reaction speed, slap accuracy, and willingness to engineer double-card situations.

### Slap Reaction Delay

`getSlapDelayMillis()` returns the AI's base slap speed. Round 1 gives the AI about 1400ms to react. By round 20, that floors at 300ms. A 30% jitter is applied on top in `getAISlapDelay()` so the AI does not feel robotic.

```java
// AIPlayer.java
@Override
public int getSlapDelayMillis(int round) {
    return Math.max(300, 1400 - (round - 1) * 55);
}

// GameController.java - adds jitter
public int getAISlapDelay() {
    AIPlayer ai  = (AIPlayer) players.get(1);
    int base     = ai.getSlapDelayMillis(currentRound);
    int variance = (int) (base * 0.30);
    int offset   = (variance > 0) ? rng.nextInt(variance * 2 + 1) - variance : 0;
    return Math.max(100, base + offset);
}
```

### Slap Accuracy

The AI does not always slap correctly. `shouldAISlap()` uses a true-positive rate that grows from around 78% in round 1 to 97% by round 10. A small false-slap rate (starting at 6%, dropping toward 1%) means the AI will occasionally slap an invalid pile in early rounds.

```java
// GameController.java
public boolean shouldAISlap() {
    if (aiHasSlapped) return false;
    boolean slappable   = checkSlappable();
    double  accuracy    = Math.min(0.97, 0.78 + (currentRound - 1) * 0.022);
    double  mistakeRate = Math.max(0.01, 0.06 - (currentRound - 1) * 0.004);
    return slappable ? rng.nextDouble() < accuracy
                     : rng.nextDouble() < mistakeRate;
}
```

### Card Selection Strategy

The AI has two card selection strategies. First, it checks whether it should try to create a double by matching the top discard card. The probability of attempting this grows with the round and also depends on how many cards the AI currently holds. If that does not apply, the AI plays its lowest-value valid card, saving higher-value cards for later.

```java
// AIPlayer.java
@Override
public Card chooseCard(GameContext context) {
    List<Card> hand = getHand();
    if (hand.isEmpty()) return null;

    int  round      = context.getCurrentRound();
    Card topDiscard = context.topDiscard();

    // Strategy 1: try to create a double
    if (topDiscard != null && shouldAttemptDouble(round)) {
        Card match = findMatchingCard(hand, topDiscard);
        if (match != null && context.isValidPlay(match)) {
            return match;
        }
    }

    // Strategy 2: play the lowest-value legal card
    Card lowest = findLowestValidCard(hand, context);
    if (lowest != null) return lowest;

    return hand.get(0);
}

private boolean shouldAttemptDouble(int round) {
    // Comfortable threshold (6+ cards in hand): 3% at round 1, caps at ~55% by round 15
    double comfortable = Math.min(0.55, 0.03 + (round - 1) * 0.037);
    // Desperate threshold (3 or fewer cards): 15% at round 1, caps at ~95% by round 15
    double desperate   = Math.min(0.95, 0.15 + (round - 1) * 0.057);

    if      (hand.size() >= 6) return rng.nextDouble() < comfortable;
    else if (hand.size() <= 3) return rng.nextDouble() < desperate;
    else                       return rng.nextDouble() < (comfortable + desperate) / 2.0;
}
```

The table below shows how the parameters change across rounds, pulled directly from the Javadoc in `AIPlayer.java`:

| Round | Slap Delay | Slap Accuracy | Double Aggression (comfortable / desperate) |
|-------|-----------|---------------|----------------------------------------------|
| 1 | ~1400ms | ~78% | 4% / 18% |
| 5 | ~1200ms | ~90% | 18% / 40% |
| 10 | ~750ms | ~97% | 36% / 63% |
| 20+ | 300ms | ~97% | 55% / 95% |

---

## Card Store

After winning a round, the player visits the Card Store before the next round starts. Card packs cost 200 points each and contain 4 cards. A maximum of 2 packs can be bought per visit. The player can also reroll the store's available cards for an increasing cost starting at 100 points.

Cards bought in the store are added to `permanentExtraCards`, a list that persists across rounds. At the start of each new round, those cards are shuffled into the fresh center deck.

```java
// GameController.java
private static final int PACK_COST        = 200;
private static final int PACK_CARDS       = 4;
private static final int PACKS_PER_VISIT  = 2;

// permanentExtraCards are added to every subsequent deck
private void resetRoundState() {
    centerDeck = new Deck();
    if (!permanentExtraCards.isEmpty()) {
        centerDeck.addToBottom(new ArrayList<>(permanentExtraCards));
        centerDeck.shuffle();
    }
    // ...
}
```

---

## Save and Load

The full game state can be saved to disk at any point during active gameplay or while the store is open. Loading restores everything exactly: the discard pile, both players' hands and draw piles, point totals, the current round, the store inventory, and even the faces-and-aces challenge state.

`GameSave` is a plain serializable data class. `GameController` produces and consumes it through `exportSave()` and `importSave()` to keep serialization separate from the game logic.

```java
// GameController.java - saving
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

// GameController.java - loading
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
```

`GameSave` holds only plain data fields, no timers or Swing objects, so it serializes cleanly:

```java
// GameSave.java (fields shown, constructor omitted)
public class GameSave implements Serializable {
    private static final long serialVersionUID = 2L;

    public final String       savedGameState;
    public final int          currentRound;
    public final int          currentPlayerIndex;
    public final int[]        playerPoints;
    public final List<Card>[] playerHands;
    public final List<Card>[] playerPersonalPiles;
    public final List<Card>   centerDeckCards;
    public final List<Card>   discardPile;
    public final List<Card>   permanentExtraCards;
    public final boolean      faceChallengeActive;
    public final Integer      lastFaceAcePlayer;
    public final boolean      centerDepleted;
    public final int          cyclesNoGain;
    public final int[]        pileSnapshot;
    public final List<Card>   storeCards;
    public final int          rerollCost;
    public final int          packsRemainingThisVisit;
}
```

---

## Sound Effects

`SoundManager` plays `.wav` files asynchronously so audio never blocks the UI thread. Each game event maps to a specific file name.

```java
// SoundManager.java (simplified)
public static void playSound(String filename) {
    new Thread(() -> {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(
                new File("sounds/" + filename));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (Exception e) {
            // Missing sound file is non-fatal; game continues without audio
        }
    }).start();
}
```

Sound files used: `cardplay.wav`, `slap.wav`, `badslap.wav`, `winfaceace.wav`, `losefaceace.wav`, `storeopen.wav`, `storebuy.wav`.

---

## Card Image Rendering

`CardImageLoader` loads card images from the `images/` directory and caches them in a `HashMap` so each file is only read once per session. If an image is missing, `GamePanel` falls back to a labeled rectangle so the game stays playable.

```java
// CardImageLoader.java (simplified)
private final Map<String, Image> cache = new HashMap<>();

public Image getImage(Card card) {
    String key = card.getSuit().name() + "_" + card.getValue().name();
    return cache.computeIfAbsent(key, k -> {
        try {
            return ImageIO.read(new File("images/" + k + ".png"));
        } catch (IOException e) {
            return null;  // GamePanel handles the null case
        }
    });
}
```

---

[Back to README](../README.md) | [Design](design.md) | [Setup](setup.md) | [Implementation Background](implementation.md)
