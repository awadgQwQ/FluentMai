package dev.fluentmai.android.feature.quarantine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import dev.fluentmai.android.core.model.QuarantineRecord

@Composable
fun QuarantineScreen(
    quarantineCount: Int,
    records: List<QuarantineRecord>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "Quarantine", style = MaterialTheme.typography.headlineSmall)
            Text(text = "$quarantineCount quarantined items")
        }
        if (records.isEmpty()) {
            item {
                Text(text = "No quarantined records.")
            }
        }
        items(records, key = { it.id }) { record ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = record.reason, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Difficulty: ${record.difficulty?.name ?: "unknown"}")
                    Text(text = "Fingerprint: ${record.rawFingerprint.take(16)}")
                }
            }
        }
    }
}

