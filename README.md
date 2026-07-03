# FluentMai Android

FluentMai Android is a local-first maimai DX score import and upload tool built as a native Android Kotlin app.

The current working goal is intentionally narrow:

- Import maimai DX score data from the Wahlap WeChat account flow.
- Store imported scores locally in Room.
- Export local scores into upload-ready formats.
- Upload imported scores to Diving Fish and LXNS.

Other product ideas stay out of scope until local import, score export, and Diving Fish/LXNS upload are reliable.

## Current Capabilities

- Jetpack Compose Android app.
- Room local score database.
- Fixture import path for repeatable tests.
- Real Wahlap auth URL import path.
- Score validation, deduplication, and quarantine routing.
- Privacy redaction for auth URLs, tokens, cookies, input values, and raw HTML.
- Exporters for Diving Fish update records JSON and LXNS user score JSON.
- Upload clients for Diving Fish and LXNS.
- LXNS song catalog lookup for `songId` resolution.

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

## Reference Boundary

`D:\Code\MaiproberPlus` is a read-only technical reference for the local Wahlap import route and prober API behavior. FluentMai Android is a clean Kotlin implementation, not a direct fork of MaiproberPlus.
