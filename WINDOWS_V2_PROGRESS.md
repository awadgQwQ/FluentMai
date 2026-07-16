# FluentMai Windows 2.0 progress

Updated: 2026-07-16 (Asia/Shanghai)

This is the single working report for the Windows 2.0 product update. It must not contain Cookies, tokens, raw Wahlap HTML, complete authentication URLs, private player identities, or user database contents.

## Status

- Phase: P0 capture foundation checkpoint; paused at required human CA trust confirmation
- Branch: `feat/windows-product-parity`
- Starting commit: `6fff45d5b7f85b358e2e05a249632010417ec55b`
- Current release decision: development may continue; public Windows release is blocked pending a project-license/Qt binding decision and complete packaged-license verification
- P0 status: backend/mock loop is complete, but the first real import is not complete. The first real attempt stopped before proxy activation because current-user root CA installation required human confirmation.
- System network changes made by this task so far: the real attempt did not reach proxy activation. Final WinINET/WinHTTP fingerprints equal the preflight baseline; no recovery journal or helper remains.
- Third-party uploads made by this task so far: none

## Git baseline

- Local `master` and `origin/master` were both `6fff45d5b7f85b358e2e05a249632010417ec55b` after `git fetch --prune --tags origin`.
- Ahead/behind at start: `0/0`.
- Worktree at start: clean.
- Remote: `origin` -> `https://github.com/Daozhu1007/FluentMai.git`.
- Windows history entered the unified repository through `7c92be5` and `a0cb20b`; PR #1 was merged as `6fff45d`.
- Existing tags at start: `v0.1.0-beta.1`, `v0.2.0-beta`, `v0.2.0-ios-alpha.1`.
- Existing releases at start: three Android/iOS pre-releases; no Windows release.
- Existing Android/iOS tags and releases have not been changed.

## Specification and reference audit

Read in full:

- `D:/Code/Prompts/FluentMai/长任务3/FluentMai_Windows_2.0_Prompt.md` (2,218 UTF-8 lines)
- root `README.md` and `README.zh-CN.md`
- `docs/platforms/windows.md`
- `windows/README.md`, `windows/PROXY_SETUP.md`, and both third-party notice files
- all 10 existing files under `windows/tests/`
- `.github/workflows/windows-build.yml`
- the relevant RhythmAlign app shell, settings, about, diagnostics, updater, PyInstaller, Inno Setup, tests, license, and repository screenshots
- the maimaidx-prober Windows proxy entry point, configuration, CA handling, system-proxy manager, domain interception, CookieJar/WAF handling, progress channel, difficulty/version fetching, retries, and license

The task message referred to additional attached FluentMai/RhythmAlign screenshots, but no separate image attachment was exposed as a readable local file. The audit therefore used the checked-in RhythmAlign screenshots plus fresh, ignored screenshots of the current FluentMai Windows build. This line should be updated if the task attachment later becomes available.

## Windows stack baseline

- Python: 3.10.11
- SQLite runtime: 3.40.1
- Binding: PyQt6 6.10.2 / Qt 6.10.0
- Fluent widget package: PyQt6-Fluent-Widgets 1.11.1
- requests: 2.32.5
- beautifulsoup4: 4.14.2
- pytest: 9.1.0
- PyInstaller: 6.18.0
- Inno Setup compiler: not installed or not on `PATH`
- Go toolchain: not installed or not on `PATH`
- mitmproxy command: installed locally, but it is not an approved long-term helper architecture
- Packaging: PyInstaller one-directory portable ZIP only; no installer implementation yet
- Updater: none in FluentMai at baseline
- Theme: hard-coded dark shell mixed with page-local styles
- Localization: JSON-backed Chinese/English strings, but coverage is incomplete and configuration is stored next to the application source/bundle

## Current product capability audit

### Present

- Manual Wahlap auth URL/Cookie/Reqable input, five-page fetch, parse, validation, deduplication, quarantine, and SQLite transaction
- Diving Fish and LXNS imports into the local score table
- Local song/chart catalog, score table, quarantine and import-batch tables
- Local score browser and a virtualized `QAbstractListModel`/delegate chart browser
- Catalog refresh from Diving Fish/LXNS
- Runtime/user-cache jacket resolution
- Legacy third-party B35/B15 dashboard cache
- Basic settings and about pages
- Source/package smoke-test mechanism and portable content audit

### Missing or materially incomplete

