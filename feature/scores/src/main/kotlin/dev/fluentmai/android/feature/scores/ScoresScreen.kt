package dev.fluentmai.android.feature.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ScoreRecord
import java.util.Locale

@Composable
fun ScoresScreen(
    scores: List<ScoreRecord>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "Scores", style = MaterialTheme.typography.headlineSmall)
        }
        if (scores.isEmpty()) {
            item {
                Text(text = "No scores imported yet.")
            }
        }
        items(scores, key = { it.id }) { score ->
            ScoreRow(score = score)
        }
    }
}

@Composable
private fun ScoreRow(score: ScoreRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = score.title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "${score.difficulty.name} ${score.level}")
                Text(text = String.format(Locale.US, "%.4f%%", score.achievement))
            }
            Text(text = "DX score: ${score.dxScore?.toString() ?: "not available"}")
        }
    }
}


internal fun ChartRecord?.isNewRatingBucket(latestChartVersion: Int): Boolean =
    latestChartVersion > 0 && this?.chartVersion == latestChartVersion
