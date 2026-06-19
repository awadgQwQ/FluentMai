# Privacy Model

Phase 0 treats the local Room database as the source of truth.

The app must not store:

- Cookie values
- Token values
- Raw HTML
- Full authentication URLs
- Input values

Logs must be redacted by default before diagnostic text is persisted or printed. The privacy redactor removes credential fields, authentication URLs, HTML tags, and input values from log messages.

Phase 0 does not implement account login, cloud sync, real Wahlap networking, WaterFish upload, or LXNS upload, so no remote credential handling is required.

