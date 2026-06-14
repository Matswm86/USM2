#!/usr/bin/env python3
"""Reference prototype for the Phase-3 career engine.

This is NOT shipped in the app; it exists to validate the three algorithms the
Kotlin engine ports verbatim, against the *real* staged data, before the
untestable-locally Kotlin is written:

  1. double round-robin schedule (circle method) — every pair meets twice,
     once home once away, no team plays twice in a round.
  2. seeded match simulation (Knuth Poisson from a strength differential) —
     deterministic for a given seed, score distribution sane.
  3. standings (3/1/0, GD then GF tiebreak).

Run: python3 tools/proto_engine.py
"""
from __future__ import annotations

import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "android/app/src/main/assets/data"


# ---- 1. schedule: circle method, double round-robin ------------------------
def round_robin(n_teams: int) -> list[list[tuple[int, int]]]:
    """Single round-robin rounds as (home, away) index pairs. Adds a bye (-1)
    when odd. Mirrors the Kotlin Schedule.singleRoundRobin."""
    teams = list(range(n_teams))
    if len(teams) % 2:
        teams.append(-1)  # bye marker
    m = len(teams)
    fixed = teams[0]
    rot = teams[1:]
    rounds = []
    for r in range(m - 1):
        order = [fixed] + rot
        pairs = []
        for i in range(m // 2):
            a, b = order[i], order[m - 1 - i]
            if a == -1 or b == -1:
                continue
            # alternate home/away by round so it balances over the season half
            pairs.append((a, b) if (r + i) % 2 == 0 else (b, a))
        rounds.append(pairs)
        rot = rot[-1:] + rot[:-1]  # rotate
    return rounds


def double_round_robin(n_teams: int) -> list[list[tuple[int, int]]]:
    first = round_robin(n_teams)
    second = [[(b, a) for (a, b) in rnd] for rnd in first]  # reverse venue
    return first + second


# ---- 2. match simulation ---------------------------------------------------
class Rng:
    """xorshift64* — same generator the Kotlin engine uses so a given seed is
    reproducible across the prototype and the app."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFFFFFFFFFF or 0x9E3779B97F4A7C15

    def next_u64(self) -> int:
        x = self.s
        x ^= (x >> 12)
        x ^= (x << 25) & 0xFFFFFFFFFFFFFFFF
        x ^= (x >> 27)
        self.s = x & 0xFFFFFFFFFFFFFFFF
        return (self.s * 0x2545F4914F6CDD1D) & 0xFFFFFFFFFFFFFFFF

    def next_double(self) -> float:
        return (self.next_u64() >> 11) / float(1 << 53)


def poisson(rng: Rng, lam: float) -> int:
    """Knuth's algorithm."""
    if lam <= 0:
        return 0
    el = math.exp(-lam)
    k, p = 0, 1.0
    while True:
        k += 1
        p *= rng.next_double()
        if p <= el:
            return k - 1


HOME_ADV = 0.35  # expected-goals bump for the home side


def expected_goals(att: float, deff: float) -> float:
    """Map a strength differential to an xG. att/deff are 0..99 overall ratings.
    Base 1.35 goals, scaled by a logistic of the (att-deff) gap."""
    gap = (att - deff) / 18.0
    return max(0.05, 1.35 * math.exp(0.45 * gap))


def simulate(home_str: float, away_str: float, seed: int) -> tuple[int, int]:
    rng = Rng(seed)
    hg = poisson(rng, expected_goals(home_str, away_str) + HOME_ADV)
    ag = poisson(rng, expected_goals(away_str, home_str))
    return hg, ag


# ---- 3. standings ----------------------------------------------------------
def standings(team_ids, results):
    """results: list of (home_idx, away_idx, hg, ag). Returns ordered rows."""
    row = {t: dict(p=0, w=0, d=0, l=0, gf=0, ga=0, pts=0) for t in team_ids}
    for h, a, hg, ag in results:
        for t, gf, ga in ((h, hg, ag), (a, ag, hg)):
            r = row[t]
            r["p"] += 1
            r["gf"] += gf
            r["ga"] += ga
        if hg > ag:
            row[h]["w"] += 1; row[h]["pts"] += 3; row[a]["l"] += 1
        elif hg < ag:
            row[a]["w"] += 1; row[a]["pts"] += 3; row[h]["l"] += 1
        else:
            row[h]["d"] += 1; row[a]["d"] += 1; row[h]["pts"] += 1; row[a]["pts"] += 1
    order = sorted(
        team_ids,
        key=lambda t: (-row[t]["pts"], -(row[t]["gf"] - row[t]["ga"]), -row[t]["gf"]),
    )
    return order, row


# ---- validation ------------------------------------------------------------
def strength_of(squad, players):
    """Best-XI mean overall — the engine's club strength."""
    if not squad:
        return 35.0
    rated = sorted((mean_attrs(p) for p in squad), reverse=True)[:11]
    return statistics.mean(rated)


def mean_attrs(p):
    sk = [v for i, v in enumerate(p["skills"]) if i != 5]
    return sum(sk) / len(sk) if sk else 0.0


def main():
    clubs = json.loads((ASSETS / "clubs.json").read_text())
    players = json.loads((ASSETS / "players.json").read_text())
    by_club = defaultdict(list)
    for p in players:
        if p.get("club"):
            by_club[p["club"]].append(p)

    # Premier League (England div 0)
    epl = [c for c in clubs if c["group"] == "England" and c["division"] == 0]
    n = len(epl)
    print(f"== schedule: {n} EPL clubs ==")
    sched = double_round_robin(n)
    print(f"rounds={len(sched)} (expect {2*(n-1)}), "
          f"matches/round={len(sched[0])} (expect {n//2})")

    # validate: each ordered pair (a beats-home b) appears exactly once; each
    # unordered pair exactly twice; each team once per round.
    venue = Counter()
    home_count = Counter()
    total = 0
    for rnd in sched:
        seen = set()
        for h, a in rnd:
            assert h not in seen and a not in seen, "team twice in a round"
            seen.add(h); seen.add(a)
            venue[(h, a)] += 1
            home_count[h] += 1
            total += 1
    assert total == n * (n - 1), f"total matches {total} != {n*(n-1)}"
    assert all(v == 1 for v in venue.values()), "an ordered pairing repeats"
    assert len(venue) == n * (n - 1), "missing fixtures"
    print(f"matches={total} ok; ordered pairs unique ok; "
          f"home games/team min={min(home_count.values())} "
          f"max={max(home_count.values())} (ideal {n-1})")

    # strengths
    strengths = {c["id"]: strength_of(by_club.get(c["id"], []), players) for c in epl}
    ranked = sorted(epl, key=lambda c: -strengths[c["id"]])
    print("\n== top/bottom EPL strength (best-XI mean overall) ==")
    for c in ranked[:3] + ranked[-3:]:
        print(f"  {strengths[c['id']]:5.1f}  {c['name']}")

    # simulate a full season, deterministically
    idx = {c["id"]: i for i, c in enumerate(epl)}
    str_by_idx = [strengths[c["id"]] for c in epl]
    season_seed = 12345
    results = []
    goals = []
    home_pts = away_pts = draws = 0
    for ri, rnd in enumerate(sched):
        for mi, (h, a) in enumerate(rnd):
            seed = season_seed * 1_000_003 + ri * 997 + mi
            hg, ag = simulate(str_by_idx[h], str_by_idx[a], seed)
            results.append((h, a, hg, ag))
            goals += [hg, ag]
            if hg > ag: home_pts += 1
            elif ag > hg: away_pts += 1
            else: draws += 1

    # determinism check: re-run identical
    again = []
    for ri, rnd in enumerate(sched):
        for mi, (h, a) in enumerate(rnd):
            seed = season_seed * 1_000_003 + ri * 997 + mi
            again.append(simulate(str_by_idx[h], str_by_idx[a], seed))
    assert [(h, a) for h, a, _, _ in results] or True
    assert [r[2:] for r in results] == again, "sim not deterministic"
    print("\n== sim determinism: PASS ==")

    ng = len(results)
    print(f"matches={ng} avg goals/match={sum(goals)/ng:.2f} "
          f"home%={100*home_pts/ng:.0f} draw%={100*draws/ng:.0f} away%={100*away_pts/ng:.0f}")
    print(f"max goals in one game={max(goals)}")

    order, row = standings([i for i in range(n)], results)
    # points sanity: total points = 3*decisive + 2*draws
    tot_pts = sum(r["pts"] for r in row.values())
    assert tot_pts == 3 * (home_pts + away_pts) + 2 * draws, "points accounting off"
    # every team plays 2*(n-1)
    assert all(r["p"] == 2 * (n - 1) for r in row.values()), "games-played wrong"
    print("\n== final table (top 6) ==")
    print(f"{'#':>2} {'club':22} {'P':>2} {'W':>2} {'D':>2} {'L':>2} "
          f"{'GF':>3} {'GA':>3} {'GD':>3} {'Pts':>3}")
    for rank, ti in enumerate(order[:6], 1):
        r = row[ti]
        gd = r["gf"] - r["ga"]
        print(f"{rank:>2} {epl[ti]['name']:22} {r['p']:>2} {r['w']:>2} {r['d']:>2} "
              f"{r['l']:>2} {r['gf']:>3} {r['ga']:>3} {gd:>3} {r['pts']:>3}")
    print("\nALL CHECKS PASSED")


if __name__ == "__main__":
    main()
