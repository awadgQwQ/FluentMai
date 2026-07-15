package dev.fluentmai.android.core.model

import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/** Stable public chart identity. Room row ids are deliberately not part of it. */
data class ChartIdentity(
    val songId: Int,
    val songType: SongType,
    val difficulty: Difficulty,
) {
    fun stableKey(): String = "$songId:${songType.name}:${difficulty.name}"

    companion object {
        fun from(chart: ChartRecord): ChartIdentity =
            ChartIdentity(chart.songId, chart.songType, chart.difficulty)

        fun from(score: ScoreRecord): ChartIdentity? =
            score.songId?.takeIf { it > 0 }?.let {
                ChartIdentity(it, score.songType, score.difficulty)
            }

        fun parseStableKey(value: String?): ChartIdentity? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 3) return null
            val songId = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val songType = SongType.entries.firstOrNull { it.name == parts[1] } ?: return null
            val difficulty = Difficulty.entries.firstOrNull { it.name == parts[2] } ?: return null
            return ChartIdentity(songId, songType, difficulty)
        }
    }
}

enum class AchievementRank(val displayName: String) {
    SSS_PLUS("SSS+"),
    SSS("SSS"),
    SS_PLUS("SS+"),
    SS("SS"),
    S_PLUS("S+"),
    S("S"),
    AAA("AAA"),
    AA("AA"),
    A("A"),
    BBB("BBB"),
    BB("BB"),
    B("B"),
    C("C"),
    D("D");

    companion object {
        fun fromAchievement(value: Double): AchievementRank =
            when {
                value >= 100.5 -> SSS_PLUS
                value >= 100.0 -> SSS
                value >= 99.5 -> SS_PLUS
                value >= 99.0 -> SS
                value >= 98.0 -> S_PLUS
                value >= 97.0 -> S
                value >= 94.0 -> AAA
                value >= 90.0 -> AA
                value >= 80.0 -> A
                value >= 75.0 -> BBB
                value >= 70.0 -> BB
                value >= 60.0 -> B
                value >= 50.0 -> C
                else -> D
            }
    }
}

enum class FullComboStatus(val displayName: String) {
    FC("FC"),
    FC_PLUS("FC+"),
    AP("AP"),
    AP_PLUS("AP+"),
    UNKNOWN("未知");

    val satisfiesFullCombo: Boolean
        get() = this != UNKNOWN
    val satisfiesAllPerfect: Boolean
        get() = this == AP || this == AP_PLUS

    companion object {
        fun fromWireValue(value: String?): FullComboStatus? =
            when (normalizeFlag(value)) {
                "fc" -> FC
                "fcp", "fcplus" -> FC_PLUS
                "ap" -> AP
                "app", "applus" -> AP_PLUS
                "" -> null
                else -> UNKNOWN
            }
    }
}

enum class FullSyncStatus(val displayName: String) {
    SYNC("SYNC"),
    FS("FS"),
    FS_PLUS("FS+"),
    FSD("FSD"),
    FSD_PLUS("FSD+"),
    UNKNOWN("未知");

    val satisfiesFullSyncDx: Boolean
        get() = this == FSD || this == FSD_PLUS

    companion object {
        fun fromWireValue(value: String?): FullSyncStatus? =
            when (normalizeFlag(value)) {
                "sync" -> SYNC
                "fs" -> FS
                "fsp", "fsplus" -> FS_PLUS
                "fsd", "fdx" -> FSD
                "fsdp", "fdxp", "fsdplus", "fdxplus" -> FSD_PLUS
                "" -> null
                else -> UNKNOWN
            }
    }
}

data class PlayerChartRecord(
    val identity: ChartIdentity,
    val chart: ChartRecord,
    val score: ScoreRecord?,
    val rating: Int?,
) {
    val rank: AchievementRank?
        get() = score?.let { AchievementRank.fromAchievement(it.achievement) }
    val fullComboStatus: FullComboStatus?
        get() = FullComboStatus.fromWireValue(score?.fc)
    val fullSyncStatus: FullSyncStatus?
        get() = FullSyncStatus.fromWireValue(score?.fs)
}

