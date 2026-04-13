# Setup and Controls

[Back to README](../README.md)

---

## Prerequisites

- **Java JDK 21** or later. You can download it from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/).
- **Card image assets** (see below). The images are not included in the repository.
- **Sound files** (optional). The game runs without them; events are just silent.

To check your Java version:

```bash
java -version
```

You should see `21` or higher in the output.

---

## Getting the Code

Clone the repository:

```bash
git clone https://github.com/YourUsername/ratscrewd.git
cd ratscrewd
```

---

## Card Image Assets

The game expects PNG images for each card in an `images/` directory at the project root. The file naming convention is:

```
SUIT_VALUE.png
```

For example:
```
images/
├── HEARTS_ACE.png
├── HEARTS_TWO.png
├── HEARTS_THREE.png
├── ...
├── SPADES_KING.png
├── JOKER_JOKER.png   ← used for both Jokers
└── BACK.png          ← card back
```

The full list of valid suit names is `HEARTS`, `DIAMONDS`, `CLUBS`, `SPADES`, and `JOKER`. Value names follow the `CardValue` enum: `ACE`, `TWO`, `THREE`, `FOUR`, `FIVE`, `SIX`, `SEVEN`, `EIGHT`, `NINE`, `TEN`, `JACK`, `QUEEN`, `KING`, `JOKER`.

Free card image sets in the right format are available from [American Contract Bridge League](https://www.acbl.org/) and similar sources. The images should be approximately 72x100 pixels for the layout to look correct, though the renderer will scale them.

If an image file is missing, the game displays a labeled placeholder rectangle for that card and continues normally.

---

## Running from Eclipse

1. Open Eclipse and go to **File > Import > Existing Projects into Workspace**.
2. Select the cloned `ratscrewd` folder and click **Finish**.
3. Right-click `Main.java` in the Package Explorer.
4. Select **Run As > Java Application**.

Make sure your `images/` and `sounds/` folders are placed at the root of the project directory (at the same level as `src/`), not inside `src/`.

---

## Running as a JAR

If you have a pre-built JAR:

```bash
java -jar ratscrewd.jar
```

Run this command from the directory that contains your `images/` and `sounds/` folders, since the application looks for those folders relative to the working directory.

To export a JAR from Eclipse: right-click the project > **Export > Java > Runnable JAR file**. Set the launch configuration to `Main` and choose a destination.

---

## Controls

| Action | Input |
|--------|-------|
| Play a card | Click on a card in your hand |
| Slap the pile | Click the center discard pile |
| Open Card Store | Appears automatically after winning a round |
| Buy a card pack | Click the pack in the store overlay |
| Reroll store | Click the Reroll button (costs points, price increases each reroll) |
| Proceed to next round | Click Continue after the store |
| Save game | Click Save from the in-game menu |
| Load game | Click Continue on the main menu (only visible when a save file exists) |
| Return to main menu | Click Menu from the in-game menu |

---

## Save File Location

The game saves to:

```
[project root]/saves/gamesave.dat
```

The `saves/` directory is created automatically on first save. Deleting `gamesave.dat` removes the saved session and hides the Continue button on the main menu.

---

## Sound Files

Place `.wav` files in a `sounds/` directory at the project root. The files the game looks for are:

| File | Event |
|------|-------|
| `cardplay.wav` | A card is played onto the center pile |
| `slap.wav` | A successful slap |
| `badslap.wav` | An invalid slap (penalty triggered) |
| `winfaceace.wav` | Human wins a Faces and Aces challenge |
| `losefaceace.wav` | Human loses a Faces and Aces challenge |
| `storeopen.wav` | Card Store opens between rounds |
| `storebuy.wav` | A card pack is purchased |

All audio plays asynchronously. Missing sound files produce no error and no audio for that event.

---

## Known Limitations

- The application does not support window resizing. The UI is designed for a fixed resolution. Maximizing the window may cause layout issues depending on your display scaling settings.
- Saves from version 1 of the application are not compatible with the current version. If loading a save throws an error, delete `saves/gamesave.dat` and start a new game.
- There is no difficulty selection. The AI always starts at round 1 behavior and scales from there.

---

[Back to README](../README.md) | [Design](design.md) | [Features](features.md) | [Implementation Background](implementation.md)
