# Phase 3 — Career / Season Engine

Pure-Kotlin management loop layered over the staged JSON DB. No Android types in
the engine package, so it is portable and (in principle) JVM-testable. The
algorithms were prototyped and validated against the real EPL data in
`tools/proto_engine.py` before the (locally un-compilable) Kotlin was written.

## Loop

Open any club in a real league tier → **Take charge** → a single-division
career: a double round-robin season is generated, club strengths are frozen,
and each tap of **Play Matchday** simulates the whole division's next round and
updates the table. State is saved to the app's private storage after every
matchday, so a career survives app restarts (resume from the office).

## Package `no.mwmai.usm2.engine`

| File | Role |
|------|------|
| `Rng.kt` | xorshift64\* PRNG, seeded per fixture so a loaded save replays identically. |
| `Schedule.kt` | Double round-robin via the circle method. Each ordered (home, away) pair occurs once; every pair meets twice (once per venue); balanced home/away counts. Team order is shuffled by seed so each career has a distinct calendar. |
| `Strength.kt` | Club strength = best-XI mean of `Player.rating` (0-99). **Refinement hook:** splits into attack/defence lines once the attribute-column names are recovered from the EXE, without changing the engine interface. |
| `Sim.kt` | Strength differential → expected goals → seeded Poisson scoreline, with a fixed home bump. Tuned to ~2.9 g/match, ~46/27/27 home/draw/away over a season. |
| `Standings.kt` | League table from played fixtures: 3/1/0, ordered by points, then GD, then GF. |
| `Career.kt` | Serializable, self-contained career state (`Fixture` + `Career`). Strengths are frozen at season start, so advancing needs no `GameData`. All mutation returns a new `Career`. |
| `CareerFactory.kt` | Builds a `Career` from `GameData` + a chosen club. Gates "manageable" to divisions of 2-30 clubs (admits England 20-24 / France 18-22 / Germany 18; excludes the 112-club International transfer pool). |

## Validation (`tools/proto_engine.py`)

Run `python3 tools/proto_engine.py`. Asserts, against the staged 20-club EPL:
schedule = 38 rounds × 10 matches = 380, every ordered pair unique, 19 home
games/team; the sim is deterministic for a seed; points accounting and
games-played are exact; strengths rank realistically (Newcastle / Man Utd /
Liverpool top for 96/97).

## Known limits / next

- Single division, single season (no promotion/relegation, no rollover yet).
- No transfers-with-budget, finances, training, or board confidence yet.
- Strength is a single overall until the EXE attribute-column names land; the
  sim has no lineups/injuries/form. These are additive — the `Strength.of`
  internals change, the engine interface does not.
