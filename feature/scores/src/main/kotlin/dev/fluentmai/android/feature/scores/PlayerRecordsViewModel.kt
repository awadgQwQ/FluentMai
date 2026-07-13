package dev.fluentmai.android.feature.scores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fluentmai.android.core.model.AchievementRank
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.FullComboStatus
import dev.fluentmai.android.core.model.FullSyncStatus
import dev.fluentmai.android.core.model.MaimaiMajorVersion
import dev.fluentmai.android.core.model.PlateKind
import dev.fluentmai.android.core.model.PlateProgress
import dev.fluentmai.android.core.model.PlayedFilter
import dev.fluentmai.android.core.model.PlayerChartRecord
import dev.fluentmai.android.core.model.PlayerRecordCatalog
import dev.fluentmai.android.core.model.PlayerRecordFilters
import dev.fluentmai.android.core.model.PlayerRecordSort
import dev.fluentmai.android.core.model.PlayerRecordStats
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import dev.fluentmai.android.core.model.SongAliasCatalog
import dev.fluentmai.android.core.model.VersionAgeFilter
import dev.fluentmai.android.core.model.buildPlayerRecordCatalog
import dev.fluentmai.android.core.model.calculatePlateProgress
import dev.fluentmai.android.core.model.filterPlayerRecords
import dev.fluentmai.android.core.model.stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class PlayerRecordsSection { RECORDS, PLATES }

internal enum class PlateListSort { LEVEL_DESC, TITLE_ASC, SONG_ID_ASC }

internal data class PlayerRecordsUiState(
    val section: PlayerRecordsSection = PlayerRecordsSection.RECORDS,
    val filters: PlayerRecordFilters = PlayerRecordFilters(),
    val constantMinText: String = "",
    val constantMaxText: String = "",
    val records: List<PlayerChartRecord> = emptyList(),
    val stats: PlayerRecordStats? = null,
    val availableVersions: List<MaimaiMajorVersion> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val selectedPlate: PlateKind = PlateKind.GENERAL,
    val selectedPlateVersionId: Int? = null,
    val selectedPlateDifficulty: Difficulty? = null,
    val plateIncompleteOnly: Boolean = true,
    val plateSort: PlateListSort = PlateListSort.LEVEL_DESC,
    val plateProgress: PlateProgress? = null,
    val isWorking: Boolean = false,
)

