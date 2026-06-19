package dev.fluentmai.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fluentmai.android.core.model.ImportBatch

@Composable
fun HomeScreen(
    totalScoreCount: Int,
    lastImport: ImportBatch?,
    isImporting: Boolean,
    onRunFakeImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "FluentMai",
            style = MaterialTheme.typography.headlineMedium,
        )
        SummarySurface(
            title = "Total scores",
            value = totalScoreCount.toString(),
        )
        SummarySurface(
            title = "Last import",
            value = lastImport?.summaryText() ?: "No imports yet",
        )
        Button(
            onClick = onRunFakeImport,
            enabled = !isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (isImporting) "Importing" else "Run fake import")
        }
    }
}

@Composable
private fun SummarySurface(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(modifier = Modifier.height(0.dp))
}

private fun ImportBatch.summaryText(): String =
    "$inserted inserted, $skippedDuplicate duplicate, $quarantined quarantined"