- One-click WeChat capture, safe helper IPC, FluentMai-owned CA, exact crash recovery, and real automatic local import
- Local B35/B15/Rating derived from `score_records`; the dashboard still uses a third-party profile cache
- Player statistics, plates, Rating Trend, recommendations, score-loss chart selection, and Kaleid×Scope boundary UI
- Unified information architecture (`首页 / 导入 / 谱面 / 工具 / 设置 / 关于`)
- Complete settings/privacy/diagnostics/update experience
- Stable AppData data-directory policy, migration versioning, installer, update channels, and Windows-only release filtering

### Unsafe legacy paths to retire

- `windows/interceptor.py` writes captured HTML to disk.
- `windows/sync_core.py` treats third-party upload as the primary result.
- `windows/PROXY_SETUP.md` documents machine-wide/manual setup and HTML dumps.
- The current `CaptureHelperController` searches for and launches the official binary, has a no-op default proxy backend, and has no authenticated IPC or crash journal.

## Data baseline and backup

No `FLUENTMAI_DB_PATH` override was active and the unified checkout contained no database at start. Two legacy databases were found in the read-only former Windows tree.

The primary legacy source database:

- Integrity: `ok`
- File SHA-256: `c77c50360d3b44c438f6900c1fd3643062938e001bb52a9a451e1ae9ec94582f`
- Songs: 1,293
- Charts: 5,372
- Legacy `music_data`: 1,326
- Local `score_records`: 0
- Quarantine: 0
- Import batches: 0
- Provider cache rows: 1
- Legacy B35 cache: 35 entries, aggregate 10,430
- Legacy B15 cache: 15 entries, aggregate 4,225
- Legacy B50 semantic SHA-256: `55501a05b1002cb5ead885ac30a0e0866650101c142f7b1fea644c41c4154932`

The packaged legacy database contained 50 cached B50 entries but no catalog or local score records.

Ignored, local-only backup copies were created under:

```text
build/windows-v2-backup/baseline-6fff45d/
```

The originals remain untouched. No database is tracked or uploaded.

Data conclusions:

- The baseline cannot satisfy P0: local `score_records` is empty.
- The current default path (`database.py` beside source/executable) is unsuitable for Installer/Portable parity and can create a second empty database after moving the app.
- Windows 2.0 needs an AppData-based path and an atomic, non-destructive legacy discovery/copy/migration flow.
- `ensure_schema()` has no schema-version table or explicit migration transaction/rollback protocol at baseline.

## Performance baseline

Measured with the ignored copy of the 1,293-song / 5,372-chart legacy database and jacket network disabled:

- Existing test suite: 38 tests in 6.06 s
- Source smoke process: 4,716.5 ms end-to-end (includes the fixed 3 s smoke dwell)
- Python/UI module import: 736.2 ms
- Main window construction: 171.3 ms
- First event processing: 833.1 ms
- Descendant QWidget count after construction: 1,149
- Navigation event processing: home 14.2 ms; dashboard 36.0 ms; scores 8.6 ms; library 7.7 ms; settings 7.9 ms; about 8.1 ms
- Load/filter all charts: 294.6 ms (5,372 matched, 500 displayed)
- Level `13+`: 107.0 ms (355 results)
- Constant `13.3`: 74.2 ms (101 results)

Baseline query code reloads and maps the full chart set for each filter. It is below the 300 ms target for these local probes but leaves little UI-thread budget and lacks aliases/Chinese normalization required by the acceptance queries.

## Window and visual baseline

Current physical desktop baseline:

- Primary display: 1,600 x 1,000
- Available work area: 1,600 x 952
- System DPI: 96 (100%)
- Single active physical display plus a WSL virtual adapter/display environment

Although `MainWindow` calls `resize(1180, 760)`, the real Qt window was forced to `1180 x 1175` by child size constraints. It exceeds the available work area by 223 px, reproducing the invisible-bottom defect. This proves that increasing or merely changing the default height is not a sufficient fix.

Ignored baseline screenshots are under:

```text
build/windows-v2-backup/baseline-6fff45d/screenshots/
```

Visual findings:

