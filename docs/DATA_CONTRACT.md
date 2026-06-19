# Data Contract

## Difficulty Mapping

- `BASIC = 0`
- `ADVANCED = 1`
- `EXPERT = 2`
- `MASTER = 3`
- `RE_MASTER = 4`

## ScoreRecord

- `id`
- `title`
- `difficulty`
- `level`
- `levelIndex`
- `achievement`
- `dxScore`
- `fc`
- `fs`
- `sourceBatchId`
- `importedAt`

`id` is deterministic from normalized `title` and `levelIndex`. The valid achievement range for Phase 0 fixtures is `0.0..101.0`.

## ImportBatch

- `id`
- `source`
- `importedAt`
- `totalParsed`
- `inserted`
- `updated`
- `skippedDuplicate`
- `quarantined`
- `rejected`

## QuarantineRecord

- `id`
- `reason`
- `difficulty`
- `rawFingerprint`
- `sourceBatchId`
- `createdAt`

Only the raw fingerprint is stored for abnormal records. The raw fixture record is not persisted.

## ImportResult

- `batchId`
- `inserted`
- `updated`
- `skippedDuplicate`
- `quarantined`
- `rejected`

