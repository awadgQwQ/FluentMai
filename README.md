# FluentMai

[English](README.md) | [简体中文](README.zh-CN.md)

An Android companion for importing, managing, browsing, and syncing maimai DX score data.

FluentMai is currently a public beta preview. It is an independent, unofficial application, and upstream data source or third-party API behavior may change without notice.

- [Download Beta](https://github.com/Daozhu1007/FluentMai-Android/releases)
- [Privacy Model](docs/PRIVACY_MODEL.md)
- [Product Scope](docs/PRODUCT_SCOPE.md)

## Overview

FluentMai is a native Android app for players who want a local-first way to bring maimai DX score data onto their own device, inspect it, and optionally sync it to supported community score services.

The project exists to make the import, validation, local storage, score browsing, and upload path easier to test and reason about. Its product direction is deliberately local-first: Room is the source of truth for imported score data, while external sync remains an optional action controlled by the user.

## Key Features

- Wahlap auth import through a local Hook flow.
- Manual Cookie / Reqable import fallback.
- Local Room score storage.
- SD/DX chart identity and deduplication.
- Rating and B50 calculation.
- Score and chart browsing with search and filtering.
- Diving Fish and LXNS upload.
- Guarded Diving Fish rebuild with explicit confirmation.
- Validation, quarantine, and privacy redaction.

## Download & Installation

FluentMai currently targets Android 14 and supports Android 8.0 or later (`minSdk 26`).

1. Open [GitHub Releases](https://github.com/Daozhu1007/FluentMai-Android/releases).
2. Download the latest beta APK.
3. Install it on a supported Android device.
4. Android may ask permission to install an APK from the browser or file manager.

## Quick Start

1. Open FluentMai.
2. Import Wahlap data through Hook or the manual fallback.
3. Review the import result, including rejected or quarantined records.
4. Browse Rating, B50, scores, and charts.
5. Optionally sync local scores to Diving Fish or LXNS.

## Engineering Highlights

- Modular Android/Kotlin architecture.
- Local-first Room persistence.
- Deterministic chart identity including SD/DX.
- Validation and quarantine pipeline.
- Rating/B50 regression verification.
- Ordinary upload and destructive rebuild isolation.
- Exact-HEAD reproducible build verification for beta assets.

## Project Structure

- `app` - Android app entry point, navigation, platform integrations, and network transport.
- `core/model` - Shared score, chart, import, and quarantine models.
- `core/importer` - Wahlap parsing, catalog handling, validation, and import pipeline logic.
- `core/database` - Room schema, DAO access, and repository persistence.
- `core/privacy` - Redaction helpers for logs and user-visible diagnostics.
- `core/exporter` - Score payload exporters for supported upload formats.
- `core/upload` - Diving Fish and LXNS upload, verification, and rebuild logic.
- `feature/home` - Home dashboard UI.
- `feature/import` - Import workflow UI.
- `feature/scores` - Rating, B50, score list, and chart browser UI.
- `feature/settings` - Upload and settings UI.
- `feature/quarantine` - Quarantine review UI.

## Build from Source

Requirements:

- Android SDK.
- JDK 17 compatible with Android Gradle Plugin 8.7.3.
- `local.properties` with `sdk.dir`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

Windows:

```powershell
.\gradlew.bat test assembleDebug
```

Unix/macOS:

```sh
./gradlew test assembleDebug
```

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, Room, Kotlin coroutines, and Ktor client dependencies.

## Privacy & Security

See the [Privacy Model](docs/PRIVACY_MODEL.md) for the current project rules.

The local Room database is the app's source of truth for imported score data. Secrets and raw auth information must not be committed, and the app is designed so Wahlap auth URLs and upload tokens are held in current UI/app state rather than written to Room. Logs and user-visible diagnostic text use redaction for credential fields, authentication URLs, raw HTML, input values, and token-like response text.

Bundled fixtures are synthetic or public test data. External sync to Diving Fish or LXNS is optional.

## Beta Limitations

- FluentMai is beta software and may contain bugs.
- Upstream page or API changes may break import or sync flows.
- Users should verify important score data before running a destructive Diving Fish rebuild.
- Diving Fish rebuild requires explicit confirmation.

## Disclaimer

FluentMai is an independent, unofficial project.

It is not affiliated with or endorsed by SEGA, Wahlap, Diving Fish, or LXNS.
