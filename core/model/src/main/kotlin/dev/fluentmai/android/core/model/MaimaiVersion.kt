package dev.fluentmai.android.core.model

data class MaimaiMajorVersion(
    val id: Int,
    val name: String,
)

enum class MaimaiCurrentVersionSource {
    CATALOG_VERSION_TABLE,
    NAMED_CHART_METADATA,
}

data class MaimaiCurrentVersion(
    val majorVersion: MaimaiMajorVersion,
    val source: MaimaiCurrentVersionSource,
)

/**
 * Resolves the operating major version from explicit major-version metadata.
 *
 * Raw song or chart maxima are deliberately not used: remote catalogs may contain
 * unpublished content batches whose numeric version is newer than the operating game.
 */
fun resolveCurrentMaimaiVersion(
    majorVersions: List<MaimaiMajorVersion>,
    charts: List<ChartRecord>,
): MaimaiCurrentVersion? {
    majorVersions
        .asSequence()
        .filter { it.id > 0 && it.name.isNotBlank() }
        .distinctBy { it.id }
        .maxByOrNull { it.id }
        ?.let { version ->
            return MaimaiCurrentVersion(
                majorVersion = version.copy(name = version.name.trim()),
                source = MaimaiCurrentVersionSource.CATALOG_VERSION_TABLE,
            )
        }

    val namedVersions = charts
        .asSequence()
        .flatMap { chart ->
            sequenceOf(
                chart.chartVersion to chart.chartVersionName,
                chart.songVersion to chart.songVersionName,
            )
        }
        .mapNotNull { (id, name) ->
            name?.takeIf { id > 0 && it.isNotBlank() }?.let { MaimaiMajorVersion(id, it.trim()) }
        }
        .distinctBy { it.id }

    return namedVersions.maxByOrNull { it.id }?.let { version ->
        MaimaiCurrentVersion(
            majorVersion = version,
            source = MaimaiCurrentVersionSource.NAMED_CHART_METADATA,
        )
    }
}

fun normalizeMaimaiVersionName(value: String): String =
    normalizeUnicodeCompatibility(value.trim())
        .lowercase()
        .replace(Regex("[\\s._·・:：-]+"), "")

fun sameMaimaiVersionName(left: String, right: String): Boolean =
    normalizeMaimaiVersionName(left) == normalizeMaimaiVersionName(right)
