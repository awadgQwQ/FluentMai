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
import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.importer.RealWahlapImportAdapter
import dev.fluentmai.android.core.importer.RealWahlapImportResult
import dev.fluentmai.android.core.importer.WahlapFixtureParser
import dev.fluentmai.android.core.importer.WahlapScorePageProvider
import dev.fluentmai.android.core.importer.WahlapSupplementalPageProvider
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.ImportResult
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import dev.fluentmai.android.core.upload.MaimaiScoreUploader
import dev.fluentmai.android.core.upload.MaimaiUploadProgress
import dev.fluentmai.android.core.upload.MaimaiUploadResult
import dev.fluentmai.android.feature.home.HomeScreen
import dev.fluentmai.android.feature.importflow.ImportScreen
import dev.fluentmai.android.feature.quarantine.QuarantineScreen
import dev.fluentmai.android.feature.scores.ScoresScreen
import dev.fluentmai.android.feature.settings.SettingsScreen
import dev.fluentmai.android.vpn.core.LocalVpnService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FluentMaiCatalog"
private const val IMPORT_TAG = "FluentMaiImport"
private const val UPLOAD_TAG = "FluentMaiUpload"
private const val TOKEN_PREFS_NAME = "fluentmai_tokens"
private const val PREF_DIVING_FISH_TOKEN = "diving_fish_upload_token"
private const val PREF_LXNS_TOKEN = "lxns_upload_token"