data class PlayerRecordCatalog(
    val records: List<PlayerChartRecord>,
    val unmatchedScoreCount: Int,
)

data class PlayerRecordStats(
    val totalCharts: Int,
    val playedCharts: Int,
    val unplayedCharts: Int,
    val unmatchedScores: Int,
    val rankCounts: Map<AchievementRank, Int>,
    val fullComboCounts: Map<FullComboStatus, Int>,
    val fullSyncCounts: Map<FullSyncStatus, Int>,
)

/**
 * Matches persisted scores to public chart identities without ever using a Room row id.
 * Title fallback is deliberately limited to a single public identity so same-title songs
 * cannot silently borrow each other's chart metadata.
 */
fun matchChartsForScores(
    charts: List<ChartRecord>,
    scores: List<ScoreRecord>,
): Map<String, ChartRecord> {
    val chartByIdentity = charts.distinctBy(ChartIdentity::from).associateBy(ChartIdentity::from)
    val uniqueChartByFallback = charts
        .groupBy { chart ->
            ScoreFallbackKey(normalizePlayerRecordText(chart.title), chart.songType, chart.levelIndex)
        }
        .mapValues { (_, matches) -> matches.distinctBy(ChartIdentity::from) }
        .filterValues { it.size == 1 }
        .mapValues { (_, matches) -> matches.single() }
    return scores.mapNotNull { score ->
        val direct = ChartIdentity.from(score)?.let(chartByIdentity::get)
        val fallback = uniqueChartByFallback[
            ScoreFallbackKey(normalizePlayerRecordText(score.title), score.songType, score.levelIndex)
        ]
        (direct ?: fallback)?.let { score.id to it }
    }.toMap()
}

fun buildPlayerRecordCatalog(
    charts: List<ChartRecord>,
    scores: List<ScoreRecord>,
): PlayerRecordCatalog {
    val bestByIdentity = scores
        .mapNotNull { score -> ChartIdentity.from(score)?.let { it to score } }
        .bestScorePairs()
    val bestByTitle = scores.bestScoresBy { score ->
        ScoreFallbackKey(normalizePlayerRecordText(score.title), score.songType, score.levelIndex)
    }
    val chartIdentities = charts.map(ChartIdentity::from).toSet()
    val chartIdentitiesByFallbackKey = charts.groupBy { chart ->
        ScoreFallbackKey(normalizePlayerRecordText(chart.title), chart.songType, chart.levelIndex)
    }.mapValues { (_, matchingCharts) -> matchingCharts.map(ChartIdentity::from).distinct() }
    val uniqueChartFallbackKeys = chartIdentitiesByFallbackKey
        .filterValues { identities -> identities.size == 1 }
        .keys
    val chartFallbackKeys = chartIdentitiesByFallbackKey.keys
    val records = charts
        .distinctBy(ChartIdentity::from)
        .map { chart ->
            val identity = ChartIdentity.from(chart)
            val fallback = ScoreFallbackKey(
                normalizePlayerRecordText(chart.title),
                chart.songType,
                chart.levelIndex,
            )
            val score = bestByIdentity[identity]
                ?: bestByTitle[fallback]?.takeIf { fallback in uniqueChartFallbackKeys }
            PlayerChartRecord(
                identity = identity,
                chart = chart,
                score = score,
                rating = score?.let { chart.levelValue?.let { constant -> calculateDxRating(constant, score.achievement, score.fc) } },
            )
        }
    val unmatchedScoreCount = scores.count { score ->
        val identityMatched = ChartIdentity.from(score)?.let { it in chartIdentities } == true
        val fallback = ScoreFallbackKey(
            normalizePlayerRecordText(score.title),
            score.songType,
            score.levelIndex,
        )
        val fallbackMatched = fallback in chartFallbackKeys && fallback in uniqueChartFallbackKeys
        !identityMatched && !fallbackMatched
    }
    return PlayerRecordCatalog(records, unmatchedScoreCount)
}

