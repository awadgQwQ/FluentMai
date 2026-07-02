package dev.fluentmai.android.feature.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.fluentmai.android.core.model.ImportResult

@Composable
fun ImportScreen(
    lastResult: ImportResult?,
    realImportSummary: String?,
    importStatus: String,
    errorMessage: String?,
    hookUrl: String,
    hookStatus: String,
    isHookRunning: Boolean,
    divingFishToken: String,
    lxnsToken: String,
    uploadStatus: String,
    uploadSummary: String?,
    uploadErrorMessage: String?,
    uploadProgressText: String?,
    uploadProgressFraction: Float?,
    scoreCount: Int,
    isImporting: Boolean,
    isUploading: Boolean,
    isPreparingHookLink: Boolean,
    onRunFakeImport: () -> Unit,
    onStartHookCapture: () -> Unit,
    onStopHookCapture: () -> Unit,
    onCopyHookUrl: () -> Unit,
    onDivingFishTokenChanged: (String) -> Unit,
    onLxnsTokenChanged: (String) -> Unit,
    onUploadDivingFish: () -> Unit,
    onUploadLxns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = isImporting || isUploading
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Import", style = MaterialTheme.typography.headlineSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Wahlap real import", style = MaterialTheme.typography.titleMedium)
                Text(text = hookStatus)
                Text(text = "Hook URL: $hookUrl", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartHookCapture,
                        enabled = !isBusy && !isHookRunning,
                    ) {
                        Text(text = "Start capture")
                    }
                    OutlinedButton(
                        onClick = onStopHookCapture,
                        enabled = isHookRunning,
                    ) {
                        Text(text = "Stop capture")
                    }
                }
                OutlinedButton(
                    onClick = onCopyHookUrl,
                    enabled = !isPreparingHookLink,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = if (isPreparingHookLink) "Preparing link" else "Copy hook link")
                }
            }
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
                Text(text = "Real import status", style = MaterialTheme.typography.titleMedium)
                Text(text = importStatus)
                Text(text = realImportSummary ?: "Captured Wahlap auth will import into the local database.")
                errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Upload local scores", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = divingFishToken,
                    onValueChange = onDivingFishTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = "Diving Fish upload token") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = onUploadDivingFish,
                    enabled = !isBusy && scoreCount > 0 && divingFishToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = if (isUploading) "Uploading" else "Upload to Diving Fish")
                }
                OutlinedTextField(
                    value = lxnsToken,
                    onValueChange = onLxnsTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = "LXNS user token") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedButton(
                    onClick = onUploadLxns,
                    enabled = !isBusy && scoreCount > 0 && lxnsToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Upload to LXNS")
                }
                Text(text = uploadStatus)
                uploadProgressText?.let { text ->
                    val percent = uploadProgressFraction?.let { " (${(it * 100).toInt()}%)" }.orEmpty()
                    Text(text = text + percent)
                }
                Text(text = uploadSummary ?: "Local scores ready to upload: $scoreCount")
                uploadErrorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Button(
            onClick = onRunFakeImport,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
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
                Text(text = "Last fixture result", style = MaterialTheme.typography.titleMedium)
                Text(text = lastResult?.summaryText() ?: "No fixture import has run in this session.")
                Text(text = "Local scores: $scoreCount")
            }
        }
    }
}

private fun ImportResult.summaryText(): String =
    "$inserted inserted, $updated updated, $skippedDuplicate duplicate, $quarantined quarantined, $rejected rejected"
