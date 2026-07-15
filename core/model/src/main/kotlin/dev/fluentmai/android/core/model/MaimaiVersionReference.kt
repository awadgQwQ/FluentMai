package dev.fluentmai.android.core.model

enum class MaimaiGeneration(val displayName: String) {
    CLASSIC("经典世代"),
    DELUXE("DX 世代"),
}

data class MaimaiPlateVersion(
    val prefixes: List<String>,
    val chartVersionStart: Int,
    val chartVersionEndExclusive: Int,
    val excludedSongIds: Set<Int> = emptySet(),
    val supportedKinds: Set<PlateKind> = VERSION_PLATE_KINDS,
) {
    init {
        require(prefixes.isNotEmpty())
        require(chartVersionStart in 1 until chartVersionEndExclusive)
    }

    fun supports(kind: PlateKind): Boolean = kind in supportedKinds

    fun displayTitle(kind: PlateKind): String =
        prefixes.joinToString(" / ") { prefix -> "$prefix${kind.displayName}" }

    fun contains(chart: ChartRecord): Boolean =
        chart.chartVersion in chartVersionStart until chartVersionEndExclusive &&
            chart.songId !in excludedSongIds
}

data class MaimaiVersionReference(
    val versionId: Int,
    val officialName: String,
    val generation: MaimaiGeneration,
    val relatedNames: List<String> = emptyList(),
    val plate: MaimaiPlateVersion? = null,
)

private val VERSION_PLATE_KINDS = setOf(
    PlateKind.GENERAL,
    PlateKind.EXTREME,
    PlateKind.GOD,
    PlateKind.MAIMAI,
)

private val TRUE_PLATE_KINDS = setOf(
    PlateKind.EXTREME,
    PlateKind.GOD,
    PlateKind.MAIMAI,
)

/**
 * One canonical table for catalog names, internal version ranges and player-facing
 * version plates. Plate ranges and exclusions mirror LXNS's structured collection
 * requirements; they deliberately do not infer membership from equality with a
 * major-version ID.
 */
val knownMaimaiVersions: List<MaimaiVersionReference> = listOf(
    MaimaiVersionReference(
        10000,
        "maimai",
        MaimaiGeneration.CLASSIC,
        relatedNames = listOf("maimai PLUS まで"),
        plate = MaimaiPlateVersion(
            prefixes = listOf("真"),
            chartVersionStart = 10000,
            chartVersionEndExclusive = 12000,
            excludedSongIds = setOf(44, 70, 146),
            supportedKinds = TRUE_PLATE_KINDS,
        ),
    ),
    MaimaiVersionReference(11000, "maimai PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(
        12000,
        "GreeN",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("超"), 12000, 13000, setOf(185, 189, 190)),
    ),
    MaimaiVersionReference(
        13000,
        "GreeN PLUS",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("檄"), 13000, 14000, setOf(341)),
    ),
    MaimaiVersionReference(
        14000,
        "ORANGE",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("橙"), 14000, 15000, setOf(281)),
    ),
    MaimaiVersionReference(
        15000,
        "ORANGE PLUS",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("暁"), 15000, 16000, setOf(419)),
    ),
    MaimaiVersionReference(
        16000,
        "PiNK",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("桃"), 16000, 17000, setOf(451, 455, 460)),
    ),
    MaimaiVersionReference(
        17000,
        "PiNK PLUS",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("櫻"), 17000, 18000, setOf(524)),
    ),
    MaimaiVersionReference(
        18000,
        "MURASAKi",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("紫"), 18000, 18500),
    ),
    MaimaiVersionReference(
        18500,
        "MURASAKi PLUS",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("菫"), 18500, 19000, setOf(853)),
    ),
    MaimaiVersionReference(
        19000,
        "MiLK",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("白"), 19000, 19500, setOf(687, 688, 712)),
    ),
    MaimaiVersionReference(
        19500,
        "MiLK PLUS",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("雪"), 19500, 19900, setOf(731)),
    ),
    MaimaiVersionReference(
        19900,
        "FiNALE",
        MaimaiGeneration.CLASSIC,
        plate = MaimaiPlateVersion(listOf("輝"), 19900, 20000, setOf(792)),
    ),
    MaimaiVersionReference(
        20000,
        "舞萌DX",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("maimai でらっくす / PLUS"),
        plate = MaimaiPlateVersion(listOf("熊", "華"), 20000, 21000, setOf(146)),
    ),
    MaimaiVersionReference(
        21000,
        "舞萌DX 2021",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("Splash / Splash PLUS"),
        plate = MaimaiPlateVersion(listOf("爽", "煌"), 21000, 22000, setOf(1213)),
    ),
    MaimaiVersionReference(
        22000,
        "舞萌DX 2022",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("UNiVERSE / UNiVERSE PLUS"),
        plate = MaimaiPlateVersion(listOf("星", "宙"), 22000, 23000, setOf(1253, 1267)),
    ),
    MaimaiVersionReference(
        23000,
        "舞萌DX 2023",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("FESTiVAL / FESTiVAL PLUS"),
        plate = MaimaiPlateVersion(listOf("祭", "祝"), 23000, 24000),
    ),
    MaimaiVersionReference(
        24000,
        "舞萌DX 2024",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("BUDDiES / BUDDiES PLUS"),
        plate = MaimaiPlateVersion(listOf("双", "宴"), 24000, 25000),
    ),
    MaimaiVersionReference(
        25000,
        "舞萌DX 2025",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("PRiSM / PRiSM PLUS"),
        plate = MaimaiPlateVersion(listOf("鏡"), 25000, 25500),
    ),
    MaimaiVersionReference(
        25500,
        "舞萌DX 2026",
        MaimaiGeneration.DELUXE,
        relatedNames = listOf("当前曲库批次"),
    ),
)

fun maimaiVersionReferenceFor(versionId: Int): MaimaiVersionReference? =
    knownMaimaiVersions.lastOrNull { it.versionId <= versionId }
        ?.takeIf { version ->
            version != knownMaimaiVersions.last() || versionId < version.versionId + 500
        }

fun maimaiPlateVersionFor(versionId: Int): MaimaiPlateVersion? =
    knownMaimaiVersions.firstOrNull { it.versionId == versionId }?.plate

fun maimaiVersionNameFor(versionId: Int): String? =
    maimaiVersionReferenceFor(versionId)?.officialName

fun buildMaimaiVersionReferences(
    catalogVersions: List<MaimaiMajorVersion>,
): List<MaimaiVersionReference> {
    val catalogNames = catalogVersions
        .asSequence()
        .filter { it.id > 0 && it.name.isNotBlank() }
        .associate { it.id to it.name.trim() }
    val knownIds = knownMaimaiVersions.mapTo(mutableSetOf()) { it.versionId }
    val known = knownMaimaiVersions.map { version ->
        version.copy(officialName = catalogNames[version.versionId] ?: version.officialName)
    }
    val catalogOnly = catalogNames
        .filterKeys { it !in knownIds }
        .map { (id, name) ->
            MaimaiVersionReference(
                versionId = id,
                officialName = name,
                generation = if (id >= 20000) MaimaiGeneration.DELUXE else MaimaiGeneration.CLASSIC,
            )
        }
    return (known + catalogOnly).sortedByDescending { it.versionId }
}
