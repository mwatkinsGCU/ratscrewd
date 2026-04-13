# Design and Architecture

[Back to README](../README.md)

---

## Class Overview

The project uses a straightforward object-oriented structure. `GameController` sits at the center and runs everything. `GamePanel` handles all drawing. `Player` is an abstract class that both `HumanPlayer` and `AIPlayer` extend.

```mermaid
classDiagram
    direction TB

    class Player {
        <<abstract>>
        +List~Card~ hand
        +List~Card~ personalDrawPile
        +int points
        +play(int index) Card
        +addToHand(Card card)
        +collect(List~Card~ pile)
        +chooseCard(GameContext ctx) Card
        +getSlapDelayMillis(int round) int
    }

    class HumanPlayer {
        +chooseCard(GameContext ctx) Card
        +getSlapDelayMillis(int round) int
    }

    class AIPlayer {
        +chooseCard(GameContext ctx) Card
        +getSlapDelayMillis(int round) int
        -shouldAttemptDouble(int round) boolean
        -findMatchingCard(List~Card~, Card) Card
        -findLowestValidCard(List~Card~, GameContext) Card
    }

    class GameController {
        -GameState gameState
        -List~Player~ players
        -Deck centerDeck
        -List~Card~ discardPile
        -int currentRound
        +initRun()
        +humanPlay(int handIndex)
        +aiPlayTurn()
        +checkSlappable() boolean
        +shouldAISlap() boolean
        +checkRoundEnd()
        +openStore()
        +buyCardPack()
        +saveToFile()
        +loadFromFile() GameController
        +exportSave() GameSave
        +importSave(GameSave save)
    }

    class Deck {
        -List~Card~ cards
        +draw() Card
        +shuffle()
        +addToBottom(List~Card~)
        +size() int
        +isEmpty() boolean
        +getCards() List~Card~
    }

    class Card {
        +Suit suit
        +CardValue value
        +isJoker() boolean
    }

    class GameSave {
        <<Serializable>>
        +String savedGameState
        +int currentRound
        +int[] playerPoints
        +List~Card~[] playerHands
        +List~Card~ discardPile
        +List~Card~ permanentExtraCards
    }

    class GamePanel {
        +paintComponent(Graphics g)
        -drawGameplayScreen(Graphics2D)
        -drawStoreOverlay(Graphics2D)
        -drawGameOverOverlay(Graphics2D)
    }

    class SoundManager {
        +playSound(String filename)$
    }

    class CardImageLoader {
        +getImage(Card card) Image
    }

    Player <|-- HumanPlayer
    Player <|-- AIPlayer
    GameController "1" --> "2" Player
    GameController "1" --> "1" Deck
    GameController "1" --> "*" Card : discardPile
    GameController ..> GameSave : creates/restores
    GameController ..> SoundManager : calls
    GamePanel --> GameController : reads state
    GamePanel ..> CardImageLoader : uses
    Deck "1" --> "*" Card
```

---

## Game State Machine

`GameController` tracks a `GameState` enum that controls what is drawn on screen and what actions are allowed. The state only ever moves in one direction per round.

```mermaid
stateDiagram-v2
    [*] --> PLAYING : New Game or Load Game
    PLAYING --> TIEBREAKER : Both players exhaust cards simultaneously
    PLAYING --> ROUND_TRANSITION : Human wins the round
    PLAYING --> GAME_OVER : AI wins the round
    TIEBREAKER --> ROUND_TRANSITION : Human wins tiebreaker
    TIEBREAKER --> GAME_OVER : AI wins tiebreaker
    ROUND_TRANSITION --> STORE : Player confirms transition
    STORE --> PLAYING : Next round begins
    GAME_OVER --> [*]
```

`GamePanel` reads `gameState` on every repaint and switches between the gameplay screen, store overlay, tiebreaker display, and game-over screen accordingly.

---

## System Design Diagram

The diagram below shows how the major subsystems connect at runtime.

```mermaid
flowchart TD
    Main["Main.java\n(entry point)"] --> MenuPanel
    MenuPanel -->|New Game| GC
    MenuPanel -->|Continue| GC

    GC["GameController\n(game logic)"] --> GP["GamePanel\n(rendering)"]
    GC --> SM["SoundManager\n(audio)"]
    GC --> Deck
    GC --> Players["HumanPlayer / AIPlayer"]
    GC -->|save / load| FS[(File System\ngamesave.dat)]

    GP --> CIL["CardImageLoader\n(image cache)"]
    GP -->|mouse events| GC
```

---

## Game Loop

Each turn follows this sequence:

```mermaid
sequenceDiagram
    participant GP as GamePanel
    participant GC as GameController
    participant AI as AIPlayer

    Note over GC: gameState == PLAYING
    GP->>GC: humanPlay(handIndex)
    GC->>GC: play card, add to discard pile
    GC->>GC: checkSlappable()
    alt Slap condition exists
        GC-->>GP: slapWindowActive = true
        GP->>GP: schedule AI slap timer
        GP->>GC: shouldAISlap()
        alt AI slaps
            GC->>AI: AI collects pile
        else AI misses
            GC->>GC: closeSlapWindow()
        end
    else No slap condition
        GC->>GC: enforceFaceAceRule()
        GC->>GC: advance turn
        GP->>GC: aiPlayTurn()
    end
    GC->>GC: checkRoundEnd()
```

---

## Architecture Decisions

**Why GameController is one large class**

The original CST-451 design planned for separate `SlapDetector` and `RoundManager` classes. During development, slap detection and round-end logic both needed continuous access to the full game state, specifically the discard pile, player hands, and turn counters. Keeping them in `GameController` avoided a web of getters and cross-references that would have made the code harder to follow, not easier. A future refactor could extract those responsibilities once the interfaces are more stable.

**Why Player is abstract**

`HumanPlayer` and `AIPlayer` share the same hand, draw pile, and points bookkeeping. The only things that differ are `chooseCard()` and `getSlapDelayMillis()`. Making `Player` abstract lets `GameController` hold a `List<Player>` and call the same methods on both, without caring which type it is talking to.

**Why GameSave is a separate class**

`GameController` holds Swing timers and a `Random` instance, neither of which can be serialized safely. `GameSave` is a plain data class with no behavior, making it straightforward to serialize and deserialize without side effects. `exportSave()` snapshots the state into a `GameSave`, and `importSave()` reads it back.

---

[Back to README](../README.md) | [Features and Code](features.md) | [Setup](setup.md)
