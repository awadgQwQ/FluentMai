# FluentMai Android

FluentMai Android is the active MVP direction for FluentMai. It is a local-first maimai DX score manager built as a native Android Kotlin app.

The old desktop FluentMai PyQt prototype is legacy and should not be extended as the MVP. MaiproberPlus / MPP-Lab is only a technical validation reference for the Android local import route, not the product base for FluentMai Android.

Phase 0 creates the product foundation:

- Native Android Kotlin project
- Jetpack Compose UI
- Room local database
- Domain modules for model, database, importer, and privacy
- Feature modules for home, import, scores, quarantine, and settings
- Fake local fixture import pipeline
- Parser, validator, deduplicator, quarantine routing, and privacy redaction tests

Phase 0 intentionally does not implement:

- Real Hook, VPN, WebView login, or Wahlap networking
- WaterFish upload
- LXNS upload
- Cloud sync
- Account login
- AI recommendations
- Community features
- Multi-game support

The fake import reads JSON fixtures from `fixtures/` to validate the architecture. Valid records are written to Room, duplicate records are skipped, and invalid records are routed to quarantine without contaminating the score table.

## Build

```powershell
.\gradlew.bat test assembleDebug
```

## Fixtures

- `fixtures/valid_sample_import.json`
- `fixtures/duplicate_import_case.json`
- `fixtures/blank_title_quarantine_case.json`
- `fixtures/invalid_achievement_case.json`
- `fixtures/invalid_level_index_case.json`

## Repository Boundary

`D:\Code\MaiproberPlus` must remain read-only for this task. If any code is copied from MaiproberPlus in a later phase, Apache-2.0 attribution, license, NOTICE requirements, and modification notes must be preserved.

