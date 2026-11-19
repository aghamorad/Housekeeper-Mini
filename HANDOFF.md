# Housekeeper Mini — Agent Handoff

## Project Overview
- **Repo / Folder:** `/Users/Morad/C3PO/Housekeeper-Mini`
- **GitHub:** `https://github.com/aghamorad/Housekeeper-Mini`
- **Targets:** Android phones/tablets AND Meta Quest (Horizon OS / Android 8.0+, API 26–35). Zero Google Play Services; adaptive `NavigationBar` (phone) / `NavigationRail` (Quest/tablet).
- **Design Rules:**
  - **75-Year-Old Rule:** Plain language, big touch/pointer targets, zero jargon, 1-tap actions.
  - **"Made by Morad"** in the Home header, NavigationRail, and About/Updates card.
  - **Self-Updater + Obtainium-style GitHub App Updater:** Checks GitHub Releases for `aghamorad/Housekeeper-Mini` and any user-added `owner/repo` APK releases.

## Current State (2026-11-19)
- Built and verified `v0.1.0`:
  - `./gradlew testDebugUnitTest assembleRelease` passed (`BUILD SUCCESSFUL`).
  - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
  - Release APK: `app/build/outputs/apk/release/app-release.apk`
- Features implemented:
  1. **Home (`OverviewScreen`):** Device/Quest detection, RAM, Internal Storage, Battery & Thermal vitals, quick links.
  2. **Storage (`StorageScreen`):** Leftover APKs, Large Files (>25 MB), Old Downloads (30+ days), Duplicate Files (SHA-256), `Select Safe`, and confirmed removal.
  3. **Apps (`AppsScreen`):** Installed user apps with App/Data/Cache size (`StorageStatsManager`), last-used tracking (`UsageStatsManager`), 1-tap App Storage/Cache screen, APK backup to `Downloads/HousekeeperMini-Backups`, and Uninstall.
  4. **Privacy (`PrivacyScreen`):** Audits apps holding Camera, Microphone, Precise Location, Contacts, Install Apps, and Draw Over Apps permissions.
  5. **Network & Updates (`NetworkScreen`):** Wi-Fi/IP/DNS info, route & LAN reachability probe (e.g. PS5 `192.168.1.81:8081` or Mac MLX), self-updater, and Obtainium-style GitHub APK App Updater.
