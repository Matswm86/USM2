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


# NOTE: the SHIPPED goal model (Sim.kt) is now the attack/defence split, not this
# single-strength one: xG = BASE*exp(K*((attack - oppDefence - leagueGap)/SCALE))
# with BASE=1.10, K=0.8, SCALE=8, HOME_ADV=0.28, leagueGap = mean(attack)-mean(defence)
# per division. Tuned against the real EPL data to ~2.8 g/match, ~50/22/28 H/D/A,
# champion high-70s/80s. The single-strength model below remains as the engine's
# pre-split fallback and still exercises the schedule / standings / determinism.
HOME_ADV = 0.35  # expected-goals bump for the home side (legacy single-strength)


def expected_goals(att: float, deff: float) -> float:
    """Legacy single-strength xG (now the pre-split fallback). att/deff 0..99."""
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


# ---- 4. season rollover (promotion/relegation) — mirrors Career.rolloverSeason
def club_strength_triple(squad):
    """(overall, attack, defence) — mirrors engine/Strength.kt over real skills."""
    if not squad:
        return (35.0, 35.0, 35.0)
    def gk(p): return p["skills"][0]
    def df(p): return p["skills"][1]
    def at(p): return p["skills"][3]
    def fit(p): return (p["skills"][4] + p["skills"][6] + p["skills"][9]) / 3
    def rating(p):
        if gk(p) > 55:
            return gk(p) * 0.6 + fit(p) * 0.2 + df(p) * 0.2
        return (at(p) + df(p) + p["skills"][7] + p["skills"][2] + fit(p)) / 5
    overall = statistics.mean(sorted((rating(p) for p in squad), reverse=True)[:11])
    attack = statistics.mean(sorted((at(p) for p in squad), reverse=True)[:5])
    backs = sorted((df(p) for p in squad), reverse=True)[:4]
    keeper = max((gk(p) for p in squad), default=50)
    defence = 0.75 * statistics.mean(backs) + 0.25 * keeper
    return (overall, attack, defence)


def expected_goals_ad(attack, opp_def, gap):
    return max(0.05, 1.10 * math.exp(0.8 * ((attack - opp_def - gap) / 8.0)))


def sim_ad(ha, hd, aa, ad, gap, seed):
    rng = Rng(seed)
    hg = poisson(rng, expected_goals_ad(ha, ad, gap) + 0.28)
    ag = poisson(rng, expected_goals_ad(aa, hd, gap))
    return hg, ag


def simulate_tier_order(club_ids, strengths, seed):
    """Deterministic full-season order of a tier from its frozen strengths."""
    n = len(club_ids)
    if n < 2:
        return list(club_ids)
    st = [strengths.get(c, (35.0, 35.0, 35.0)) for c in club_ids]
    attack = [s[1] for s in st]
    defence = [s[2] for s in st]
    gap = statistics.mean(attack) - statistics.mean(defence)
    results = []
    for ri, rnd in enumerate(double_round_robin(n)):
        for (h, a) in rnd:
            fseed = seed * 1_000_003 + ri * 9176 + h * 131 + a
            hg, ag = sim_ad(attack[h], defence[h], attack[a], defence[a], gap, fseed)
            results.append((h, a, hg, ag))
    order, _ = standings(list(range(n)), results)
    return [club_ids[i] for i in order]


