package dev.fluentmai.android.core.model

data class KaleidScopeGate(
    val id: String,
    val name: String,
    val songs: List<String>,
    val unlockCondition: String,
    val sourceUrl: String,
    val sourceUpdatedAtEpochMillis: Long?,
)

sealed interface KaleidScopeCatalog {
    data class Available(
        val gates: List<KaleidScopeGate>,
        val fetchedAtEpochMillis: Long,
    ) : KaleidScopeCatalog

    data class Unavailable(
        val reason: String,
        val reviewedSources: List<String>,
    ) : KaleidScopeCatalog
}