class MainActivity : ComponentActivity() {
    private val database by lazy { FluentMaiDatabase.create(this) }
    private val repository by lazy { FluentMaiRepository(database) }
    private val persistence by lazy { RoomImportPersistence(database) }
    private val importPipeline by lazy { FakeImportPipeline() }
    private val privacyRedactor by lazy { PrivacyRedactor() }
    private val scoreUploader by lazy {
        MaimaiScoreUploader(transport = AndroidNetworkMaimaiUploadTransport(this))
    }
    private val songCatalogClient by lazy { LxnsMaimaiSongCatalogClient(privacyRedactor) }
    private val songCatalogStore by lazy {
        SongCatalogStore(
            context = this,
            client = songCatalogClient,
            redactor = privacyRedactor,
        )
    }
    private val tokenPreferences by lazy {
        getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
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
                    runCookieImport = { cookieInput -> runCookieImport(cookieInput) },
                    loadLocalChartCatalog = { songCatalogStore.loadLocalCatalog() },
                    refreshChartCatalog = { songCatalogStore.refreshFromNetwork() },
                    uploadToDivingFish = { token, onProgress -> uploadToDivingFish(token, onProgress) },
                    rebuildDivingFish = { token, onProgress -> rebuildDivingFish(token, onProgress) },
                    uploadToLxns = { token, onProgress -> uploadToLxns(token, onProgress) },
                    initialDivingFishToken = tokenPreferences.getString(PREF_DIVING_FISH_TOKEN, "").orEmpty(),
                    initialLxnsToken = tokenPreferences.getString(PREF_LXNS_TOKEN, "").orEmpty(),
                    persistDivingFishToken = { token -> persistToken(PREF_DIVING_FISH_TOKEN, token) },
                    persistLxnsToken = { token -> persistToken(PREF_LXNS_TOKEN, token) },
                    redactMessage = privacyRedactor::redact,
                )
            }
        }
    }

    private fun persistToken(key: String, value: String) {
        tokenPreferences.edit().putString(key, value).apply()
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
        val client = WahlapHttpScorePageClient(
            redactor = privacyRedactor,
            supplementalPageSink = { page ->
                runCatching {
                    val safeLabel = page.label.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(filesDir, "wahlap-supplemental-$safeLabel.html").writeText(page.html)
                }.onFailure { error ->
                    Log.w(IMPORT_TAG, "Unable to cache supplemental page ${page.label}: ${error::class.java.simpleName}")
                }
            },
            debugPageSink = { label, html ->
                runCatching {
                    val safeLabel = label.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(filesDir, "wahlap-debug-$safeLabel.html").writeText(html)
                }.onFailure { error ->
                    Log.w(IMPORT_TAG, "Unable to cache Wahlap debug page $label: ${error::class.java.simpleName}")
                }
            },
        )
        try {
            client.login(authUrl)
        } finally {
            afterLoginAttempt()
        }

        val catalog = fetchSongCatalogOrEmpty()
        val realImportAdapter = RealWahlapImportAdapter(
            parser = WahlapFixtureParser(songCatalog = catalog),
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
            supplementalPageProvider = WahlapSupplementalPageProvider {
                client.fetchSupplementalScorePages()
            },
            persistence = persistence,
        )
        if (result.failedDifficultyCount == 0 && result.importResult.batchId.isNotBlank()) {
            repository.replaceLatestWahlapScorePages(result.importResult.batchId, fetchedPages)
        }
        return result
    }

    private suspend fun runCookieImport(cookieInput: String): RealWahlapImportResult {
        val credentials = WahlapCookieImportCredentials.parse(cookieInput)
        val catalog = fetchSongCatalogOrEmpty()
        val fetchedPages = mutableListOf<CachedWahlapScorePage>()
        val realImportAdapter = RealWahlapImportAdapter(
            parser = WahlapFixtureParser(songCatalog = catalog),
            sanitizeFailure = privacyRedactor::redact,
        )
        val client = WahlapManualCookieScorePageClient(
            credentials = credentials,
            redactor = privacyRedactor,
            supplementalPageSink = { page ->
                runCatching {
                    val safeLabel = page.label.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(filesDir, "wahlap-manual-supplemental-$safeLabel.html").writeText(page.html)
                }.onFailure { error ->
                    Log.w(IMPORT_TAG, "Unable to cache manual supplemental page ${page.label}: ${error::class.java.simpleName}")
                }
            },
            debugPageSink = { label, html ->
                runCatching {
                    val safeLabel = label.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(filesDir, "wahlap-manual-debug-$safeLabel.html").writeText(html)
                }.onFailure { error ->
                    Log.w(IMPORT_TAG, "Unable to cache manual Wahlap debug page $label: ${error::class.java.simpleName}")
                }
            },
        )
        return try {
            client.validateLogin()
            val result = realImportAdapter.importFetchedPages(
                source = "wahlap:manual-cookie",
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
                supplementalPageProvider = WahlapSupplementalPageProvider {
                    client.fetchSupplementalScorePages()
                },
                persistence = persistence,
            )
            if (result.failedDifficultyCount == 0 && result.importResult.batchId.isNotBlank()) {
                repository.replaceLatestWahlapScorePages(result.importResult.batchId, fetchedPages)
            }
            result
        } finally {
            client.close()
        }
    }

    private suspend fun fetchSongCatalogOrEmpty(): MaimaiSongCatalog =
        songCatalogStore.loadLocalCatalog()?.catalog ?: MaimaiSongCatalog.Empty

    private suspend fun uploadToDivingFish(
        token: String,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        onProgress(MaimaiUploadProgress(0, 1, "Reading local scores"))
        val currentScores = repository.scores()
        return scoreUploader.uploadToDivingFish(
            importToken = token,
            scores = currentScores,
            onProgress = onProgress,
        )
    }

    private suspend fun rebuildDivingFish(
        token: String,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        onProgress(MaimaiUploadProgress(0, 1, "Reading local scores"))
        val currentScores = repository.scores()
        return scoreUploader.rebuildDivingFishRecords(
            importToken = token,
            freshScores = currentScores,
            recordsToRemove = emptyList(),
            onProgress = onProgress,
        )
    }

    private suspend fun uploadToLxns(
        token: String,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        onProgress(MaimaiUploadProgress(0, 1, "Reading local scores"))
        val catalog = songCatalogStore.loadLocalCatalog()?.catalog ?: MaimaiSongCatalog.Empty
        val currentScores = repository.scores().withCatalogSongIds(catalog)
        return scoreUploader.uploadToLxns(
            userToken = token,
            scores = currentScores,
            onProgress = onProgress,
        )
    }

    private fun List<ScoreRecord>.withCatalogSongIds(catalog: MaimaiSongCatalog): List<ScoreRecord> =
        map { score ->
            if (score.songId != null) {
                score
            } else {
                score.copy(songId = catalog.idForTitle(score.title))
            }
        }
}

