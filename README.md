# Cribbage Coach

An offline Android trainer for two-player, six-card cribbage. Every hand grades your discard
against all 15 options, then grades every pegging card you had a real choice on, using only what
you could see at the time.

- **Your crib / Their crib:** repeating single-hand drills as the dealer or the non-dealer.
- **Full game:** play the computer to 121. The game saves after every decision, so Continue picks
  up exactly where you left off. The final screen replays your game with the same deals and the
  top-graded choice every time, to show the score perfect play would have reached.
- **Profile:** your skill rating, accuracy (the share of decisions that matched the top choice,
  overall and for discards and pegging), charts of both over time and your recent hands.
- **Settings:** card face and card back styles, and a two-tap reset of your stats.

The skill rating starts at 1000. Each finished hand counts as a performance of 1500 minus 250 per
expected point given up, and the rating moves 5% of the way toward it, so it settles at your
typical play. Hands are kept in `history.csv` in the app's private storage.

## Layout

- `engine`: rules, discard analysis, pegging search and the hand state machine. Plain Kotlin/JVM.
- `tablegen`: simulates strong discards and writes `engine/src/main/resources/discard_tables.txt`.
- `app`: Jetpack Compose UI.

## Commands

Tests, then a sideloadable build at `app/build/outputs/apk/release/app-release.apk`:

```bash
./gradlew :engine:test
./gradlew :app:assembleRelease
```

Regenerate the tables, which takes about three minutes:

```bash
./gradlew :tablegen:run
```

## How advice is worked out

Hand and crib averages are exact. Crib averages weight each opponent discard by how often a strong
player throws those ranks from that seat.

Pegging enumerates every rank combination the opponent could hold, about 1,800 at most, weighted by
how often each rank is kept and filtered by any go they've said. Each is then searched to the end
of the hand with both sides' cards face up. That makes verdicts repeatable, but the face-up search
is a model of real play rather than a proven solution, so pegging values are strong estimates.

Scores near the end of a real game can change the right play. Grades only ever maximise the
current hand, even in a full game, so "perfect play" means the top-graded choice each time rather
than score-aware strategy.

Deals come from a seed and the hand number, and the computer is deterministic. That is what makes
both the perfect-play replay and saved games work: a save holds the seed, the scores and your
choices so far this hand, and restoring replays them.

## Fonts

JetBrains Mono and Inter, both under the SIL Open Font License, in `app/src/main/res/font`.