- Home and settings content render as a light surface inside a dark shell.
- Selected navigation items can become blank white pills with invisible text.
- Long import/settings layouts drive the top-level minimum height instead of scrolling.
- Import credentials are all flattened into the primary page.
- Dashboard and chart cards contain reusable FluentMai-specific visual work, but the old navigation duplicates concepts.
- The current about page shows stale version/author/contact content and lacks the required third-party details and Windows disclaimer.
- RhythmAlign provides useful patterns for theme-following settings, independent About navigation, centralized version metadata, diagnostic copying, background update checks, SHA-256 verification, PyInstaller spec, and Inno Setup structure. Its media-specific content and exact branding/layout are not to be copied.

## Proxy, WeChat, CA, and recovery baseline

Read-only system state at task start:

- WeChat/Weixin is running and logged in according to the user; the process tree is present.
- FluentMai and proxy helper were not running.
- User WinINET proxy was disabled but retained a prior proxy-server value and bypass list.
- No PAC URL was active.
- WinHTTP was direct.
- Target ports 8033/8080 and common local proxy ports checked were free at the snapshot time.
- A Clash service exists, although the WinINET proxy switch was off.
- Existing mitmproxy and maimaidx-prober root certificates are present; they must not be deleted by FluentMai.

Only hashes/presence flags are recorded in this report. Exact values will be captured immediately before each test into an ignored, current-user-protected recovery journal and restored byte-for-byte/field-for-field.

Official reference:

- Source commit: `7a8bf1302fa569ebc036c908f8c6662c007b8b3f`
- Source license: MIT
- Binary SHA-256: `0bdaf93fda06b866fe1be517be5a351ec52c0e7ac840a0bd861ea31df29685db`
- Default proxy: port 8033; source initially permits an empty bind host and then normalizes display to loopback
- TLS MITM is restricted to maimai/chunithm Wahlap hosts
- CookieJar preserves request and response cookies, including EdgeOne/TencentEdge challenge state
- Browser fingerprint headers are forwarded for subsequent difficulty requests
- It supports difficulty and optional version slicing plus an in-page authenticated progress path
- Windows CA installation targets `LocalMachine\\Root` and may trigger UAC
- It writes Import Token to JSON and defaults to uploading to Diving Fish
- Its fetch loops retry without a finite bound
- Its registry rollback does not cover all required fields/WinHTTP/crash-after-process-death cases

Therefore the official EXE is a behavior baseline only. FluentMai must ship its own MIT-attributed helper or equivalent, bind loopback only, use a random session secret and finite retries, keep raw pages in memory, return them to FluentMai over authenticated local IPC, default to local import, and implement independent exact recovery.

## License and distribution baseline

This section is an engineering audit, not legal advice.

- The unified repository has no project-level `LICENSE`.
- PyQt6 is dual-licensed under GPLv3 or a Riverbank commercial license and is not available under LGPL: <https://www.riverbankcomputing.com/software/pyqt/intro/>
- Riverbank states that a non-GPL-compatible distribution requires a commercial PyQt license: <https://riverbankcomputing.com/commercial/license-faq>
- PyQt6-Fluent-Widgets declares GPLv3 for the open-source package and a commercial path for commercial use: <https://github.com/zhiyiYo/PyQt-Fluent-Widgets>
- Qt itself is available under commercial or open-source licenses; an LGPL route has dynamic-linking, notice, relinking, and corresponding-source obligations: <https://www.qt.io/development/open-source-lgpl-obligations>
- PyInstaller uses GPL-2.0-or-later with an exception allowing distribution of bundled applications: <https://pyinstaller.org/en/stable/license.html>
- maimaidx-prober is MIT licensed and requires preservation of its copyright/license notice: <https://github.com/Diving-Fish/maimaidx-prober/blob/main/LICENSE>
- requests is Apache-2.0; Beautiful Soup is MIT according to their upstream package/project metadata.
- RhythmAlign is the same author's read-only design/code reference under PolyForm Noncommercial 1.0.0; reuse is limited to the rights and author authorization stated by this task, with its notice retained where code is actually reused.
- Live alias endpoints do not document a separate dataset redistribution license; the existing runtime-cache-only policy remains appropriate.
- Game jacket redistribution rights are not established. The 74 untracked reference jackets are not to be copied; runtime/user cache remains the release-safe default.

Current conclusion: engineering work and private development artifacts may continue. A public Windows release must not be created until the user explicitly chooses and documents a valid project/PyQt/Fluent-Widgets licensing path and the final artifact contains the required license texts, source offer/instructions where applicable, and a complete dependency inventory.

## Existing CI baseline

