package dev.fluentmai.android.feature.scores

import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ChartIdentity
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.FullComboStatus
import dev.fluentmai.android.core.model.FullSyncStatus
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongAliasCatalog
import dev.fluentmai.android.core.model.SongType
import dev.fluentmai.android.core.model.buildPlayerRecordCatalog

internal data class ChartQueryFilters(
    val searchQuery: String = "",
    val levelQuery: String = "",
    val constantMin: Double? = null,
    val constantMax: Double? = null,
    val difficulty: Difficulty? = null,
    val genre: ChartGenreFilter = ChartGenreFilter.All,
    val version: ChartVersionFilter = ChartVersionFilter.All,
    val status: ChartStatusFilter = ChartStatusFilter.All,
    val songType: SongType? = null,
    val achievementMin: Double? = null,
    val achievementMax: Double? = null,
    val fullCombo: FullComboStatus? = null,
    val fullSync: FullSyncStatus? = null,
    val sort: ChartSort = ChartSort.ConstantDesc,
)

internal data class ChartQueryItem(
    val chart: ChartRecord,
    val score: ScoreRecord?,
)

internal data class ChartQueryResult(
    val items: List<ChartQueryItem> = emptyList(),
    val matchingCount: Int = 0,
)

internal class ChartQueryEngine private constructor(
    private val entries: List<IndexedChart>,
) {
    fun query(
        filters: ChartQueryFilters,
        currentVersion: Int,
        limit: Int = 500,
    ): ChartQueryResult {
        val normalizedQuery = normalizeQuery(filters.searchQuery)
        val designerAliases = designerAliasesFor(normalizedQuery)
        val matched = entries.filter { entry ->
            (filters.difficulty == null || entry.chart.difficulty == filters.difficulty) &&
                filters.genre.matches(entry.normalizedGenre) &&
                filters.version.matches(entry.chart, currentVersion) &&
                entry.chart.matchesLevel(filters.levelQuery) &&
                filters.matchesConstant(entry.chart.levelValue) &&
                entry.matchesSearch(normalizedQuery, designerAliases) &&
                filters.status.matches(entry.score) &&
                (filters.songType == null || entry.chart.songType == filters.songType) &&
                filters.matchesScore(entry.score)
        }
        val items = matched
            .sortedWith(filters.sort.comparator())
            .take(limit)
            .map { entry -> ChartQueryItem(entry.chart, entry.score) }
        return ChartQueryResult(items = items, matchingCount = matched.size)
    }

    companion object {
        fun create(
            charts: List<ChartRecord>,
            scores: List<ScoreRecord>,
            aliases: SongAliasCatalog = SongAliasCatalog.Empty,
        ): ChartQueryEngine {
            val scoresByIdentity = buildPlayerRecordCatalog(charts, scores).records
                .associate { it.identity to it.score }
            return ChartQueryEngine(
                entries = charts.map { chart ->
                    IndexedChart(
                        chart = chart,
                        score = scoresByIdentity[ChartIdentity.from(chart)],
                        normalizedGenre = normalizeQuery(chart.genre),
                        normalizedTitle = normalizeQuery(chart.title),
                        searchableFields = listOf(
                            chart.title,
                            chart.artist,
                            chart.genre,
                            chart.noteDesigner,
                            chart.songVersionName.orEmpty(),
                            chart.chartVersionName.orEmpty(),
                            chart.bpm?.toString().orEmpty(),
                            chart.songId.toString(),
                            "${chart.songId}-${chart.songType.name}-${chart.difficulty.name}",
                            chart.difficulty.name,
                            chart.songType.name,
                        ).plus(aliases.aliasesFor(chart.songId)).map(::normalizeQuery),
                    )
                },
            )
        }
    }
}

private fun ChartQueryFilters.matchesConstant(value: Double?): Boolean =
    (constantMin == null || (value != null && value >= constantMin)) &&
        (constantMax == null || (value != null && value <= constantMax))

private fun ChartQueryFilters.matchesScore(score: ScoreRecord?): Boolean {
    if (achievementMin != null && (score == null || score.achievement < achievementMin)) return false
    if (achievementMax != null && (score == null || score.achievement > achievementMax)) return false
    if (fullCombo != null && FullComboStatus.fromWireValue(score?.fc) != fullCombo) return false
    if (fullSync != null && FullSyncStatus.fromWireValue(score?.fs) != fullSync) return false
    return true
}

internal data class IndexedChart(
    val chart: ChartRecord,
    val score: ScoreRecord?,
    val normalizedGenre: String,
    val normalizedTitle: String,
    val searchableFields: List<String>,
) {
    fun matchesSearch(normalizedQuery: String, designerAliases: Set<String>): Boolean =
        normalizedQuery.isBlank() ||
            searchableFields.any { it.contains(normalizedQuery) } ||
            designerAliases.any { alias -> searchableFields.any { it.contains(alias) } }
}

internal enum class ChartStatusFilter(val label: String) {
    All("全部"),
    Played("已游玩"),
    Missing("未游玩");

    fun matches(score: ScoreRecord?): Boolean =
        when (this) {
            All -> true
            Played -> score != null
            Missing -> score == null
        }
}

internal enum class ChartGenreFilter(val label: String) {
    All("全部分区"),
    Maimai("舞萌区"),
    OngekiChunithm("中二/音击区"),
    VocaloidNiconico("VOCALOID & NICONICO"),
    Touhou("东方区"),
    GameVariety("GAME & VARIETY"),
    PopsAnime("POPS & ANIME"),
    Utage("宴会场");

