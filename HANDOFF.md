# Housekeeper Mini — Agent Handoff

## Project Overview
- **Repo / Folder:** `/Users/Morad/C3PO/Housekeeper-Mini`
- **GitHub:** `https://github.com/aghamorad/Housekeeper-Mini`
- **Targets:** Android phones/tablets AND Meta Quest (Horizon OS / Android 8.0+, API 26–35). Zero Google Play Services; adaptive `NavigationBar` (phone) / `NavigationRail` (Quest/tablet).
- **Design Rules:**
  - **75-Year-Old Rule:** Plain language, big touch/pointer targets, zero jargon, 1-tap actions.
  - **"Made by Morad"** in the Home header, NavigationRail, and About/Updates card.
  - **Self-Updater + Obtainium-style GitHub App Updater:** Checks GitHub Releases for `aghamorad/Housekeeper-Mini` and any user-added `owner/repo` APK releases.

## Current State (2026-11-20)
- Built and verified `v0.2.0` (`versionCode = 2`):
  - `./gradlew testDebugUnitTest assembleRelease` passed (`BUILD SUCCESSFUL`).
  - Release APK: `app/build/outputs/apk/release/app-release.apk`
- Features & Updates in `v0.2.0` (aligned with macOS **Housekeeper 0.30.1**):
  1. **Signature Burgundy Theme (`Theme.kt`):** Warm oxblood/burgundy Dark Mode (`#260C10` background, `#331D21` surface, `#EAB19E` accent, `#97393A` primary container) and warm parchment-burgundy Light Mode (`#F9F4F1` background, `#FFFCFA` surface, `#9E2F27` primary).
  2. **Liquid Glass Octopus Icon & In-App Mascot:** Generated multi-density `ic_launcher.png` / `ic_launcher_round.png` (`mdpi`–`xxxhdpi`), `drawable-nodpi/mascot_octopus.png`, and pearlescent Liquid Glass adaptive icon (`ic_launcher_bg.xml` + `ic_launcher_fg.xml`) directly from macOS Housekeeper's canonical Liquid Glass icon and `mascot.png`; added interactive octopus mascot in `OverviewScreen` and `NavigationRail`.
  3. **Zero-Idle-Drain UI Magic:** Event-driven `AnimatedContent` tab transitions, spring-animated RAM & Storage progress bars, tap-bounce octopus mascot, and an event-driven `SpeedArcGauge` in `NetworkScreen` (zero background timers).
  4. **Built-In Live Speed Test (`NetworkAndUpdateEngine.kt`, `Screens.kt`):** Live Cloudflare Edge streaming Download (Mbps), Upload (Mbps), 5-sample median Ping & Jitter (ms), sweeping burgundy arc gauge, and plain-English connection quality verdict.
  5. **Home, Storage, Apps, Privacy & Self-Updater:** All core phone & Meta Quest maintenance screens and Obtainium-style GitHub APK updater preserved.