@Composable
private fun FluentMaiApp(
    repository: FluentMaiRepository,
    runFakeImport: suspend () -> ImportResult,
    runRealImport: suspend (String, () -> Unit) -> RealWahlapImportResult,
    runCookieImport: suspend (String) -> RealWahlapImportResult,
    loadLocalChartCatalog: suspend () -> SongCatalogSnapshot?,
    refreshChartCatalog: suspend () -> SongCatalogSnapshot,
    uploadToDivingFish: suspend (String, (MaimaiUploadProgress) -> Unit) -> MaimaiUploadResult,
    rebuildDivingFish: suspend (String, (MaimaiUploadProgress) -> Unit) -> MaimaiUploadResult,
    uploadToLxns: suspend (String, (MaimaiUploadProgress) -> Unit) -> MaimaiUploadResult,
    initialDivingFishToken: String,
    initialLxnsToken: String,
    persistDivingFishToken: (String) -> Unit,
    persistLxnsToken: (String) -> Unit,
    redactMessage: (String) -> String,
) {
    val context = LocalContext.current
    val authUrlRedactor = remember { PrivacyRedactor() }
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
    var uploadStatus by remember { mutableStateOf(UploadRunStatus.Idle) }
    var divingFishToken by remember { mutableStateOf(initialDivingFishToken) }
    var lxnsToken by remember { mutableStateOf(initialLxnsToken) }
    var lastUploadResult by remember { mutableStateOf<MaimaiUploadResult?>(null) }
    var lastUploadError by remember { mutableStateOf<String?>(null) }
    var uploadProgressText by remember { mutableStateOf<String?>(null) }
    var uploadProgressFraction by remember { mutableStateOf<Float?>(null) }
    var hookLink by remember { mutableStateOf(WahlapHookHttpService.HOOK_URL) }
    var wahlapCookieInput by remember { mutableStateOf("") }
    var isPreparingHookLink by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
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

    fun updateUploadProgress(progress: MaimaiUploadProgress) {
        uploadProgressText = progress.message
        uploadProgressFraction = if (progress.completedSteps <= 0) {
            null
        } else {
            progress.fraction.coerceIn(0f, 1f)
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

    fun startDivingFishUpload() {
        val capturedToken = divingFishToken.trim()
        if (capturedToken.isBlank()) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Enter the Diving Fish upload token first."
            return
        }
        if (scoreCount <= 0) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Import scores before uploading."
            return
        }
        scope.launch {
            isUploading = true
            uploadStatus = UploadRunStatus.Uploading
            lastUploadError = null
            lastUploadResult = null
            updateUploadProgress(MaimaiUploadProgress(0, 1, "Preparing Diving Fish upload"))
            try {
                val result = withContext(Dispatchers.IO) {
                    uploadToDivingFish(capturedToken) { progress ->
                        scope.launch { updateUploadProgress(progress) }
                    }
                }
                lastUploadResult = result
                uploadStatus = result.toUploadRunStatus()
                uploadProgressText = result.message
                uploadProgressFraction = if (result.success || result.hasCloudLocalDiff) 1f else uploadProgressFraction
                Log.i(UPLOAD_TAG, "Diving Fish upload completed: ${result.safeSummary()}")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                lastUploadError = safeMessage
                uploadStatus = UploadRunStatus.Failed
                uploadProgressText = "Upload failed: $safeMessage"
                Log.e(UPLOAD_TAG, "Diving Fish upload failed: $safeMessage")
            } finally {
                isUploading = false
            }
        }
    }

    fun startDivingFishRebuild() {
        val capturedToken = divingFishToken.trim()
        if (capturedToken.isBlank()) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Enter the Diving Fish upload token first."
            return
        }
        if (scoreCount <= 0) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Import scores before rebuilding Diving Fish records."
            return
        }
        scope.launch {
            isUploading = true
            uploadStatus = UploadRunStatus.Uploading
            lastUploadError = null
            lastUploadResult = null
            updateUploadProgress(MaimaiUploadProgress(0, 1, "Preparing guarded Diving Fish rebuild"))
            try {
                val result = withContext(Dispatchers.IO) {
                    rebuildDivingFish(capturedToken) { progress ->
                        scope.launch { updateUploadProgress(progress) }
                    }
                }
                lastUploadResult = result
                uploadStatus = result.toUploadRunStatus()
                uploadProgressText = result.message
                uploadProgressFraction = if (result.success || result.hasCloudLocalDiff) 1f else uploadProgressFraction
                Log.i(UPLOAD_TAG, "Diving Fish rebuild completed: ${result.safeSummary()}")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                lastUploadError = safeMessage
                uploadStatus = UploadRunStatus.Failed
                uploadProgressText = "Rebuild failed: $safeMessage"
                Log.e(UPLOAD_TAG, "Diving Fish rebuild failed: $safeMessage")
            } finally {
                isUploading = false
            }
        }
    }

    fun startLxnsUpload() {
        val capturedToken = lxnsToken.trim()
        if (capturedToken.isBlank()) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Enter the LXNS user token first."
            return
        }
        if (scoreCount <= 0) {
            uploadStatus = UploadRunStatus.Failed
            lastUploadError = "Import scores before uploading."
            return
        }
        scope.launch {
            isUploading = true
            uploadStatus = UploadRunStatus.Uploading
            lastUploadError = null
            lastUploadResult = null
            updateUploadProgress(MaimaiUploadProgress(0, 1, "Preparing LXNS upload"))
            try {
                val result = withContext(Dispatchers.IO) {
                    uploadToLxns(capturedToken) { progress ->
                        scope.launch { updateUploadProgress(progress) }
                    }
                }
                lastUploadResult = result
                uploadStatus = if (result.success) UploadRunStatus.Success else UploadRunStatus.Failed
                uploadProgressText = result.message
                uploadProgressFraction = if (result.success) 1f else uploadProgressFraction
                Log.i(UPLOAD_TAG, "LXNS upload completed: ${result.safeSummary()}")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                lastUploadError = safeMessage
                uploadStatus = UploadRunStatus.Failed
                uploadProgressText = "Upload failed: $safeMessage"
                Log.e(UPLOAD_TAG, "LXNS upload failed: $safeMessage")
            } finally {
                isUploading = false
            }
        }
    }

    fun startManualCookieImport() {
        val capturedInput = wahlapCookieInput.trim()
        if (capturedInput.isBlank()) {
            importStatus = ImportRunStatus.Failed
            lastImportError = "Paste a Wahlap Cookie or Reqable request header first."
            return
        }
        scope.launch {
            isImporting = true
            importStatus = ImportRunStatus.Importing
            lastImportError = null
            lastRealResult = null
            try {
                stopVpnService(context)
                WahlapHookHttpService.stop(context)
                WahlapHookBridge.finishImport()
                WahlapHookBridge.setStatus("Importing scores with manual Wahlap Cookie credentials.")
                val result = withContext(Dispatchers.IO) { runCookieImport(capturedInput) }
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
                Log.i(IMPORT_TAG, "Manual Wahlap import completed: ${result.safeSummary()}")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                lastImportError = safeMessage
                importStatus = ImportRunStatus.Failed
                Log.e(IMPORT_TAG, "Manual Wahlap import failed: $safeMessage")
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
        scope.launch {
            isPreparingHookLink = true
            try {
                val authUrl = withContext(Dispatchers.IO) {
                    WahlapWechatAuthUrlClient(authUrlRedactor).maimaiDxAuthUrl()
                }
                hookLink = authUrl
                copyTextToClipboard(context, "FluentMai WeChat auth link", authUrl)
                WahlapHookBridge.setStatus("Wahlap WeChat auth link copied. Open it in WeChat; VPN capture will record the callback.")
            } catch (error: Exception) {
                val safeMessage = redactMessage(error.message ?: error::class.java.simpleName)
                hookLink = WahlapHookHttpService.HOOK_URL
                copyTextToClipboard(context, "FluentMai fallback Hook link", WahlapHookHttpService.HOOK_URL)
                WahlapHookBridge.setStatus("Auth link generation failed; copied fallback Hook link: $safeMessage")
            } finally {
                isPreparingHookLink = false
            }
        }
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
                divingFishToken = divingFishToken,
                lxnsToken = lxnsToken,
                uploadStatus = uploadStatus.label,
                uploadSummary = lastUploadResult?.summaryText(),
                uploadErrorMessage = lastUploadError,
                uploadProgressText = uploadProgressText,
                uploadProgressFraction = uploadProgressFraction,
                scoreCount = scoreCount,
                isImporting = isImporting,
                isUploading = isUploading,
                isPreparingHookLink = isPreparingHookLink,
                wahlapCookieInput = wahlapCookieInput,
                onRunFakeImport = ::startImport,
                onStartHookCapture = ::startHookCapture,
                onStopHookCapture = ::stopHookCapture,
                onCopyHookUrl = ::copyHookUrl,
                onWahlapCookieInputChanged = { value -> wahlapCookieInput = value },
                onImportWahlapCookie = ::startManualCookieImport,
                onDivingFishTokenChanged = { token ->
                    divingFishToken = token
                    persistDivingFishToken(token)
                },
                onLxnsTokenChanged = { token ->
                    lxnsToken = token
                    persistLxnsToken(token)
                },
                onUploadDivingFish = ::startDivingFishUpload,
                onRebuildDivingFish = ::startDivingFishRebuild,
                onUploadLxns = ::startLxnsUpload,
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

private enum class UploadRunStatus(val label: String) {
    Idle("Idle"),
    Uploading("Uploading"),
    Success("Success"),
    CloudMismatch("Uploaded, verify mismatch"),
    Failed("Failed"),
}

private fun ImportResult.safeSummary(): String =
    "inserted=$inserted updated=$updated duplicate=$skippedDuplicate quarantined=$quarantined rejected=$rejected"

private fun RealWahlapImportResult.safeSummary(): String =
    "${importResult.safeSummary()} parsed=$parsedRecordCount " +
        "fetchedDifficulties=$fetchedDifficultyCount failedDifficulties=$failedDifficultyCount " +
        "supplementalPages=$fetchedSupplementalPageCount supplementalParsed=$parsedSupplementalRecordCount"

private fun RealWahlapImportResult.summaryText(): String =
    "Inserted ${importResult.inserted}, updated ${importResult.updated}, duplicate ${importResult.skippedDuplicate}, " +
        "quarantined ${importResult.quarantined}, rejected ${importResult.rejected}; " +
        "fetched $fetchedDifficultyCount difficulties, parsed $parsedRecordCount records, " +
        "supplemental pages $fetchedSupplementalPageCount, supplemental records $parsedSupplementalRecordCount."

private fun MaimaiUploadResult.safeSummary(): String =
    "platform=${platform.name} success=$success status=$statusCode uploaded=$uploadedScoreCount " +
        "updated=$updatedCount created=$createdCount " +
        "cloudOnly=${syncDiff?.cloudOnly?.size ?: 0} localOnly=${syncDiff?.localOnly?.size ?: 0}"

private fun MaimaiUploadResult.summaryText(): String =
    "${platform.displayName}: ${displayStatusText()}, " +
        "$uploadedScoreCount scores, HTTP $statusCode, $message"

private fun MaimaiUploadResult.toUploadRunStatus(): UploadRunStatus =
    when {
        success -> UploadRunStatus.Success
        hasCloudLocalDiff -> UploadRunStatus.CloudMismatch
        else -> UploadRunStatus.Failed
    }

private fun MaimaiUploadResult.displayStatusText(): String =
    when {
        success -> "Success"
        hasCloudLocalDiff -> "Uploaded, verify mismatch"
        else -> "Failed"
    }

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