    fun matches(normalizedGenre: String): Boolean =
        when (this) {
            All -> true
            Maimai -> normalizedGenre == "maimai"
            OngekiChunithm -> normalizedGenre.contains("オンゲキ") ||
                normalizedGenre.contains("chunithm") ||
                normalizedGenre.contains("中二") ||
                normalizedGenre.contains("音击")
            VocaloidNiconico -> normalizedGenre.contains("vocaloid") ||
                normalizedGenre.contains("niconico") ||
                normalizedGenre.contains("ボーカロイド")
            Touhou -> normalizedGenre.contains("東方") || normalizedGenre.contains("东方")
            GameVariety -> normalizedGenre.contains("ゲーム") ||
                normalizedGenre.contains("バラエティ") ||
                normalizedGenre.contains("game") ||
                normalizedGenre.contains("variety")
            PopsAnime -> normalizedGenre.contains("pops") ||
                normalizedGenre.contains("アニメ") ||
                normalizedGenre.contains("anime")
            Utage -> normalizedGenre.contains("宴会") || normalizedGenre.contains("utage")
        }
}

internal enum class ChartVersionFilter(val label: String) {
    All("全部版本"),
    Current("当前版本"),
    Dx2025("DX 2025"),
    Dx2024("DX 2024"),
    Dx2023("DX 2023"),
    Finale("FiNALE"),
    Classic("旧框");

    fun matches(chart: ChartRecord, currentVersion: Int): Boolean =
        when (this) {
            All -> true
            Current -> currentVersion > 0 && chart.songVersion == currentVersion
            Dx2025 -> chart.songVersion in 25000 until 25500
            Dx2024 -> chart.songVersion in 24000 until 25000
            Dx2023 -> chart.songVersion in 23000 until 24000
            Finale -> chart.songVersion in 19900 until 20000 || chart.chartVersion in 19900 until 20000
            Classic -> chart.songVersion in 1 until 20000
        }
}

internal enum class ChartSort(val label: String) {
    ConstantDesc("定数降序"),
    ConstantAsc("定数升序"),
    VersionDesc("上线新到旧"),
    VersionAsc("上线旧到新"),
    AchievementAsc("成绩升序"),
    AchievementDesc("成绩降序"),
    TitleAsc("曲名升序"),
    TitleDesc("曲名降序");

    fun comparator(): Comparator<IndexedChart> {
        val primary = when (this) {
            ConstantDesc -> compareByDescending<IndexedChart> { it.chart.levelValue ?: -1.0 }
                .thenByDescending { it.chart.levelIndex }
                .thenBy { it.normalizedTitle }
            ConstantAsc -> compareBy<IndexedChart> { it.chart.levelValue ?: 999.0 }
                .thenBy { it.chart.levelIndex }
                .thenBy { it.normalizedTitle }
            VersionDesc -> compareByDescending<IndexedChart> { it.chart.chartVersion }
                .thenByDescending { it.chart.songVersion }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
            VersionAsc -> compareBy<IndexedChart> { it.chart.chartVersion }
                .thenBy { it.chart.songVersion }
                .thenBy { it.chart.levelValue ?: 999.0 }
            AchievementAsc -> compareBy<IndexedChart> { it.score?.achievement ?: 999.0 }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
            AchievementDesc -> compareByDescending<IndexedChart> { it.score?.achievement ?: -1.0 }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
            TitleAsc -> compareBy<IndexedChart> { it.normalizedTitle }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
            TitleDesc -> compareByDescending<IndexedChart> { it.normalizedTitle }
                .thenByDescending { it.chart.levelValue ?: -1.0 }
        }
        return primary
            .thenBy { it.chart.songId }
            .thenBy { it.chart.songType.ordinal }
            .thenBy { it.chart.levelIndex }
    }
}

private fun ChartRecord.matchesLevel(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return true
    val numeric = trimmed.toDoubleOrNull()
    if (numeric != null && trimmed.contains(".")) {
        return levelValue?.let { kotlin.math.abs(it - numeric) < 0.0001 } == true
    }

    val normalized = normalizeQuery(trimmed)
    val isPlusLevel = normalized.endsWith("+")
    val baseLevel = normalized.removeSuffix("+").toIntOrNull()
    if (baseLevel != null) {
        if (level.equals(trimmed, ignoreCase = true)) return true
        val value = levelValue ?: return false
        return if (isPlusLevel) {
            value >= baseLevel + 0.6 - 0.0001 && value <= baseLevel + 0.9 + 0.0001
        } else {
            value >= baseLevel.toDouble() - 0.0001 && value <= baseLevel + 0.5 + 0.0001
        }
    }

    return level.equals(trimmed, ignoreCase = true)
}

private fun designerAliasesFor(query: String): Set<String> =
    when {
        query.contains("沙发太") || query.contains("沙發太") ->
            setOf("サファ太").map(::normalizeQuery).toSet()
        query.contains("哈皮") ->
            setOf("はっぴー").map(::normalizeQuery).toSet()
        query.contains("73") ||
            query.contains("shichimi") ||
            query.contains("シチミ") ||
            query.contains("七味") ->
            setOf(
                "7.3ghz",
                "シチミッピー",
                "シチミヘルツ",
                "しちみへるつ",
                "超七味星人",
            ).map(::normalizeQuery).toSet()
        else -> emptySet()
    }
