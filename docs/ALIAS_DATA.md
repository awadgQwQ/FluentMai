# Alias data pipeline

FluentMai merges two public community sources at runtime: the documented LXNS maimai alias
endpoint and the YuzuChaN alias endpoint used by EasyMai and Yuri-YuzuChaN/maimaiDX. LXNS
associates a canonical maimai `song_id` with aliases and shares the ID namespace used by the
app's song catalog. Yuzu provides broader community coverage.

## Source and licensing boundary

- API documentation: <https://maimai.lxns.net/docs/api/maimai>
- Runtime endpoint: <https://maimai.lxns.net/api/v0/maimai/alias/list>
- Yuzu runtime endpoint: <https://www.yuzuchan.moe/api/v2/aliases/maimaidx/aliases>
- Yuzu client and cache behavior reference: <https://github.com/Yuri-YuzuChaN/maimaiDX>
- EasyMai integration reference: <https://github.com/Lista233/EasyMai>

EasyMai and Yuri-YuzuChaN/maimaiDX are MIT licensed. Neither community endpoint documents a
separate redistribution license for its live alias dataset. FluentMai therefore fetches the
data at runtime for local search and caches the merged response in a canonical app-private
form; it does not ship or redistribute a copied alias database.

## Cache and update behavior

- The app starts with the last validated local cache, so offline alias search works after a
  successful fetch. Ordinary title, ID, artist, designer, version, BPM, and genre search does
  not depend on alias availability.
- Cache metadata includes a schema version, fetch time, source URL, and a deterministic
  SHA-256 content version.
- The app-private cache keeps the legacy `lxns-maimai-alias-cache-v1.json` filename so an
  existing validated LXNS-only cache remains an offline fallback during the two-source
  rollout. Its current schema and metadata identify the merged community payload.
- A network update is parsed and validated before an atomic replacement. Empty data is
  rejected. When a prior cache exists, an update retaining less than 80% of either aliased
  songs or aliases is rejected as likely truncated.
- Any request, parse, validation, or write failure leaves the previous cache untouched.

## Identity and mapping rules

- Aliases attach only to canonical `song_id`; titles are never used to guess a mapping.
  Duplicate aliases across both sources are normalized, deduplicated case-insensitively,
  and sorted.
- SD and DX charts intentionally share the song-level aliases. A chart itself is identified
  by `song_id + SD/DX + difficulty`.
- Yuzu represents many ordinary DX songs as the base song ID plus 10,000. IDs from 10,000
  through 99,999 are reduced modulo 10,000 before merging. The current source's collisions
  under that mapping have the same song name. Utage IDs at or above 100,000 stay unchanged
  so banquet charts cannot collide with ordinary songs.
- Same-title songs remain separate because their IDs differ. A stale or changed source ID is
  reported as unmapped instead of being silently reassigned by title.
- The current catalog is compared with every alias refresh. Unmapped source IDs remain in
  the cache for forward compatibility, while the UI reports their count and does not attach
  them to an unrelated song.
