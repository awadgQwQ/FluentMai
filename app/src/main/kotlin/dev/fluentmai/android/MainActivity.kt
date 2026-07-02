package dev.fluentmai.android

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fluentmai.android.core.database.FluentMaiDatabase
import dev.fluentmai.android.core.database.FluentMaiRepository
import dev.fluentmai.android.core.database.RoomImportPersistence
import dev.fluentmai.android.core.importer.FakeImportPipeline
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.ImportResult
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import dev.fluentmai.android.feature.home.HomeScreen
import dev.fluentmai.android.feature.importflow.ImportScreen
import dev.fluentmai.android.feature.quarantine.QuarantineScreen
import dev.fluentmai.android.feature.scores.ScoresScreen
import dev.fluentmai.android.feature.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FluentMaiCatalog"

class MainActivity : ComponentActivity() {
    private val database by lazy { FluentMaiDatabase.create(this) }
    private val repository by lazy { FluentMaiRepository(database) }
    private val persistence by lazy { RoomImportPersistence(database) }
    private val importPipeline by lazy { FakeImportPipeline() }
    private val privacyRedactor by lazy { PrivacyRedactor() }
    private val songCatalogClient by lazy { LxnsMaimaiSongCatalogClient(privacyRedactor) }
    private val songCatalogStore by lazy {
        SongCatalogStore(
            context = this,
            client = songCatalogClient,
            redactor = privacyRedactor,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FluentMaiTheme {
                FluentMaiApp(
                    repository = repository,
                    runFakeImport = { runFakeImport() },
                    loadLocalChartCatalog = { songCatalogStore.loadLocalCatalog() },
                    refreshChartCatalog = { songCatalogStore.refreshFromNetwork() },
                )
            }
        }
    }

    private suspend fun runFakeImport(): ImportResult {
        val fixture = withContext(Dispatchers.IO) {
            assets.open("valid_sample_import.json").bufferedReader().use { it.readText() }
        }
        return importPipeline.importJson(
            source = "asset:valid_sample_import.json",
            json = fixture,
            persistence = persistence,
        )
    }
}

@Composable
private fun FluentMaiApp(
    repository: FluentMaiRepository,
    runFakeImport: suspend () -> ImportResult,
    loadLocalChartCatalog: suspend () -> SongCatalogSnapshot?,
    refreshChartCatalog: suspend () -> SongCatalogSnapshot,
) {
    val startupStartedAtMs = remember { SystemClock.elapsedRealtime() }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var scoreCount by remember { mutableStateOf(0) }
    var scores by remember { mutableStateOf<List<ScoreRecord>>(emptyList()) }
    var chartRecords by remember { mutableStateOf<List<ChartRecord>>(emptyList()) }
    var isChartCatalogLoading by remember { mutableStateOf(false) }
    var quarantineCount by remember { mutableStateOf(0) }
    var quarantineRecords by remember { mutableStateOf<List<QuarantineRecord>>(emptyList()) }
    var lastImport by remember { mutableStateOf<ImportBatch?>(null) }
    var lastResult by remember { mutableStateOf<ImportResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshState() {
        scoreCount = repository.scoreCount()
        scores = repository.scores()
        quarantineCount = repository.quarantineCount()
        quarantineRecords = repository.quarantineRecords()
        lastImport = repository.latestImportBatch()
    }

    fun refreshChartRecords() {
        scope.launch {
            isChartCatalogLoading = true
            val localStartedAt = SystemClock.elapsedRealtime()
            val localSnapshot = withContext(Dispatchers.IO) { loadLocalChartCatalog() }
            if (localSnapshot != null) {
                chartRecords = localSnapshot.catalog.charts()
                Log.i(
                    TAG,
                    "Local song catalog ready in ${SystemClock.elapsedRealtime() - localStartedAt}ms: " +
                        "source=${localSnapshot.source.logName} songs=${localSnapshot.songCount} " +
                        "charts=${localSnapshot.chartCount} bytes=${localSnapshot.jsonBytes}",
                )
            } else {
                Log.w(TAG, "No local song catalog cache or bundled fallback available")
            }

            val networkStartedAt = SystemClock.elapsedRealtime()
            runCatching {
                withContext(Dispatchers.IO) { refreshChartCatalog() }
            }.onSuccess { networkSnapshot ->
                chartRecords = networkSnapshot.catalog.charts()
                Log.i(
                    TAG,
                    "LXNS song catalog background refresh completed in " +
                        "${SystemClock.elapsedRealtime() - networkStartedAt}ms: " +
                        "songs=${networkSnapshot.songCount} charts=${networkSnapshot.chartCount} " +
                        "startupElapsedMs=${SystemClock.elapsedRealtime() - startupStartedAtMs}",
                )
            }.onFailure { error ->
                Log.w(
                    TAG,
                    "LXNS song catalog background refresh failed after " +
                        "${SystemClock.elapsedRealtime() - networkStartedAt}ms: ${error.message ?: error::class.java.simpleName}",
                )
            }
            isChartCatalogLoading = false
        }
    }

    fun startImport() {
        scope.launch {
            isImporting = true
            try {
                lastResult = withContext(Dispatchers.IO) { runFakeImport() }
                refreshState()
            } finally {
                isImporting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
        refreshChartRecords()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(text = tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            AppTab.Home -> HomeScreen(
                totalScoreCount = scoreCount,
                lastImport = lastImport,
                isImporting = isImporting,
                onRunFakeImport = ::startImport,
                modifier = modifier,
            )

            AppTab.Import -> ImportScreen(
                lastResult = lastResult,
                isImporting = isImporting,
                onRunFakeImport = ::startImport,
                modifier = modifier,
            )

            AppTab.Scores -> ScoresScreen(
                scores = scores,
                modifier = modifier,
            )

            AppTab.Quarantine -> QuarantineScreen(
                quarantineCount = quarantineCount,
                records = quarantineRecords,
                modifier = modifier,
            )

            AppTab.Settings -> SettingsScreen(modifier = modifier)
        }
    }
}

@Composable
private fun FluentMaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF246B5A),
            secondary = Color(0xFF735C0F),
            tertiary = Color(0xFF7A405A),
            background = Color(0xFFFBFCF8),
            surface = Color(0xFFFFFFFF),
        ),
        content = content,
    )
}

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home),
    Import("Import", Icons.Filled.PlayArrow),
    Scores("Scores", Icons.Filled.List),
    Quarantine("Quarantine", Icons.Filled.Warning),
    Settings("Settings", Icons.Filled.Settings),
}
