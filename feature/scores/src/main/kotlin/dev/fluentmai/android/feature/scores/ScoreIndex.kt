package dev.fluentmai.android.feature.scores

import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType

internal data class ScoreKey(
    val title: String,
    val songType: SongType,
    val levelIndex: Int,
) {
    companion object {
        fun fromScore(score: ScoreRecord): ScoreKey =
            ScoreKey(normalizeQuery(score.title), score.songType, score.levelIndex)

        fun fromChart(chart: ChartRecord): ScoreKey =
            ScoreKey(normalizeQuery(chart.title), chart.songType, chart.levelIndex)
    }
}

private data class ScoreSongIdKey(
    val songId: Int,
    val songType: SongType,
    val levelIndex: Int,
)

private data class ScoreTitleDifficultyKey(
    val title: String,
    val songType: SongType,
    val levelIndex: Int,
)

internal class ScoreIndex private constructor(
    private val exact: Map<ScoreKey, ScoreRecord>,
    private val bySongId: Map<ScoreSongIdKey, ScoreRecord>,
    private val byTitleDifficulty: Map<ScoreTitleDifficultyKey, ScoreRecord>,
) {
    fun scoreFor(chart: ChartRecord): ScoreRecord? =
        exact[ScoreKey.fromChart(chart)]
            ?: bySongId[ScoreSongIdKey(chart.songId, chart.songType, chart.levelIndex)]
            ?: byTitleDifficulty[ScoreTitleDifficultyKey(normalizeQuery(chart.title), chart.songType, chart.levelIndex)]

    companion object {
        fun from(scores: List<ScoreRecord>): ScoreIndex =
            ScoreIndex(
                exact = scores.bestBy { ScoreKey.fromScore(it) },
                bySongId = scores
                    .mapNotNull { score -> score.songId?.let { ScoreSongIdKey(it, score.songType, score.levelIndex) to score } }
                    .bestPairs(),
                byTitleDifficulty = scores.bestBy {
                    ScoreTitleDifficultyKey(normalizeQuery(it.title), it.songType, it.levelIndex)
                },
            )

        private fun <K> List<ScoreRecord>.bestBy(keySelector: (ScoreRecord) -> K): Map<K, ScoreRecord> =
            groupBy(keySelector).mapValues { (_, records) -> records.bestScore() }

        private fun <K> List<Pair<K, ScoreRecord>>.bestPairs(): Map<K, ScoreRecord> =
            groupBy({ it.first }, { it.second }).mapValues { (_, records) -> records.bestScore() }

        private fun List<ScoreRecord>.bestScore(): ScoreRecord =
            maxWithOrNull(
                compareBy<ScoreRecord> { it.achievement }
                    .thenBy { it.dxScore ?: -1 },
            ) ?: first()
    }
}

internal fun scoreForChartForTest(scores: List<ScoreRecord>, chart: ChartRecord): ScoreRecord? =
    ScoreIndex.from(scores).scoreFor(chart)