internal class PlayerRecordsViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreState(savedStateHandle))
    val uiState: StateFlow<PlayerRecordsUiState> = _uiState.asStateFlow()

    private var catalog: PlayerRecordCatalog? = null
    private var currentVersionId: Int? = null
    private var indexedCharts: List<ChartRecord>? = null
    private var indexedScores: List<ScoreRecord>? = null
    private var indexedMajorVersions: List<MaimaiMajorVersion>? = null
    private var indexedAliases: SongAliasCatalog? = null
    private var generation = 0L
    private var indexJob: Job? = null
    private var queryJob: Job? = null

    fun submitCatalog(
        charts: List<ChartRecord>,
        scores: List<ScoreRecord>,
        majorVersions: List<MaimaiMajorVersion>,
        operatingVersionId: Int?,
        aliases: SongAliasCatalog = SongAliasCatalog.Empty,
    ) {
        if (
            charts === indexedCharts &&
            scores === indexedScores &&
            majorVersions === indexedMajorVersions &&
            aliases === indexedAliases &&
            operatingVersionId == currentVersionId
        ) return
        indexedCharts = charts
        indexedScores = scores
        indexedMajorVersions = majorVersions
        indexedAliases = aliases
        currentVersionId = operatingVersionId
        val requestGeneration = ++generation
        indexJob?.cancel()
        queryJob?.cancel()
        indexJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val result = withContext(Dispatchers.Default) {
                val builtCatalog = buildPlayerRecordCatalog(charts, scores)
                val versions = (
                    majorVersions + charts.mapNotNull { chart ->
                        chart.chartVersionName
                            ?.takeIf { chart.chartVersion > 0 && it.isNotBlank() }
                            ?.let { MaimaiMajorVersion(chart.chartVersion, it.trim()) }
                    }
                    ).distinctBy { it.id }.sortedByDescending { it.id }
                val genres = charts.map { it.genre.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
                Triple(builtCatalog, versions, genres)
            }
            if (requestGeneration != generation) return@launch
            catalog = result.first
            val selectedVersion = _uiState.value.selectedPlateVersionId
                ?: operatingVersionId
                ?: result.second.firstOrNull()?.id
            _uiState.update {
                it.copy(
                    availableVersions = result.second,
                    availableGenres = result.third,
                    selectedPlateVersionId = selectedVersion,
                )
            }
            persist(KEY_PLATE_VERSION, selectedVersion)
            runQuery()
            updatePlateProgress()
        }
    }

    fun updateSection(value: PlayerRecordsSection) {
        persist(KEY_SECTION, value.name)
        _uiState.update { it.copy(section = value) }
    }

    fun updateQuery(value: String) = updateFilters(180L) { it.copy(query = value) }

    fun updateConstantMin(value: String) {
        persist(KEY_CONSTANT_MIN, value)
        _uiState.update { it.copy(constantMinText = value) }
        updateFilters { it.copy(constantMin = value.trim().toDoubleOrNull()) }
    }

    fun updateConstantMax(value: String) {
        persist(KEY_CONSTANT_MAX, value)
        _uiState.update { it.copy(constantMaxText = value) }
        updateFilters { it.copy(constantMax = value.trim().toDoubleOrNull()) }
    }

    fun updateDisplayLevel(value: String) = updateFilters { it.copy(displayLevel = value) }
    fun updateDifficulty(value: Difficulty?) = updateFilters { it.copy(difficulty = value) }
    fun updateGenre(value: String?) = updateFilters { it.copy(genre = value) }
    fun updateSongType(value: SongType?) = updateFilters { it.copy(songType = value) }
    fun updateRank(value: AchievementRank?) = updateFilters { it.copy(rank = value) }
    fun updateFullCombo(value: FullComboStatus?) = updateFilters { it.copy(fullCombo = value) }
    fun updateFullSync(value: FullSyncStatus?) = updateFilters { it.copy(fullSync = value) }
    fun updatePlateBlocker(value: PlateKind?) = updateFilters { it.copy(plateBlockerFor = value) }
    fun updatePlayed(value: PlayedFilter) = updateFilters { it.copy(played = value) }
    fun updateVersionAge(value: VersionAgeFilter) = updateFilters { it.copy(versionAge = value) }
    fun updateRecordVersion(value: Int?) = updateFilters { it.copy(versionId = value) }
    fun updateSort(value: PlayerRecordSort) = updateFilters { it.copy(sort = value) }

    fun resetFilters() {
        val reset = PlayerRecordFilters()
        persistFilters(reset)
        persist(KEY_CONSTANT_MIN, "")
        persist(KEY_CONSTANT_MAX, "")
        _uiState.update { it.copy(filters = reset, constantMinText = "", constantMaxText = "") }
        runQuery()
    }

    fun updatePlateKind(value: PlateKind) {
        persist(KEY_PLATE_KIND, value.name)
        _uiState.update { it.copy(selectedPlate = value) }
        updatePlateProgress()
    }

    fun updatePlateVersion(value: Int) {
        persist(KEY_PLATE_VERSION, value)
        _uiState.update { it.copy(selectedPlateVersionId = value) }
        updatePlateProgress()
    }

    fun updatePlateDifficulty(value: Difficulty?) {
        persist(KEY_PLATE_DIFFICULTY, value?.name)
        _uiState.update { it.copy(selectedPlateDifficulty = value) }
    }

    fun updatePlateIncompleteOnly(value: Boolean) {
        persist(KEY_PLATE_INCOMPLETE, value)
        _uiState.update { it.copy(plateIncompleteOnly = value) }
    }

    fun updatePlateSort(value: PlateListSort) {
        persist(KEY_PLATE_SORT, value.name)
        _uiState.update { it.copy(plateSort = value) }
    }

    private fun updateFilters(
        debounceMillis: Long = 0,
        transform: (PlayerRecordFilters) -> PlayerRecordFilters,
    ) {
        val next = transform(_uiState.value.filters)
        if (next == _uiState.value.filters) return
        persistFilters(next)
        _uiState.update { it.copy(filters = next) }
        runQuery(debounceMillis)
    }

    private fun runQuery(debounceMillis: Long = 0) {
        val playerCatalog = catalog ?: return
        val filters = _uiState.value.filters
        val operatingVersion = currentVersionId
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            if (debounceMillis > 0) delay(debounceMillis)
            val records = withContext(Dispatchers.Default) {
                filterPlayerRecords(playerCatalog.records, filters, operatingVersion, indexedAliases ?: SongAliasCatalog.Empty)
            }
            if (catalog !== playerCatalog || _uiState.value.filters != filters) return@launch
            _uiState.update {
                it.copy(
                    records = records,
                    stats = playerCatalog.stats(records),
                    isWorking = false,
                )
            }
        }
    }

    private fun updatePlateProgress() {
        val playerCatalog = catalog ?: return
        val state = _uiState.value
        val versionId = state.selectedPlateVersionId
        val versionName = state.availableVersions.firstOrNull { it.id == versionId }?.name
        val progress = calculatePlateProgress(playerCatalog.records, state.selectedPlate, versionId, versionName)
        _uiState.update { it.copy(plateProgress = progress) }
    }

    private fun persistFilters(filters: PlayerRecordFilters) {
        persist(KEY_QUERY, filters.query)
        persist(KEY_DISPLAY_LEVEL, filters.displayLevel)
        persist(KEY_DIFFICULTY, filters.difficulty?.name)
        persist(KEY_GENRE, filters.genre)
        persist(KEY_SONG_TYPE, filters.songType?.name)
        persist(KEY_RANK, filters.rank?.name)
        persist(KEY_FC, filters.fullCombo?.name)
        persist(KEY_FS, filters.fullSync?.name)
        persist(KEY_PLATE_BLOCKER, filters.plateBlockerFor?.name)
        persist(KEY_PLAYED, filters.played.name)
        persist(KEY_VERSION_AGE, filters.versionAge.name)
        persist(KEY_RECORD_VERSION, filters.versionId)
        persist(KEY_SORT, filters.sort.name)
    }

    private fun <T> persist(key: String, value: T?) {
        savedStateHandle[key] = value
    }

    private companion object {
        private const val KEY_SECTION = "records.section"
        private const val KEY_QUERY = "records.query"
        private const val KEY_CONSTANT_MIN = "records.constant.min"
        private const val KEY_CONSTANT_MAX = "records.constant.max"
        private const val KEY_DISPLAY_LEVEL = "records.level"
        private const val KEY_DIFFICULTY = "records.difficulty"
        private const val KEY_GENRE = "records.genre"
        private const val KEY_SONG_TYPE = "records.songType"
        private const val KEY_RANK = "records.rank"
        private const val KEY_FC = "records.fc"
        private const val KEY_FS = "records.fs"
        private const val KEY_PLATE_BLOCKER = "records.plateBlocker"
        private const val KEY_PLAYED = "records.played"
        private const val KEY_VERSION_AGE = "records.versionAge"
        private const val KEY_RECORD_VERSION = "records.version"
        private const val KEY_SORT = "records.sort"
        private const val KEY_PLATE_KIND = "plates.kind"
        private const val KEY_PLATE_VERSION = "plates.version"
        private const val KEY_PLATE_DIFFICULTY = "plates.difficulty"
        private const val KEY_PLATE_INCOMPLETE = "plates.incomplete"
        private const val KEY_PLATE_SORT = "plates.sort"

        private fun restoreState(handle: SavedStateHandle): PlayerRecordsUiState {
            val minText: String = handle[KEY_CONSTANT_MIN] ?: ""
            val maxText: String = handle[KEY_CONSTANT_MAX] ?: ""
            return PlayerRecordsUiState(
                section = enumOrDefault<PlayerRecordsSection>(handle[KEY_SECTION], PlayerRecordsSection.RECORDS),
                filters = PlayerRecordFilters(
                    query = handle[KEY_QUERY] ?: "",
                    versionId = handle[KEY_RECORD_VERSION],
                    constantMin = minText.toDoubleOrNull(),
                    constantMax = maxText.toDoubleOrNull(),
                    displayLevel = handle[KEY_DISPLAY_LEVEL] ?: "",
                    difficulty = enumOrNull<Difficulty>(handle[KEY_DIFFICULTY]),
                    genre = handle[KEY_GENRE],
                    songType = enumOrNull<SongType>(handle[KEY_SONG_TYPE]),
                    rank = enumOrNull<AchievementRank>(handle[KEY_RANK]),
                    fullCombo = enumOrNull<FullComboStatus>(handle[KEY_FC]),
                    fullSync = enumOrNull<FullSyncStatus>(handle[KEY_FS]),
                    plateBlockerFor = enumOrNull<PlateKind>(handle[KEY_PLATE_BLOCKER]),
                    played = enumOrDefault<PlayedFilter>(handle[KEY_PLAYED], PlayedFilter.ALL),
                    versionAge = enumOrDefault<VersionAgeFilter>(handle[KEY_VERSION_AGE], VersionAgeFilter.ALL),
                    sort = enumOrDefault<PlayerRecordSort>(handle[KEY_SORT], PlayerRecordSort.RATING_DESC),
                ),
                constantMinText = minText,
                constantMaxText = maxText,
                selectedPlate = enumOrDefault<PlateKind>(handle[KEY_PLATE_KIND], PlateKind.GENERAL),
                selectedPlateVersionId = handle[KEY_PLATE_VERSION],
                selectedPlateDifficulty = enumOrNull<Difficulty>(handle[KEY_PLATE_DIFFICULTY]),
                plateIncompleteOnly = handle[KEY_PLATE_INCOMPLETE] ?: true,
                plateSort = enumOrDefault<PlateListSort>(handle[KEY_PLATE_SORT], PlateListSort.LEVEL_DESC),
            )
        }

        private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
            name?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } }

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumOrNull<T>(name) ?: default
    }
}
