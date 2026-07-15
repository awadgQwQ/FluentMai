# FluentMai

[English](README.md) | [简体中文](README.zh-CN.md)

FluentMai is an unofficial Android, local-first score companion for players of the mainland China version of maimai DX. It is currently a public Beta, supports Android 8.0 and later, and is not affiliated with or endorsed by SEGA, Wahlap, Diving Fish, or LXNS.

- [Download FluentMai v0.2.0 Beta](https://github.com/Daozhu1007/FluentMai-Android/releases/tag/v0.2.0-beta)
- [Privacy model](docs/PRIVACY_MODEL.md)
- [Product scope](docs/PRODUCT_SCOPE.md)
- [Changelog](CHANGELOG.md)

## About the project

FluentMai brings the score-import and analysis workflow onto the player's own Android device. Valid imported scores are stored in a local Room database and are used for browsing, B35/B15, Rating, statistics, plate progress, trends, and recommendations. Uploading to a supported community service is optional and always initiated by the user.

Android is the maintained and released platform. The repository also contains experimental iOS and Kotlin Multiplatform work, but there is no installable iOS release for general users.

## Key features

- Import from Wahlap through the local Hook flow, with a manual fallback when needed.
- Validate imported records, deduplicate stable SD/DX chart identities, keep valid scores in Room, and isolate suspicious records in quarantine.
- Calculate DX Rating and the correct old-version B35/current-version B15, without treating future content batches as the current major version.
- Browse the complete catalog through one unified chart browser and open the same chart detail view from B50, search, plate, and recommendation flows.
- Search by title, community alias, song ID, BPM, artist, or chart designer, with offline Simplified/Traditional Chinese normalization.
- Combine difficulty, major version, category, chart-constant range, play status, achievement range/rank, FC, FS, and SD/DX filters.
- Review player score statistics and data-driven plate progress, including explicit “insufficient data” states.
- Use a single-chart Rating calculator, a version-name reference, and an achievement/loss calculator that reads note counts from a selected chart while retaining manual input.
- Track real Rating history and generate explainable improvement suggestions by simulating B35/B15 changes; recommendations are deterministic calculations, not skill predictions.
- Optionally upload local scores to Diving Fish or LXNS.
- Use responsive phone-landscape and tablet layouts with a shared Android feature set.
- Keep new upload tokens and raw Wahlap import pages in the current session only; they are not newly persisted.

## Screenshots

| Home B50 | Unified chart browser | Chart detail |
| --- | --- | --- |
| <img src="docs/screenshots/home-b50.png" width="260" alt="FluentMai home B50"> | <img src="docs/screenshots/chart-browser.png" width="260" alt="Unified chart browser"> | <img src="docs/screenshots/chart-detail.png" width="260" alt="Unified chart detail"> |

| Plate progress | Note-loss calculator |
| --- | --- |
| <img src="docs/screenshots/plate-progress.png" width="260" alt="Data-driven plate progress"> | <img src="docs/screenshots/note-calculator.png" width="260" alt="Chart note-loss calculator"> |

Screenshots show local test data. Authentication URLs, tokens, cookies, and raw imported pages are deliberately excluded.

## Download and installation

FluentMai supports Android 8.0 or later (`minSdk 26`) and currently targets Android 14 (`targetSdk 34`).

1. Open the [GitHub Release for v0.2.0 Beta](https://github.com/Daozhu1007/FluentMai-Android/releases/tag/v0.2.0-beta).
2. Download `FluentMai-v0.2.0-beta-android.apk`.
3. Install it over the existing app. Do not uninstall or clear app data when upgrading.
4. If prompted, allow APK installation for the browser or file manager you used.

The v0.2.0 Beta asset is a debug-signed test build because this repository does not contain a trusted release keystore or automated Android release-signing workflow. It is intended for Beta testing, not as a stable production release.

## Quick start

1. Open FluentMai and use the Import tab.
2. Try the Wahlap Hook flow first; use the manual fallback if the upstream flow requires it.
3. Review the import summary and any quarantined records.
4. Check B35/B15 on Home, or use Charts for search, filters, statistics, plate progress, and unified details.
5. Open Tools for Rating, note-loss/achievement, version reference, Rating Trend, and explainable improvement suggestions.
6. Enter a Diving Fish or LXNS token only when you choose to upload. Tokens are session-only and must not be shared in issues or logs.

## Data and privacy

- The local Room database is the source of truth for imported Android score data.
- New upload tokens, full authentication URLs, and raw Wahlap pages are not written to Room or ordinary app storage. Tokens and raw pages exist only for the active session/request flow.
- Import validation, conservative matching, deduplication, and quarantine prevent suspicious records from silently replacing valid results.
- Diagnostic text is redacted before display or logging. External upload responses are treated as untrusted text.
- Diving Fish and LXNS uploads are optional and user-triggered.
- Upgrading does not proactively delete token/page caches that may already exist from an older FluentMai build. The current version stops creating new persistent copies without silently destroying prior app data.

See [Privacy Model](docs/PRIVACY_MODEL.md) and [Import Pipeline](docs/IMPORT_PIPELINE.md) for the detailed boundaries.

## Current limitations

- Upstream Wahlap pages and third-party APIs can change; import or upload may require a future FluentMai update.
- Kaleid×Scope has a model and unavailable-state UI, but no complete, auditable gate/song data source is connected. FluentMai does not invent this data.
- This is Beta software and may still have UI issues or device-specific compatibility problems. Not every Android device has been verified.
- Community aliases depend on validated runtime sources and may be incomplete or temporarily unavailable; ordinary catalog search remains usable.
- Improvement suggestions are mathematical B35/B15 simulations, not predictions of player skill or chart fit.
- Users should retain an external backup of important score data, especially before third-party rebuild or sync operations.

## Android build

Requirements:

- Android SDK with API 34 build support.
- JDK 17 or a compatible JDK capable of targeting Java 17.
- `local.properties` containing `sdk.dir`, or a configured `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat :app:assembleDebug
```

Unix/macOS:

```sh
./gradlew test
./gradlew :app:assembleDebug
```

The Android app uses Kotlin, Jetpack Compose, Room, coroutines, Ktor, and a modular Gradle project. Release signing credentials are intentionally not stored in the repository.

## iOS current status

- The repository contains an experimental iOS MVP and a Kotlin Multiplatform shared domain layer.
- iOS has not reached a formal release.
- There is currently no iOS Release that general users can install directly.
- iOS builds and physical-device verification are still in progress.
- Android is the currently maintained and released platform.

See [IOS_TESTING.md](IOS_TESTING.md) only if you are a developer testing the experimental MVP on a Mac. iOS is intentionally not listed as a primary download.

## Project structure

- `app` — Android entry point, navigation, platform networking, and Hook integration.
- `core/model` — shared score, Rating, version, plate, toolbox, and recommendation domain logic; also the Kotlin Multiplatform domain layer.
- `core/importer` — Wahlap parsing, public catalog handling, validation, and import pipeline.
- `core/database` — Android Room schema, migrations, DAOs, and repositories.
- `core/privacy` — redaction helpers for diagnostics.
- `core/exporter` and `core/upload` — upload payloads and optional Diving Fish/LXNS flows.
- `feature/home`, `feature/import`, `feature/scores`, `feature/quarantine`, `feature/settings`, `feature/tools` — Android Compose features.
- `iosApp` — experimental SwiftUI MVP; not part of the Android Release.
- `fixtures` — bundled public or synthetic fallback/test data.
- `docs` — data contracts, privacy, import, plate, alias, toolbox, and recommendation notes.

## Third-party data and acknowledgements

FluentMai uses public catalog/version metadata and documented APIs from LXNS, optional upload interfaces from Diving Fish and LXNS, community alias data fetched at runtime from LXNS and YuzuChaN, and public SEGA rules/pages as references for scoring and plate semantics. Alias data is validated and cached privately for local search; the repository does not redistribute a copied community alias database.

MaiproberPlus, EasyMai, maimai.py, and other community projects were useful read-only implementation or behavior references where documented. Mentioning a project does not imply partnership, endorsement, or an official relationship. Dependency notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

FluentMai is an independent, unofficial fan-made tool for the mainland China version of maimai DX. It is not affiliated with, authorized by, or endorsed by SEGA, Wahlap, Diving Fish, LXNS, RankHub, EasyMai, or their operators. All game names, artwork, music metadata, and trademarks belong to their respective owners.

Use the app and third-party upload services at your own risk. Upstream changes can break imports or synchronization, and important data should be backed up outside the app.