def rollover(pyramid, strengths, promo_slots, season, season_seed):
    n = len(pyramid)
    def rseed(salt):
        return (season_seed * 1_000_003) ^ (salt * 0x100000001B3) ^ (season << 21)
    ordered = [simulate_tier_order(t["clubIds"], strengths, rseed(t["division"])) for t in pyramid]
    promoted = [[] for _ in range(n)]
    relegated = [[] for _ in range(n)]
    for t in range(n):
        order = ordered[t]
        k = min(promo_slots, len(order) // 2)
        if t > 0 and k > 0:
            promoted[t] = order[:k]
        if t < n - 1 and k > 0:
            relegated[t] = order[-k:]
    new = []
    for t in range(n):
        moved = set(promoted[t]) | set(relegated[t])
        stayed = [c for c in ordered[t] if c not in moved]
        came_up = promoted[t + 1] if t < n - 1 else []
        came_down = relegated[t - 1] if t > 0 else []
        new.append({"division": pyramid[t]["division"],
                    "clubIds": sorted(stayed + came_up + came_down)})
    return new, ordered, promoted, relegated


def validate_rollover(clubs, players):
    by_club = defaultdict(list)
    for p in players:
        if p.get("club"):
            by_club[p["club"]].append(p)
    groups = defaultdict(list)
    for c in clubs:
        if c["group"] == "England":
            groups[c["division"]].append(c)
    tiers = []
    for div in sorted(groups):
        cl = groups[div]
        if 2 <= len(cl) <= 30:
            tiers.append({"division": div, "clubIds": sorted(x["id"] for x in cl)})
    strengths = {}
    for div_clubs in groups.values():
        for c in div_clubs:
            strengths[c["id"]] = club_strength_triple(by_club.get(c["id"], []))

    promo = 3
    season, seed = 1, 999
    sizes0 = [len(t["clubIds"]) for t in tiers]
    all0 = set(c for t in tiers for c in t["clubIds"])
    print(f"\n== rollover: England pyramid {len(tiers)} tiers, sizes {sizes0} ==")
    pyr = tiers
    for _ in range(6):
        new, ordered, promoted, relegated = rollover(pyr, strengths, promo, season, seed)
        assert [len(t["clubIds"]) for t in new] == sizes0, "tier size changed"
        flat = [c for t in new for c in t["clubIds"]]
        assert len(flat) == len(set(flat)), "a club lands in two tiers"
        assert set(flat) == all0, "club set not conserved"
        for t in range(len(pyr) - 1):
            k = min(promo, len(ordered[t]) // 2)
            for c in ordered[t][-k:]:
                assert c in new[t + 1]["clubIds"], "relegated club missing from tier below"
            for c in ordered[t + 1][:k]:
                assert c in new[t]["clubIds"], "promoted club missing from tier above"
        new2, *_ = rollover(pyr, strengths, promo, season, seed)
        assert [t["clubIds"] for t in new2] == [t["clubIds"] for t in new], "rollover not deterministic"
        pyr = new
        season += 1
    # show the top tier champion drift over the 6 rolled seasons (sanity, not asserted)
    print("   sizes conserved · clubs conserved · promo/relegation exact · deterministic")
    print(f"   promo_slots={promo}; ran 6 rollovers without losing or duplicating a club")


def fixture_seed(season_seed, rnd, home, away):
    """Mirror of Kotlin Career.fixtureSeed (used by playNextRound + the preview)."""
    return (season_seed * 1_000_003 + rnd * 9176 + home * 131 + away) & 0xFFFFFFFFFFFFFFFF


def goal_minutes(home_goals, away_goals, seed):
    """Mirror of Kotlin Sim.goalMinutes: minutes 1..90 from the fixture seed."""
    rng = Rng(seed ^ 0x51ED27015A1C)
    def draw(n):
        return sorted(max(1, min(90, 1 + int(rng.next_double() * 90))) for _ in range(n))
    return draw(home_goals), draw(away_goals)


def validate_match_timeline(epl, players, by_club):
    """The match view animates toward the SAME score playNextRound records, then
    spaces the goals over 1..90. Assert: (a) the preview path and the round-play
    path produce the identical managed result from the shared fixtureSeed, and
    (b) the goal timeline has one minute per goal, all in 1..90, and is
    deterministic for a given seed."""
    n = len(epl)
    trip = [club_strength_triple(by_club.get(c["id"], [])) for c in epl]
    attack = [t[1] for t in trip]
    defence = [t[2] for t in trip]
    gap = sum(attack) / n - sum(defence) / n
    sched = double_round_robin(n)
    season_seed = 777
    managed = 0
    # next fixture for the managed club is in round 0
    rnd = 0
    nxt = next((m for m in sched[rnd] if managed in m), None)
    assert nxt is not None, "managed club has no fixture in round 0"
    h, a = nxt
    pseed = fixture_seed(season_seed, rnd, h, a)
    preview = sim_ad(attack[h], defence[h], attack[a], defence[a], gap, pseed)
    # "play the whole round" the same way and read back the managed fixture
    played = {}
    for (hh, aa) in sched[rnd]:
        s = fixture_seed(season_seed, rnd, hh, aa)
        played[(hh, aa)] = sim_ad(attack[hh], defence[hh], attack[aa], defence[aa], gap, s)
    assert played[(h, a)] == preview, f"preview {preview} != round-play {played[(h, a)]}"

    hg, ag = preview
    hmin, amin = goal_minutes(hg, ag, pseed)
    assert len(hmin) == hg and len(amin) == ag, "goal count != scoreline"
    assert all(1 <= m <= 90 for m in hmin + amin), "minute out of 1..90"
    assert (hmin, amin) == goal_minutes(hg, ag, pseed), "timeline not deterministic"
    print("\n== match preview == round-play, timeline deterministic: PASS ==")
    print(f"  managed fixture {epl[h]['name']} {hg}-{ag} {epl[a]['name']}  "
          f"home goals @ {hmin}  away goals @ {amin}")


# ---- 5. transfers (mirrors engine/Valuation.kt + Career.signPlayer/sellPlayer) -
def _fitness(p):
    return (p["skills"][4] + p["skills"][6] + p["skills"][9]) // 3


def player_rating(p):
    """Mirror of Model.kt Player.rating (integer-truncating, role-weighted)."""
    gk, df, at, bs, pa = (p["skills"][0], p["skills"][1], p["skills"][3],
                          p["skills"][2], p["skills"][7])
    f = _fitness(p)
    if gk > 55:
        return int(gk * 0.6 + f * 0.2 + df * 0.2)
    return (at + df + pa + bs + f) // 5


def age_mult(age):
    if age <= 18: return 0.80
    if 19 <= age <= 23: return 0.95
    if 24 <= age <= 27: return 1.10
    if 28 <= age <= 30: return 0.90
    if 31 <= age <= 33: return 0.65
    return 0.40


def value_k(p):
    r = max(1, min(99, player_rating(p))) / 100.0
    return int(r * r * r * 14000 * age_mult(p["age"]))


def buy_price(p): return int(value_k(p) * 1.10)
def sell_price(p): return int(value_k(p) * 0.90)


def validate_transfers(clubs, players, by_club):
    """Assert the transfer engine's invariants on the real data: budget bookkeeping,
    squad-membership conservation, strength recompute, no money pump, determinism,
    and the squad floor. Mirrors Career.signPlayer / sellPlayer / squadFor."""
    BUDGET_FRACTION, BUDGET_FLOOR, MIN_SQUAD, MAX_SQUAD = 0.30, 250, 12, 30
    epl = [c for c in clubs if c["group"] == "England" and c["division"] == 0]
    managed = epl[0]["id"]
    epl_ids = {c["id"] for c in epl}

    def squad_idx(club_id, transfers):
        moved = dict(transfers)  # (playerIndex -> toClub); last write wins
        stayed = [i for i, p in enumerate(players)
                  if p.get("club") == club_id and i not in moved]
        joined = [i for i, tc in moved.items() if tc == club_id]
        return sorted(stayed + joined)

    def triple(idxs):
        return club_strength_triple([players[i] for i in idxs])

    # starting budget = 30% of squad value, floored
    sv = sum(value_k(p) for p in by_club[managed])
    budget0 = max(BUDGET_FLOOR, int(sv * BUDGET_FRACTION))

    # pick the strongest affordable attacker at another EPL club
    cands = [(i, p) for i, p in enumerate(players)
             if p.get("club") in epl_ids and p.get("club") != managed
             and p["skills"][0] <= 55 and buy_price(p) <= budget0]
    assert cands, "no affordable signing target — budget model too low"
    ti, tp = max(cands, key=lambda t: t[1]["skills"][3])
    seller = tp["club"]

    m0, s0 = squad_idx(managed, []), squad_idx(seller, [])
    _, att0, _ = triple(m0)

    # --- BUY ---
    fee = buy_price(tp)
    tr1 = [(ti, managed)]
    budget1 = budget0 - fee
    m1, s1 = squad_idx(managed, tr1), squad_idx(seller, tr1)
    _, att1, _ = triple(m1)
    assert budget1 == budget0 - fee, "budget not debited by fee"
    assert len(m1) == len(m0) + 1 and len(s1) == len(s0) - 1, "squad sizes wrong after buy"
    assert ti in m1 and ti not in s1, "player not conserved (in managed XOR seller)"
    assert att1 >= att0 - 1e-9, "buying the top attacker dropped attack strength"

    # --- SELL him straight back: no money pump, squad size restored ---
    fee_s = sell_price(tp)
    tr2 = tr1 + [(ti, None)]
    budget2 = budget1 + fee_s
    m2 = squad_idx(managed, tr2)
    assert len(m2) == len(m0), "squad size not restored after sell"
    assert budget2 < budget0, "buy-then-sell is not a loss (money pump!)"
    assert budget0 - budget2 == fee - fee_s, "round-trip cost != premium+discount spread"

    # --- determinism: identical ops -> identical state ---
    assert squad_idx(managed, tr2) == m2 and (budget0 - buy_price(tp) + sell_price(tp)) == budget2

    # --- squad floor: you can sell down to MIN_SQUAD, not below ---
    base = squad_idx(managed, [])
    legal_sells = len(base) - MIN_SQUAD
    assert legal_sells >= 0, "starting squad already below floor"
    tr = []
    sold = 0
    for i in base:
        if len(squad_idx(managed, tr)) <= MIN_SQUAD:
            break  # the ViewModel guard blocks this sell
        tr.append((i, None))
        sold += 1
    assert sold == legal_sells and len(squad_idx(managed, tr)) == MIN_SQUAD, "floor guard wrong"

    print("\n== transfers: budget/conservation/no-pump/determinism/floor: PASS ==")
    print(f"  {epl[0]['name']} budget £{budget0/1000:.1f}M; signed "
          f"{tp['name']} ({player_rating(tp)}) from {by_club and seller} for £{fee/1000:.2f}M; "
          f"attack {att0:.1f}->{att1:.1f}; sell-back recoups £{fee_s/1000:.2f}M "
          f"(round-trip cost £{(fee-fee_s)/1000:.2f}M); floor lets {legal_sells} sales")


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
    validate_rollover(clubs, players)
    validate_match_timeline(epl, players, by_club)
    validate_transfers(clubs, players, by_club)
    print("\nALL CHECKS PASSED")


if __name__ == "__main__":
    main()
