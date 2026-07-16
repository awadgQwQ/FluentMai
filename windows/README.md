# FluentMai for Windows

FluentMai for Windows is the independent desktop implementation in the FluentMai product repository. It is Alpha / Development software and does not promise feature or data-format parity with Android or iOS.

## Technology stack

- Python 3.10 or later
- PyQt6 and PyQt6-Fluent-Widgets
- SQLite for local catalog, score, quarantine, import-batch, and provider-cache data
- requests and Beautiful Soup for supported import/catalog flows
- pytest for tests
- PyInstaller for the portable one-directory build

## Setup and run

From the repository root:

```powershell
python -m pip install -r windows/requirements.txt
Set-Location windows
python main.py
```

Runtime data is local. Set `FLUENTMAI_DB_PATH` to choose an explicit database path for tests or experiments. Do not use a real user database in CI.

## Tests and static compilation

From the repository root:

```powershell
python -m pytest -q windows/tests
python -m compileall -q -f windows scripts/windows
```

The test suite uses synthetic payloads and temporary databases. It must not use real Cookies, tokens, auth URLs, raw HTML, or player data.

## Startup smoke test

The source and packaged application support a CI-only `--smoke-test` argument. It constructs and shows the main window, including the core pages, and exits automatically.

```powershell
.\scripts\windows\smoke_test.ps1 -Mode source
```

The helper forces Qt's offscreen backend and a temporary SQLite path so smoke tests do not touch user data.

## Packaging

Build the portable directory and ZIP from the repository root:

```powershell
.\scripts\windows\build_portable.ps1
```

Outputs are written under `build/windows/` and are ignored by Git. Validate the packaged executable with:

```powershell
.\scripts\windows\smoke_test.ps1 -Mode package
```

The build includes application code, `assets/`, `locales/`, the default non-secret `config.json`, and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). It must not contain databases, raw HTML, logs, credentials, caches, source-only agent instructions, or unlicensed jacket images.

## Current features

- Manual Wahlap import from an auth URL, Cookie, or Reqable request headers
- Diving-Fish full-record import by Import Token
- LXNS full-record import by personal API token
- Local score database, validation, deduplication, quarantine, and import-batch summaries
- Diving-Fish and LXNS song/chart catalog caches
- Local score browsing and song/chart search
- Cover resolution through user cache, runtime providers, and optional local assets
- Redaction of Token, Cookie, Authorization, and auth-URL material in application messages

## Known limitations

- Automatic Windows proxy capture is represented by a safe controller interface; no embedded certificate or registry-changing flow is enabled by default.
- The manual Wahlap flow still needs broader real-account validation.
- The upload business layer exists, but the upload UI is not fully wired.
- The legacy dashboard still depends on public third-party player data; local scores are the source of truth for the newer local pages.
- No game jackets are distributed in the repository or portable package; missing covers are expected until resolved from user cache or a runtime provider.
- The product repository currently has no declared project-level `LICENSE`. PyQt6 and PyQt6-Fluent-Widgets also require an explicit GPL/commercial-license compliance decision. These issues block a public Windows Release.

## Differences from Android and iOS

- Windows uses PyQt6 rather than Compose or SwiftUI.
- Windows uses its own SQLite schema and release cycle.
- It does not consume Android Room migrations and does not currently share the KMP domain module.
- Import, upload, analysis, and UI coverage differ across platforms; repository co-location does not imply feature parity.

## Privacy

Never commit or package real Cookies, Authorization headers, Import Tokens, LXNS tokens, full auth URLs, raw Wahlap HTML, user databases, logs, or score exports. The settings UI does not persist tokens, and Credential Manager/keyring integration remains pending.
