package dev.fluentmai.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Privacy")
        Text(text = "Phase 0 stores score data only in the local Room database.")
        Text(text = "No Cookie, Token, raw HTML, full authentication URL, or input value is stored or logged.")
        Text(text = "Real Hook, VPN, Wahlap networking, WaterFish upload, and LXNS upload are not implemented in this MVP skeleton.")
    }
}

