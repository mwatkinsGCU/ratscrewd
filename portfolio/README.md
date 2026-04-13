# Ratscrew'd

A single-player Java desktop card game based on Egyptian Rat Screw, built as a two-semester capstone project at Grand Canyon University (CST-451 and CST-452).

---

## What It Is

Egyptian Rat Screw is a fast card game where players take turns flipping cards onto a center pile. When a pair lands on top, or a Joker gets played, the first person to slap the pile takes all the cards. Face cards and Aces add a challenge layer where the opponent has to match them, or lose the pile.

Ratscrew'd takes that base game and builds on it:

- A reactive AI opponent whose speed and aggression increase each round
- A between-round Card Store where you spend earned points on extra cards for future decks
- Full save and load support so you can pick up a session where you left off
- Sound effects for slaps, card plays, and store events

The game runs as a standalone Java desktop application. No install required beyond having Java 21 on your machine.

---

## Navigation

| Page | What's There |
|------|-------------|
| [Design and Architecture](docs/design.md) | Class hierarchy, game state machine, module overview |
| [Features and Code](docs/features.md) | Each major feature with real source code |
| [Development Background](docs/implementation.md) | Planning, scope decisions, challenges, what I learned |
| [Setup and Controls](docs/setup.md) | How to clone, build, and run the project |

---

## Tech Stack

| | |
|---|---|
| Language | Java 21 |
| GUI Framework | Java Swing |
| Persistence | Java Object Serialization |
| IDE | Eclipse 2024.12 |
| Build | JAR export via Eclipse |
| Source Control | Git / GitHub |

---

## Project Status

The project is complete as a capstone submission. All nine use cases and 14 functional requirements were implemented and tested across two milestone iterations. Some features from the original CST-451 design were cut or deferred to keep the schedule realistic for a solo developer. Those decisions are documented in [Development Background](docs/implementation.md).

---

## Repository Contents

```
ratscrewd/
├── src/
│   └── ratscrewd/
│       ├── Main.java
│       ├── game/
│       │   ├── GameController.java
│       │   └── GameSave.java
│       ├── model/
│       │   ├── Card.java
│       │   ├── CardValue.java
│       │   ├── Deck.java
│       │   └── Suit.java
│       ├── players/
│       │   ├── Player.java
│       │   ├── HumanPlayer.java
│       │   ├── AIPlayer.java
│       │   └── GameContext.java
│       ├── ui/
│       │   ├── GamePanel.java
│       │   ├── MenuPanel.java
│       │   └── HowToPlayDialog.java
│       └── util/
│           ├── CardImageLoader.java
│           ├── SoundManager.java
│           └── Constants.java
├── images/       ← card image assets (not included in repo)
├── sounds/       ← .wav audio files (not included in repo)
└── README.md
```

---

*Built by Michael Watkins — CST-452 Senior Project II, Grand Canyon University, Spring 2026*
