package dev.fluentmai.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.fluentmai.android.core.database.CachedWahlapScorePage
import dev.fluentmai.android.core.database.FluentMaiDatabase
import dev.fluentmai.android.core.database.FluentMaiRepository
import dev.fluentmai.android.core.database.RoomImportPersistence
import dev.fluentmai.android.core.importer.FakeImportPipeline
import dev.fluentmai.android.core.importer.RealWahlapImportAdapter
import dev.fluentmai.android.core.importer.RealWahlapImportResult
import dev.fluentmai.android.core.importer.WahlapFixtureParser
import dev.fluentmai.android.core.importer.WahlapScorePageProvider
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
import dev.fluentmai.android.vpn.core.LocalVpnService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FluentMaiCatalog"
private const val IMPORT_TAG = "FluentMaiImport"

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
                    runRealImport = { authUrl, afterLoginAttempt ->
                        runRealImport(authUrl, afterLoginAttempt)
                    },
                    loadLocalChartCatalog = { songCatalogStore.loadLocalCatalog() },
                    refreshChartCatalog = { songCatalogStore.refreshFromNetwork() },
                    redactMessage = privacyRedactor::redact,
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

    private suspend fun runRealImport(
        authUrl: String,
        afterLoginAttempt: () -> Unit = {},
    ): RealWahlapImportResult {
        val fetchedPages = mutableListOf<CachedWahlapScorePage>()
        val client = WahlapHttpScorePageClient(redactor = privacyRedactor)
        try {
            client.login(authUrl)
        } finally {
            afterLoginAttempt()
        }

        val realImportAdapter = RealWahlapImportAdapter(
            parser = WahlapFixtureParser(),
            sanitizeFailure = privacyRedactor::redact,
        )
        val result = realImportAdapter.importFetchedPages(
            source = "wahlap:real-device",
            pageProvider = WahlapScorePageProvider { difficulty ->
                client.fetchScorePage(difficulty).also { html ->
                    fetchedPages += CachedWahlapScorePage(
                        sourceBatchId = "",
                        difficulty = difficulty,
                        html = html,
                        fetchedAt = System.currentTimeMillis(),
                    )
                }
            },
            persistence = persistence,
        )
        if (result.failedDifficultyCount == 0 && result.importResult.batchId.isNotBlank()) {
            repository.replaceLatestWahlapScorePages(result.importResult.batchId, fetchedPages)
        }
        return result
    }
}

