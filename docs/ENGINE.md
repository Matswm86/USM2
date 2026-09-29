# Phase 3 — Career / Season Engine

Pure-Kotlin management loop layered over the staged JSON DB. No Android types in
the engine package, so it is portable and (in principle) JVM-testable. The
algorithms were prototyped and validated against the real EPL data in
`tools/proto_engine.py` before the (locally un-compilable) Kotlin was written.

## Loop

Open any club in a real league tier → **Take charge** → a single-division
career: a double round-robin season is generated, club strengths are frozen,
and each tap of **Play Matchday** simulates the whole division's next round and
updates the table. The managed club's fixture is watched first in the animated
match view; when it ends, the round is recorded. State is saved to the app's
private storage after every matchday, so a career survives app restarts (resume
from the office).

## Package `no.mwmai.usm2.engine`

| File | Role |
|------|------|
| `Rng.kt` | xorshift64\* PRNG, seeded per fixture so a loaded save replays identically. |
| `Schedule.kt` | Double round-robin via the circle method. Each ordered (home, away) pair occurs once; every pair meets twice (once per venue); balanced home/away counts. Team order is shuffled by seed so each career has a distinct calendar. |
| `Strength.kt` | Club strengths on the 0-99 scale. `of` = best-XI mean of `Player.rating` (used for sorting and the legacy model); `attack` = mean Attacking of the five best attackers; `defence` = 0.75 × mean Defending of the four best defenders + 0.25 × the best keeper's Goalkeeping. |
| `Sim.kt` | Attack vs opposing defence → expected goals, centred on the division's own attack-minus-defence gap → seeded Poisson scoreline per side, with a fixed home bump. Also draws the goal minutes the match view shows. Constants tuned against the real EPL data in `tools/proto_engine.py`; saves made before the attack/defence split fall back to the single-strength model (`playOverall`). |
| `Standings.kt` | League table from played fixtures: 3/1/0, ordered by points, then GD, then GF. |
| `Career.kt` | Serializable, self-contained career state (`Fixture`, `ClubStrength`, `Tier`, `Career`). Strengths are frozen at career start and re-frozen for the clubs a transfer or a new starting XI touches; the whole group **pyramid** travels in the save, so advancing a season AND rolling it over needs no `GameData`. Also holds the transfer list, the budget, the season's wages and gate income, and the chosen starting XI. All mutation returns a new `Career`. |
| `CareerFactory.kt` | Builds a `Career` from `GameData` + a chosen club. Gates "manageable" to divisions of 2-30 clubs (admits England 20-24 / France 18-22 / Germany 18; excludes the 112-club International transfer pool). Captures the group's full pyramid + every club's frozen strength for rollover, and seeds the starting transfer budget (30% of squad value, floor £250k), wage bill and club-size factor. |
| `Valuation.kt` | Transfer value in £k from overall rating (cubic curve) and age (peak 24-27, steep fall after 30). Buying costs value + 10%, selling brings value - 10%, so a buy-then-sell round trip is a net loss. |
| `Transfer.kt` | One recorded `Transfer` (player, destination or released, fee) and `TransferLimits` (a squad of 12 to 30). The career keeps the full list; the last move per player gives his current club. |
| `Finance.kt` | Season finances derived from squad value and tier: wages charged each matchday the club plays, gate income on home games (tier base scaled by club size), prize money by final league position at season end, and a one-off bonus for winning promotion. |
| `Weather.kt` | `pitchConditionFor`: a deterministic DRY / MUD / WET / ICE pitch per fixture, seasonal (bad pitches cluster mid-season, ICE only in deep winter). Cosmetic: it picks the match-view backdrop from an RNG stream salted off the goal seed, so it can never change a score. |

## Season rollover (promotion / relegation)

When the managed season completes, **Start Season N+1** (`Career.rolloverSeason`)
promotes/relegates across the whole group pyramid and generates a fresh season:

- The managed tier's outcome uses the **real played table**; every other tier is
  settled by a deterministic full-season simulation from the frozen strengths.
- Top `promotionSlots` of each tier go up, bottom `promotionSlots` go down,
  computed simultaneously off the pre-move orders, so clubs are conserved.
- The managed club's new tier becomes next season's active division; a new fixture
  list + a centred `leagueGap` are rebuilt from the frozen strengths; `season++`.
- The managed club is paid the season's prize money by its final position, plus a
  bonus if it moved up a tier; the season's wage and gate tallies reset.

A no-op before the season is complete or for pre-rollover saves (empty pyramid).
The league table marks promotion (green) and relegation (red) zones live.

## Validation (`tools/proto_engine.py`)

Run `python3 tools/proto_engine.py`. Against the staged 20-club EPL it asserts:
schedule = 38 rounds × 10 matches = 380 with every ordered pair unique; the sim
is deterministic for a seed; points accounting and games-played are exact. It
prints the home games per team, the strongest and weakest clubs and a final table
as a sanity check. It then runs one validation per feature, each asserting the
property the Kotlin relies on:

- rollover: clubs conserved, tier sizes unchanged, result deterministic;
- match timeline: the previewed score equals the score the round records; goal
  minutes are 1-90 and deterministic;
- weather: deterministic, salted off the goal seed, ICE only in deep winter, DRY
  the most common pitch;
- transfers: budget debited by the fee, no money pump on a buy-then-sell, squad
  floor enforced;
- finances: the running budget equals its closed form; prize money falls with
  league position;
- lineup: a weaker XI gives a weaker side; a committed XI is honoured, pruned on
  a sale and refilled to eleven.

## Known limits / next

- Only the managed club trades. Buying takes a player from his club and selling
  releases him to an abstract buyer; AI clubs never sign, sell or swap players on
  their own. The seed DB ships no balances, so budget, wages, gate and prize
  money are derived from squad value and tier (`Valuation.kt`, `Finance.kt`).
- No ageing: players do not age or develop between seasons.
- No injuries / suspensions / training / board confidence.
- The result is fixed by the engine before the match view starts, and the view
  animates toward it. In-match substitutions (three per game) change who is on
  the pitch and who is credited with a goal, not the score. The managed club's
  starting XI can be picked by hand (`Career.setXI`), and a weaker XI fields a
  weaker side.
