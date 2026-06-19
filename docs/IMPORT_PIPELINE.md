# Import Pipeline

Phase 0 uses a fake local fixture pipeline.

1. Read JSON from `fixtures/`.
2. Parse fixture records with `FixtureImportParser`.
3. Validate records with `ScoreRecordValidator`.
4. Route invalid records to `QuarantineRecord`.
5. Deduplicate valid records with `ImportDeduplicator`.
6. Write accepted scores to Room.
7. Write quarantine entries to Room.
8. Write an `ImportBatch`.
9. Return an `ImportResult`.

## Validation Rules

- `title` must not be blank.
- `achievement` must be within `0.0..101.0`.
- `levelIndex` must be `0..4`.
- `difficulty` must map to the same `levelIndex` when both are present.
- Duplicate imports must not create duplicate score rows.
- Invalid records must not contaminate the score table.

## Deduplication

Score ids are deterministic from normalized title and level index. The importer also checks existing Room score ids before insert. Room uses conflict-ignore inserts and a unique index on title plus level index as an additional guard.

## Quarantine

Quarantine records store reason, optional difficulty, fingerprint, batch id, and creation time. They do not store raw HTML, credentials, full URLs, or raw fixture payloads.

