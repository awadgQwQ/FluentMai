# Windows platform

## Status

Windows is an independent Alpha / Development implementation under `windows/`. Its source is part of the product repository, but there is currently no public Windows Release.

- Technology: Python 3.10+, PyQt6, PyQt6-Fluent-Widgets, requests, Beautiful Soup, and SQLite
- UI: independent desktop navigation and pages
- Storage: independent local SQLite database
- Packaging: PyInstaller one-directory portable build

See [windows/README.md](../../windows/README.md) for setup, tests, packaging, smoke tests, features, and limitations.

## Current validation baseline

```powershell
python -m pip install -r windows/requirements.txt
python -m pytest -q windows/tests
python -m compileall -q -f windows scripts/windows
.\scripts\windows\smoke_test.ps1 -Mode source
.\scripts\windows\build_portable.ps1
.\scripts\windows\smoke_test.ps1 -Mode package
```

The Windows workflow runs only for changes under `windows/`, `scripts/windows/`, or its own workflow file. It uploads a development artifact after tests, compilation, source startup, packaging, packaged startup, and content checks succeed.

## Data, artwork, and release limits

- Windows data is not the Android Room database and has no documented cross-platform migration path.
- User databases, raw HTML captures, tokens, Cookies, logs, build directories, and local configuration are excluded from source control.
- Game jacket images are not bundled because their redistribution rights are not established. The app uses user cache or runtime providers.
- A public Windows pre-release remains blocked until the project license and the complete runtime third-party distribution obligations are resolved and the packaged UI is manually reviewed.