The Windows workflow currently installs dependencies, runs 38 tests, compiles Python, runs source smoke, builds a PyInstaller portable directory, runs package smoke, checks a small forbidden-file pattern, hashes the ZIP, and uploads a 14-day development artifact.

Gaps against Windows 2.0:

- feature branch push trigger is absent
- `test-fixtures/**` and Windows-related root configuration paths are absent
- no explicit integration/migration/golden/search/plate/rating groups beyond existing unit tests
- no helper build, helper mock/IPC tests, restore-script tests, installer build, or installer smoke
- no SBOM/license inventory, broad credential scan, or release asset naming
- no Windows-only updater/channel tests

## Test ledger

| Command | Exit | Result |
| --- | ---: | --- |
| `python -m pytest -q windows/tests` | 0 | 38 passed, 0 failed, 0 errors, 0 skipped; 6.06 s |
| `python -m compileall -q -f windows scripts/windows` | 0 | completed |
| `.\\scripts\\windows\\smoke_test.ps1 -Mode source` | 0 | source window constructed/shown and exited; 4,716.5 ms measured wrapper time |
| `python -m pytest -q windows/tests` | 0 | 63 passed; capture/helper/recovery/rating additions included |
| `python -m compileall -q windows` | 0 | completed after P0 foundation changes |
| `.\\scripts\\windows\\build_portable.ps1` | 0 | main app and `FluentMaiCaptureProxy.exe` built; forbidden artifact count 0 |
| `.\\scripts\\windows\\smoke_test.ps1 -Mode package` | 0 | packaged main window constructed/shown and exited |
| packaged helper IPC smoke | 0 | helper reached ready state without CA installation, timed out finitely, and restored the simulated proxy exactly |

## Checkpoints

- [x] `chore: establish Windows v2 baseline`
- [ ] `feat: integrate local Wahlap capture pipeline`
- [ ] `fix: make Windows shell responsive and DPI safe`
- [ ] `feat: reach Android feature parity on Windows`
- [ ] `ui: adopt FluentMai Windows design system`
- [ ] `feat: add Windows settings about and updater`
- [ ] `perf: optimize Windows chart browsing`
- [ ] `build: prepare FluentMai Windows alpha`
- [ ] `test: validate FluentMai Windows v2`

## Immediate next work

1. The user must explicitly accept the Windows trust prompt for `FluentMai Local Capture CA`; do not resume proxy activation until that human action is available.
2. Retry the first real WeChat import from the already-validated service-account path, then verify AppData SQLite counts, B35/B15/Rating, restart persistence, and exact network restoration.
3. Repeat the real import and verify zero duplicate scores/Rating-history rows plus exact restoration again.
4. Wire the validated backend into a cancellable UI worker with an explicit CA disclosure/confirmation step.
5. Continue the window/DPI checkpoint only after the real P0 loop passes.

## P0 foundation checkpoint evidence

- AppData migration copied (not moved) the legacy database to `%LOCALAPPDATA%\\FluentMai\\data\\maimai_data.db`; source SHA-256 remained `c77c50360d3b44c438f6900c1fd3643062938e001bb52a9a451e1ae9ec94582f` and the migrated database passed `integrity_check` at schema 2.
- The public LXNS catalog was refreshed to 1,304 songs, 5,414 charts, and 20 explicit major versions; operating version resolved to 25,500 without creating local scores.
- The packaged helper binds loopback, authenticates each IPC session with a random token passed through stdin, only intercepts the exact Wahlap host, keeps page bodies in memory, uses finite retries, and never uploads by default.
- Success, authentication failure, helper error, helper crash, cancellation, stale-journal recovery, and packaged-helper finite-timeout paths are covered without changing the real system proxy.
- Android Rating boundary values and B35/B15 future-version fail-closed semantics are covered by golden tests. Repeated imports do not duplicate automatic Rating history.
- Real WeChat UI navigation reached the official `舞萌 | 中二` service-account `我的记录 -> 舞萌DX` entry using window-relative coordinates after accessibility discovery showed the Qt canvas exposes no actionable UIA controls.
- The first real capture failed during CA preparation with `ca_installation_timeout`; the helper never announced ready, proxy activation never occurred, local score/import-batch counts stayed zero, the CA remained uninstalled, and the database stayed healthy.
- A residual `certutil` process from the confirmation wait was terminated after verifying its exact system image path. Final helper count is zero, the recovery journal is absent, and final WinINET/WinHTTP fingerprints match preflight.
