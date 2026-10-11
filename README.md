# USM2: Ultimate Soccer Manager 2, rebuilt for Android

A native Android (Kotlin / Jetpack Compose) reconstruction of the 1997 DOS
football-management classic **Ultimate Soccer Manager 2**, re-laid for touch.
It reuses the original game's real database (clubs, managers, players,
stadiums) and screen artwork, decoded from the original files.

![The match view: both sides in their real formation on the original pitch art, with the live scoreboard and goal ticker.](docs/screen-match.png)

*The match view, drawn from the original game's pitch art and player sprites.*

## Download

[![Build APK](https://github.com/Matswm86/USM2/actions/workflows/build-android.yml/badge.svg)](https://github.com/Matswm86/USM2/actions/workflows/build-android.yml)

**[⬇ Download the latest APK](https://github.com/Matswm86/USM2/releases/download/latest/usm2.apk)**

Sideload it on Android 8.0+ (enable "install unknown apps"). It is debug-signed,
so if you are updating over an older build with a different signature, uninstall
the old copy first.

## What works today

It is a playable manager, not just a database browser:

- **Take charge of any English, French or German club** and play a full
  season: a double round-robin fixture list, simulated results for the whole
  division each matchday, and a live league table with promotion / relegation
  zones.
- **Watchable matches** on the original pitch art: both teams placed in their
  real formation, the ball and players flowing with play, a ticking clock,
  scoreboard and goal ticker, and diving goalkeepers. Pitches are dry, muddy,
  wet or frozen (deterministic per fixture, cosmetic only). The animated
  scoreline always matches the result the engine records.
- **Crowd audio** from the original game's own sounds: welcome, kick-off
  whistle, a roar when you score, a groan when you concede, full-time whistle.
- **Transfers & a living budget**: buy and sell from the office phone against a
  real balance that moves with wages, gate receipts and prize money.
- **Pick your starting XI** (a weaker eleven fields a weaker side), and make
  **substitutions during the match** (three per game; the clock pauses).
- **League browser**: England (Premier League down to the Conference), the
  European club pool (browse-only), France and Germany. 412 clubs, 7,800+
  players, with per-player attributes decoded from the original game.
- **Season rollover**: promotion, relegation and a fresh fixture list across a
  five-tier English pyramid.

## How it works

The app is written in Kotlin with Jetpack Compose and is self-contained: it
asks for no permissions and needs no network. The original game's database and
screen art were decoded by Python tools into a slim JSON dataset and PNG images
that ship inside the APK. A pure-Kotlin engine builds each season's double
round-robin fixture list and settles every result from the clubs' attack and
defence strengths with a seeded Poisson goal model, so a saved career replays
identically. The match view then animates toward the result the engine has
already recorded. Your career is saved to a file in the app's private storage
after each move, so it survives restarts.

## Status

Version 0.1.0 and playable end to end; not done yet: AI clubs trading players on
their own, player ageing, injuries and suspensions, and wider playtest balancing.

## Roadmap

1. **Decode the original assets**: database and all screen artwork. ✅
2. **UI shell**: office, league/squad browser, player detail, transfers. ✅
3. **Match & management engine**: fixtures, tables, transfers, finances,
   manual line-up, season rollover, and an animated match view with weather,
   crowd audio and diving keepers. ✅
4. **More depth (optional):** AI clubs making their own transfers and wider
   playtest balancing.

## Building

APKs are built in CI (GitHub Actions), not locally. Push to `main` or run the
**Build Android APK** workflow; it produces `usm2.apk` as a workflow artifact
and updates the rolling `latest` release.

The Android project lives in [`android/`](android/) (`compileSdk 35`,
`minSdk 26`). The slim derived dataset and screen art it ships are committed
under `android/app/src/main/assets/`, and the match sounds under
`android/app/src/main/res/raw/`. The Python decoders that produced them are in
[`tools/`](tools/); they read the original game files from a gitignored
`original/` folder that you supply. The reverse-engineering notes are in
[`docs/FORMATS.md`](docs/FORMATS.md).

## Credits & legal

*Ultimate Soccer Manager 2* was created by Impressions Games and published by
Sierra in 1997. The game, its database and its artwork remain the property of
their respective rights holders. This project is a non-commercial fan
reconstruction for preservation and personal use, and is not affiliated with or
endorsed by the original creators. The original game program files are not
redistributed here.
