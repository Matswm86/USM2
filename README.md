# USM2 — Ultimate Soccer Manager 2, rebuilt for Android

A native Android (Kotlin / Jetpack Compose) reconstruction of the 1997 DOS
football-management classic **Ultimate Soccer Manager 2**, re-laid for touch.
It reuses the original game's real database (clubs, managers, players,
stadiums) and screen artwork, decoded from the original files.

## Download

[![Build APK](https://github.com/Matswm86/USM2/actions/workflows/build-android.yml/badge.svg)](https://github.com/Matswm86/USM2/actions/workflows/build-android.yml)

**[⬇ Download the latest APK](https://github.com/Matswm86/USM2/releases/download/latest/usm2.apk)**

Sideload it on Android 8.0+ (enable "install unknown apps"). It is debug-signed,
so if you are updating over an older build with a different signature, uninstall
the old copy first.

## What works today

- **Office** home screen with the original artwork.
- **League browser**: England (Premier League down to the Conference), the
  European club pool, France and Germany. 412 clubs, 8,600+ players.
- **Squads** per club, sorted by rating, with per-player attributes.
- **Transfer market**: search every player across all leagues.

The match engine and management loop (fixtures, finances, training, board
confidence, match simulation) are not built yet, see the roadmap.

## Roadmap

1. **Decode the original assets** — database and all screen artwork. ✅
2. **UI shell** — office, league/squad browser, player detail, transfers. ✅
3. **Match & management engine** — fixtures, tables, transfers, finances,
   training, board confidence, and a match simulation driven by the decoded
   ratings. 🔜
4. **Balance & playtest.**

## Building

APKs are built in CI (GitHub Actions), not locally. Push to `main` or run the
**Build Android APK** workflow; it produces `usm2.apk` as a workflow artifact
and updates the rolling `latest` release.

The Android project lives in [`android/`](android/) (`compileSdk 35`,
`minSdk 26`). The slim derived dataset and screen art it ships are committed
under `android/app/src/main/assets/`. The Python decoders that produced them
are in [`tools/`](tools/); the reverse-engineering notes are in
[`docs/FORMATS.md`](docs/FORMATS.md).

## Credits & legal

*Ultimate Soccer Manager 2* was created by Impressions Games and published by
Sierra in 1997. The game, its database and its artwork remain the property of
their respective rights holders. This project is a non-commercial fan
reconstruction for preservation and personal use, and is not affiliated with or
endorsed by the original creators. The original game program files are not
redistributed here.