fun PlayerRecordCatalog.stats(records: List<PlayerChartRecord> = this.records): PlayerRecordStats =
    PlayerRecordStats(
        totalCharts = records.size,
        playedCharts = records.count { it.score != null },
        unplayedCharts = records.count { it.score == null },
        unmatchedScores = unmatchedScoreCount,
        rankCounts = records.mapNotNull { it.rank }.groupingBy { it }.eachCount(),
        fullComboCounts = records.mapNotNull { it.fullComboStatus }.groupingBy { it }.eachCount(),
        fullSyncCounts = records.mapNotNull { it.fullSyncStatus }.groupingBy { it }.eachCount(),
    )

enum class VersionAgeFilter { ALL, CURRENT, OLD }

enum class PlateKind(val displayName: String) {
    GENERAL("将"),
    EXTREME("极"),
    GOD("神"),
    MAIMAI("舞舞"),
    CONQUEROR("霸者"),
}

data class PlateBlocker(
    val record: PlayerChartRecord,
    val currentValue: String,
    val requirementGap: String,
)

data class PlateProgress(
    val kind: PlateKind,
    val versionId: Int?,
    val versionName: String,
    val requiredCount: Int,
    val completedCount: Int,
    val blockers: List<PlateBlocker>,
    val eligibleRecords: List<PlayerChartRecord>,
    val dataSufficient: Boolean,
    val dataMessage: String? = null,
    val plateName: String = if (kind == PlateKind.CONQUEROR) "覇者" else "$versionName${kind.displayName}",
) {
    val remainingCount: Int = (requiredCount - completedCount).coerceAtLeast(0)
    val completionFraction: Double = if (requiredCount == 0) 0.0 else completedCount.toDouble() / requiredCount
    val isComplete: Boolean = dataSufficient && requiredCount > 0 && remainingCount == 0
}

/**
 * Rules follow SEGA's official 2020-01-15 plate announcement.
 * Version plates use BASIC through MASTER; Re:MASTER is excluded. 覇者 uses every
 * STANDARD chart from BASIC through Re:MASTER and the official 80% clear threshold.
 */
fun calculatePlateProgress(
    records: List<PlayerChartRecord>,
    kind: PlateKind,
    versionId: Int?,
    versionName: String?,
): PlateProgress {
    if (kind != PlateKind.CONQUEROR && versionId == null) {
        return PlateProgress(kind, null, versionName.orEmpty(), 0, 0, emptyList(), emptyList(), false, "缺少版本信息")
    }
    val plateVersion = versionId?.let(::maimaiPlateVersionFor)
    if (kind != PlateKind.CONQUEROR && plateVersion == null) {
        return PlateProgress(
            kind,
            versionId,
            versionName.orEmpty(),
            0,
            0,
            emptyList(),
            emptyList(),
            false,
            "该曲库版本还没有可核验的版本牌要求",
        )
    }
    if (kind != PlateKind.CONQUEROR && plateVersion?.supports(kind) != true) {
        return PlateProgress(
            kind,
            versionId,
            versionName.orEmpty(),
            0,
            0,
            emptyList(),
            emptyList(),
            false,
            "该版本没有${kind.displayName}牌要求",
        )
    }
    val eligible = records.filter { record ->
        when (kind) {
            PlateKind.CONQUEROR -> record.chart.songType == SongType.STANDARD
            else -> plateVersion?.contains(record.chart) == true && record.chart.difficulty != Difficulty.RE_MASTER
        }
    }
    if (eligible.isEmpty()) {
        return PlateProgress(
            kind,
            versionId,
            versionName.orEmpty(),
            0,
            0,
            emptyList(),
            emptyList(),
            false,
            if (kind == PlateKind.CONQUEROR) "曲库中没有可核验的标准谱面" else "该版本没有可核验的 BASIC～MASTER 谱面",
        )
    }
    val blockers = eligible.mapNotNull { record -> record.plateBlocker(kind) }
    return PlateProgress(
        kind = kind,
        versionId = if (kind == PlateKind.CONQUEROR) null else versionId,
        versionName = if (kind == PlateKind.CONQUEROR) "全标准谱面" else versionName.orEmpty(),
        requiredCount = eligible.size,
        completedCount = eligible.size - blockers.size,
        blockers = blockers,
        eligibleRecords = eligible,
        dataSufficient = true,
        plateName = if (kind == PlateKind.CONQUEROR) {
            "覇者"
        } else {
            checkNotNull(plateVersion).displayTitle(kind)
        },
    )
}

