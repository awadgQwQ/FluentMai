package dev.fluentmai.android.feature.tools

import dev.fluentmai.android.core.model.KaleidScopeCatalog

interface KaleidScopeRepository {
    fun currentCatalog(): KaleidScopeCatalog
}

object ReviewedKaleidScopeRepository : KaleidScopeRepository {
    override fun currentCatalog(): KaleidScopeCatalog =
        KaleidScopeCatalog.Unavailable(
            reason = "SEGA 官方页面目前只说明模式、三曲挑战、生命值与条件随天数放宽的机制，" +
                "没有提供可审查、可稳定更新的结构化门曲和逐门解锁条件。",
            reviewedSources = listOf(
                "https://maimai.sega.com/play/newfunction2/",
            ),
        )
}
