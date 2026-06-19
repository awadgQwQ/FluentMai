# Phase 0 Acceptance

Phase 0 is accepted when:

- The Android project builds successfully.
- Unit tests pass.
- Fake import inserts valid score records.
- Re-running the same fake import does not duplicate score rows.
- Invalid records are routed to quarantine.
- Blank-title records never enter the score table.
- Invalid achievement records never enter the score table.
- Invalid levelIndex records never enter the score table.
- Privacy redaction tests pass.
- Documentation states that MPP-Lab is a technical validation reference, not the product base.
- `D:\Code\MaiproberPlus` remains unmodified.
- The final FluentMai Android worktree is clean except for intentional committed changes.

## Phase 0 Limitations

Phase 0 does not perform real Hook, VPN, WebView login, Wahlap networking, WaterFish upload, LXNS upload, cloud sync, account login, AI recommendation, community, or multi-game behavior.