@Composable
private fun FluentMaiApp(
    repository: FluentMaiRepository,
    runFakeImport: suspend () -> ImportResult,
    runRealImport: suspend (String, () -> Unit) -> RealWahlapImportResult,
    loadLocalChartCatalog: suspend () -> SongCatalogSnapshot?,
    refreshChartCatalog: suspend () -> SongCatalogSnapshot,
    redactMessage: (String) -> String,
) {
    val context = LocalContext.current
    val startupStartedAtMs = remember { SystemClock.elapsedRealtime() }
    val hookStatus by WahlapHookBridge.status.collectAsState()
    val isHookRunning by WahlapHookBridge.vpnRunning.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var scoreCount by remember { mutableStateOf(0) }
    var scores by remember { mutableStateOf<List<ScoreRecord>>(emptyList()) }
    var chartRecords by remember { mutableStateOf<List<ChartRecord>>(emptyList()) }
    var isChartCatalogLoading by remember { mutableStateOf(false) }
    var quarantineCount by remember { mutableStateOf(0) }
    var quarantineRecords by remember { mutableStateOf<List<QuarantineRecord>>(emptyList()) }
    var lastImport by remember { mutableStateOf<ImportBatch?>(null) }
    var lastResult by remember { mutableStateOf<ImportResult?>(null) }
    var lastRealResult by remember { mutableStateOf<RealWahlapImportResult?>(null) }
    var lastImportError by remember { mutableStateOf<String?>(null) }
    var importStatus by remember { mutableStateOf(ImportRunStatus.Idle) }
    var hookLink by remember { mutableStateOf(WahlapHookHttpService.HOOK_URL) }
    var isPreparingHookLink by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(context)
        } else {
            WahlapHookBridge.setStatus("VPN permission was not granted; cannot capture Wahlap auth.")
        }
    }

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

    fun startCapturedRealImport(capturedAuthUrl: String) {
        scope.launch {
            isImporting = true
            importStatus = ImportRunStatus.Importing
            lastImportError = null
            lastRealResult = null
            val captureStopped = AtomicBoolean(false)
            fun stopCaptureAfterLogin() {
                if (captureStopped.compareAndSet(false, true)) {
                    Log.i(IMPORT_TAG, "Stopping capture services after Wahlap login attempt")
                    stopVpnService(context)
                    WahlapHookHttpService.stop(context)
                }
            }
            try {
                WahlapHookBridge.setStatus("Captured Wahlap auth; replaying login and importing scores.")
                Log.i(IMPORT_TAG, "Starting real Wahlap import from captured auth URL")
                val result = withContext(Dispatchers.IO) {
                    runRealImport(capturedAuthUrl, ::stopCaptureAfterLogin)
                }
                lastRealResult = result
                importStatus = if (result.failedDifficultyCount == 0 && result.fetchedDifficultyCount > 0) {
                    ImportRunStatus.Success
                } else {
                    ImportRunStatus.Failed
                }
                lastImportError = result.takeIf { it.failedDifficultyCount > 0 }
                    ?.failures
                    ?.joinToString("; ") { "${it.difficulty.name}: ${it.message}" }
                refreshState()
                Log.i(IMPORT_TAG, "Real Wahlap import completed: ${result.safeSummary()}")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                lastImportError = safeMessage
                importStatus = ImportRunStatus.Failed
                Log.e(IMPORT_TAG, "Real Wahlap import failed: $safeMessage")
            } finally {
                stopCaptureAfterLogin()
                isImporting = false
                WahlapHookBridge.finishImport()
            }
        }
    }

    fun startHookCapture() {
        WahlapHookHttpService.start(context)
        val vpnPrepareIntent = VpnService.prepare(context)
        if (vpnPrepareIntent != null) {
            vpnPermissionLauncher.launch(vpnPrepareIntent)
        } else {
            startVpnService(context)
        }
    }

    fun stopHookCapture() {
        stopVpnService(context)
        WahlapHookHttpService.stop(context)
    }

    fun copyHookUrl() {
        isPreparingHookLink = true
        hookLink = WahlapHookHttpService.HOOK_URL
        copyTextToClipboard(context, "FluentMai Wahlap Hook", hookLink)
        WahlapHookBridge.setStatus("Hook link copied. Open it in WeChat after starting capture.")
        isPreparingHookLink = false
    }

    LaunchedEffect(Unit) {
        refreshState()
        refreshChartRecords()
    }

    LaunchedEffect(Unit) {
        WahlapHookBridge.capturedAuthUrls.collect { capturedAuthUrl ->
            startCapturedRealImport(capturedAuthUrl)
        }
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
                realImportSummary = lastRealResult?.summaryText(),
                importStatus = importStatus.label,
                errorMessage = lastImportError,
                hookUrl = hookLink,
                hookStatus = hookStatus,
                isHookRunning = isHookRunning,
                scoreCount = scoreCount,
                isImporting = isImporting,
                isPreparingHookLink = isPreparingHookLink,
                onRunFakeImport = ::startImport,
                onStartHookCapture = ::startHookCapture,
                onStopHookCapture = ::stopHookCapture,
                onCopyHookUrl = ::copyHookUrl,
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

private enum class ImportRunStatus(val label: String) {
    Idle("Idle"),
    Importing("Importing"),
    Success("Success"),
    Failed("Failed"),
}

private fun RealWahlapImportResult.safeSummary(): String =
    "inserted=${importResult.inserted} updated=${importResult.updated} duplicate=${importResult.skippedDuplicate} " +
        "quarantined=${importResult.quarantined} rejected=${importResult.rejected} parsed=$parsedRecordCount " +
        "fetchedDifficulties=$fetchedDifficultyCount failedDifficulties=$failedDifficultyCount"

private fun RealWahlapImportResult.summaryText(): String =
    "Inserted ${importResult.inserted}, updated ${importResult.updated}, duplicate ${importResult.skippedDuplicate}, " +
        "quarantined ${importResult.quarantined}, rejected ${importResult.rejected}; " +
        "fetched $fetchedDifficultyCount difficulties, parsed $parsedRecordCount records."

private fun startVpnService(context: Context) {
    val intent = Intent(context, LocalVpnService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopVpnService(context: Context) {
    context.startService(Intent(context, LocalVpnService::class.java).apply {
        action = LocalVpnService.DISCONNECT_INTENT
    })
    WahlapHookBridge.setVpnRunning(false)
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