private fun PlayerChartRecord.plateBlocker(kind: PlateKind): PlateBlocker? {
    val score = score
    return when (kind) {
        PlateKind.GENERAL -> {
            val achievement = score?.achievement ?: 0.0
            if (achievement >= 100.0) null else PlateBlocker(
                this,
                score?.let { "${it.achievement.fixedFourDecimals()}%" } ?: "未游玩",
                "达成率还差 ${(100.0 - achievement).coerceAtLeast(0.0).fixedFourDecimals()}%",
            )
        }
        PlateKind.EXTREME -> if (fullComboStatus?.satisfiesFullCombo == true) null else PlateBlocker(
            this,
            fullComboStatus?.displayName ?: "未达成",
            "需要 FC 或更高状态",
        )
        PlateKind.GOD -> if (fullComboStatus?.satisfiesAllPerfect == true) null else PlateBlocker(
            this,
            fullComboStatus?.displayName ?: "未达成",
            "需要 AP 或 AP+",
        )
        PlateKind.MAIMAI -> if (fullSyncStatus?.satisfiesFullSyncDx == true) null else PlateBlocker(
            this,
            fullSyncStatus?.displayName ?: "未达成",
            "需要 FSD 或 FSD+",
        )
        PlateKind.CONQUEROR -> {
            val achievement = score?.achievement ?: 0.0
            if (achievement >= 80.0) null else PlateBlocker(
                this,
                score?.let { "${it.achievement.fixedFourDecimals()}%" } ?: "未游玩",
                "距 CLEAR 还差 ${(80.0 - achievement).coerceAtLeast(0.0).fixedFourDecimals()}%",
            )
        }
    }
}

private fun Double.fixedFourDecimals(): String {
    val scaled = (this * 10_000.0).roundToLong()
    val sign = if (scaled < 0L) "-" else ""
    val magnitude = scaled.absoluteValue
    return "$sign${magnitude / 10_000}.${(magnitude % 10_000).toString().padStart(4, '0')}"
}

fun PlayerChartRecord.isEligibleForPlate(kind: PlateKind): Boolean =
    when (kind) {
        PlateKind.CONQUEROR -> chart.songType == SongType.STANDARD
        else -> chart.difficulty != Difficulty.RE_MASTER
    }

fun PlayerChartRecord.meetsPlateRequirement(kind: PlateKind): Boolean = plateBlocker(kind) == null

private data class ScoreFallbackKey(
    val title: String,
    val songType: SongType,
    val levelIndex: Int,
)

private fun normalizePlayerRecordText(value: String): String =
    normalizeUnicodeCompatibility(value.trim())
        .lowercase()
        .replace(Regex("[\\s._·・:：!！?？'\"“”‘’()（）\\[\\]【】/\\\\-]+"), "")

private fun normalizeFlag(value: String?): String =
    value.orEmpty().trim().lowercase()
        .replace("+", "plus")
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")

private fun <K> List<ScoreRecord>.bestScoresBy(keySelector: (ScoreRecord) -> K): Map<K, ScoreRecord> =
    groupBy(keySelector).mapValues { (_, values) -> values.bestScore() }

private fun <K> List<Pair<K, ScoreRecord>>.bestScorePairs(): Map<K, ScoreRecord> =
    groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.bestScore() }

private fun List<ScoreRecord>.bestScore(): ScoreRecord =
    maxWithOrNull(compareBy<ScoreRecord> { it.achievement }.thenBy { it.dxScore ?: -1 }) ?: first()
