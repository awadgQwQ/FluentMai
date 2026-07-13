# Toolbox calculation and data sources

FluentMai keeps toolbox formulas in `core:model`. Compose screens format inputs and results but
do not contain a second copy of the scoring rules.

## Single-chart DX Rating

The implementation uses the existing tested DX Rating coefficient table and calculates:

`floor(chart constant × min(achievement, 100.5) / 100 × coefficient)`

Achievement boundaries, the 100.5% cap, flooring, decimal precision, and invalid inputs are
covered by JVM tests. FC/AP flags do not add Rating.

## Achievement and judgement loss

Reviewed sources:

- SEGA's official DX 1.02-A scoring adjustment:
  <https://maimai.sega.jp/news/2019-08-07/>
- AstroDX's clean-room scoring implementation:
  <https://github.com/2394425147/astrodx/blob/main/Scripts/Models/Scoring/Metrics/Internal/AchievementStats.cs>
- AstroDX judgement aggregation and BREAK extra values:
  <https://github.com/2394425147/astrodx/blob/main/Scripts/Models/Scoring/Metrics/Internal/JudgementStats.cs>
- AstroDX base judgement multipliers:
  <https://github.com/2394425147/astrodx/blob/main/Scripts/Models/Scoring/NoteRecord.cs>
- LXNS note-count schema:
  <https://maimai.lxns.net/docs/api/maimai>

The base weighted note count is `Tap + Touch + 2×Hold + 3×Slide + 5×Break`. Critical Perfect
and Perfect receive full base credit, Great receives 0.8, Good receives 0.5, and Miss receives
zero. If a chart contains BREAK notes, their extra one percent is divided equally across BREAKs.
The extra multipliers are 1.0 for Critical Perfect, 0.75 for the 2550 Perfect window, 0.5 for the
2500 Perfect window, 0.4 for Great, 0.3 for Good, and zero for Miss. This agrees with SEGA's
published 101.0000%, 100.7500%, and 100.5000% all-BREAK examples.

The calculator reports mathematical loss from the supplied note counts. It does not infer timing
windows or fabricate per-chart counts when the catalog omits them.

## Version reference

Version IDs and names come from the LXNS public song catalog version table. The app prefers the
validated runtime catalog and retains a centrally maintained fallback list in `core:model` for
offline display. Plate abbreviations are intentionally omitted until a stable, reviewable source
is available.

## Kaleid×Scope

SEGA's official international page documents the mode and its three-song/life-based behavior:
<https://maimai.sega.com/play/newfunction2/>. It does not currently expose a structured,
auditable list of gates, songs, and per-gate unlock conditions. FluentMai therefore includes a
model, repository boundary, and unavailable-state UI, but no placeholder gates or invented
conditions.
