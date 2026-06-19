package dev.fluentmai.android.feature.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fluentmai.android.core.model.ImportResult

@Composable
fun ImportScreen(
    lastResult: ImportResult?,
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
        Text(text = "Import", style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = onRunFakeImport,
            enabled = !isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (isImporting) "Importing" else "Run fixture import")
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Last result", style = MaterialTheme.typography.titleMedium)
                Text(text = lastResult?.summaryText() ?: "No fixture import has run in this session.")
            }
        }
    }
}

private fun ImportResult.summaryText(): String =
    "$inserted inserted, $skippedDuplicate duplicate, $quarantined quarantined, $rejected rejected"

