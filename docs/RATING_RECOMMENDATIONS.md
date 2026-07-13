# Rating recommendation model

FluentMai recommendations are deterministic B35/B15 simulations. They are not an AI model and
do not estimate a player's skill, timing strengths, chart fit, or likelihood of reaching a goal.

## Inputs

The model only uses data already validated by the app:

- the local best score matched to a stable `songId + SD/DX + difficulty` identity;
- the chart constant;
- the chart's major-version bucket;
- the centrally tested DX Rating formula;
- the current B35 and B15 sets and their tail Rating values;
- explicit user filters and the user's local “do not practice” identity set.

Unplayed charts are not recommended because they do not have a real current achievement or
single-chart Rating. Future-version charts and charts without a reliable version bucket are not
eligible. Explicitly locked or disabled charts remain in the existing B50 baseline when they have
a historical score, matching the app's B35/B15 calculation, but they are not offered as new
practice suggestions.

## Target selection

With no explicit target, the model selects the next achievement milestone from:

`97.0, 98.0, 99.0, 99.5, 100.0, 100.5`

When a target total Rating is supplied, each chart is simulated independently. The model derives
the minimum single-chart Rating needed to reach that total:

- for a chart already in B35/B15: `current single Rating + required total gain`;
- for a chart outside the set: `bucket tail Rating + required total gain`.

It then finds the minimum achievement, at 0.0001% precision, that reaches the required
single-chart Rating. An explicit achievement target is combined with this result by taking the
higher target. If one chart cannot reach the requested total even at the 100.5% Rating cap, that
chart is excluded for this target.

## Gain and explanation

For every candidate the model recomputes the affected bucket after replacing only that chart's
achievement. It reports both:

- theoretical single-chart Rating gain;
- actual total B50 gain after the B35 or B15 cutoff is applied.

The explanation is selected from a closed set of auditable states:

- the user target is already complete;
- the chart is already in B35/B15;
- the target enters and exceeds the bucket tail;
- the target ties the bucket tail without increasing B50;
- the target remains below the bucket tail.

Results sort by actual B50 gain, theoretical gain, achievement gap, target single Rating, chart
constant, title, and stable identity. Equal inputs therefore produce equal output and order.

## Deliberate limits

No reliable chart-fit or player-skill data source is available in the current product data.
FluentMai therefore does not show a fitted difficulty score and does not claim that a chart is
“best for you”. The filters narrow mathematical candidates; they do not predict play outcomes.
