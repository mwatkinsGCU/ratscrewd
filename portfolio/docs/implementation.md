# Development Background

[Back to README](../README.md)

---

## Where the Idea Came From

I have played Egyptian Rat Screw since I was a kid and always thought the slap mechanic would translate well to a digital format. The speed element, specifically the fact that reaction time matters as much as the cards you are dealt, seemed like something an AI opponent could replicate in an interesting way. Most digital card games lean into turn-based play where speed is irrelevant. ERS does not.

When it came time to choose a capstone project, building a game felt like the most direct way to put together everything I had learned in the program: object-oriented design, event handling, state management, and testing. It also gave me something I actually wanted to sit down and play.

---

## CST-451: Planning and Design

The first semester was spent on the design before writing any application code. That included:

- Writing use cases and functional requirements for every planned feature
- Building a class diagram and a logical model for the game loop
- Deciding on the technology stack (Java 21, Swing, Eclipse)
- Planning features that went beyond the base ERS rules: Power Stickers, Upgrade Packs, a standalone SlapDetector class, a standalone RoundManager class, and eventual distribution on itch.io

By the end of CST-451, the design looked solid on paper. The gap between a clean design document and working code is something you only really feel once you start building.

---

## CST-452: Building It

Implementation ran across two milestone iterations. Each one followed the same basic loop: build a working version, run the test cases, find the problems, and fix them.

**First iteration** covered the core gameplay: deck setup, dealing, turn taking, slap detection, Faces and Aces, and round-end conditions. The AI existed but was passive, with no real scaling yet.

**Second iteration** added the AI difficulty system, the Card Store, save and load, sound effects, and the full UI including the main menu, store overlay, tiebreaker screen, and game-over screen.

---

## Scope Changes

About halfway through the second iteration, it became clear that some features from the original design were not going to fit. The Capstone Project Handbook's Milestone 4-5 Special Note explicitly allows for scope reduction when timeline constraints make it necessary, and I used that provision for the following:

**Removed:**

- *SlapDetector as a standalone class.* The slap detection logic needed constant access to the discard pile, turn counter, and slap grace period state. Pulling it into its own class would have required passing all of that in, which made it more complicated, not less. The logic is now inside `GameController.checkSlappable()` and `shouldAISlap()`.

- *RoundManager as a standalone class.* Same situation. Round-end detection touches player hands, personal draw piles, the center deck state, and the cycle-limit tracking fields. Keeping it inside `GameController` was the cleaner call for a solo developer working against a deadline.

- *Distribution on itch.io or Steam.* Setting up a public store page, handling platform-specific packaging, and writing marketing copy was outside the scope of what the capstone timeline could accommodate. The application runs locally.

**Deferred:**

- *Power Stickers.* The Sticker class was designed but never built. Adding temporary per-card modifiers would have required new UI overlays, changes to how the game state is tracked, and additional save/load fields. Attempting to squeeze that in would have put the stability of the core game at risk.

- *Upgrade Packs.* The Card Store was simplified to sell card packs only. Permanent card attribute upgrades needed a separate metadata tracking system for individual cards, which was not realistic given the time left.

All of these decisions were documented in the Milestone 4-5 submission and reviewed with the course mentor.

---

## Challenges

**Getting the slap timing right** was the hardest part of the project. The AI needs to react to a slap condition after a short delay, but the delay timer has to fire at the right time relative to the UI repaint cycle. There were several versions of this before it worked correctly. The current approach uses `slapWindowActive` as a flag that blocks card plays while the timer is running, and `closeSlapWindow()` to resume the turn if the AI decides not to slap.

**Managing game state across the UI** took more planning than expected. `GamePanel` reads `gameState` from `GameController` on every repaint. Getting all the overlays, including gameplay, store, tiebreaker, and game-over screens, to switch cleanly without leftover state required being very deliberate about what each state transition resets and what it leaves in place.

**Save and load** looked simple until I tried to make it actually reliable. The first approach tried to serialize `GameController` directly, which failed because Swing timers and `Random` are not serializable. Moving to a separate `GameSave` data class fixed that. The main requirement is that every field that describes the game state has a corresponding field in `GameSave`, and that `importSave()` restores them in the right order.

**The cycle-limit tiebreaker** took a few iterations. The original design had a fixed cycle cap. The final version tracks whether the center deck has been depleted, then monitors each subsequent cycle to see if either player gained 5 or more cards net. If 10 cycles pass without that happening, a tiebreaker is triggered. This feels fairer because it gives both players a real chance to break the stalemate before the game forces an end.

---

## What I Would Do Differently

The biggest thing is that `GameController` ended up carrying too much responsibility. It handles game logic, AI coordination, store management, save/load, tiebreaker resolution, and slap timing all in one class. That worked fine for getting the project across the finish line, but it makes individual features harder to test in isolation.

Given more time, I would extract `SlapDetector` and `RoundManager` back out as standalone classes, this time with well-defined interfaces so the coupling issue does not come back. I would also write unit tests for the AI difficulty formulas and the cycle-limit logic specifically, since those are the pieces most likely to behave unexpectedly at the edges.

The card image loading system is also a bit fragile. Right now, a missing image silently falls back to a placeholder rectangle. That is fine for development but it would be better to fail loudly at startup and tell the user exactly which files are missing.

---

## What I Learned

The most useful thing this project taught me is that design documents do not automatically reflect what is possible to build in the time you have. The CST-451 design was technically sound. The problem was scope. Building everything in that design as a single developer in two semesters was never realistic, and recognizing that early enough to adjust the scope without breaking what was already working required judgment that is hard to develop without actually going through it.

On the technical side, the AI difficulty system was the part I found most interesting to tune. The slap delay formula is simple: `Math.max(300, 1400 - (round - 1) * 55)`. But the accuracy rate, the false-slap probability, and the double-aggression thresholds all interact, and getting those numbers to feel right took quite a bit of playtesting. The AI in round 1 should lose most of the time. The AI in round 10 should feel genuinely threatening. That balance took longer to find than I expected.

---

[Back to README](../README.md) | [Design](design.md) | [Features](features.md) | [Setup](setup.md)
