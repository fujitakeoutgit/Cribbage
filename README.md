# Cribbage Coach

An offline Android trainer for the best play on a single hand of two-player, six-card cribbage.
Each hand grades your discard against all 15 options, then grades every pegging card you had a
real choice on, using only what you could see at the time.

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

Scores near the end of a real game can change the right play. This app only ever maximises the
current hand.

## Fonts

JetBrains Mono and Inter, both under the SIL Open Font License, in `app/src/main/res/font`.
