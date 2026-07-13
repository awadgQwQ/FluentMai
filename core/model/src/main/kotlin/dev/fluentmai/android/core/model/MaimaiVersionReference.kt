package dev.fluentmai.android.core.model

enum class MaimaiGeneration(val displayName: String) {
    CLASSIC("经典世代"),
    DELUXE("DX 世代"),
}

data class MaimaiVersionReference(
    val versionId: Int,
    val officialName: String,
    val generation: MaimaiGeneration,
)

val knownMaimaiVersions: List<MaimaiVersionReference> = listOf(
    MaimaiVersionReference(10000, "maimai", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(11000, "maimai PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(12000, "GreeN", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(13000, "GreeN PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(14000, "ORANGE", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(15000, "ORANGE PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(16000, "PiNK", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(17000, "PiNK PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(18000, "MURASAKi", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(18500, "MURASAKi PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(19000, "MiLK", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(19500, "MiLK PLUS", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(19900, "FiNALE", MaimaiGeneration.CLASSIC),
    MaimaiVersionReference(20000, "舞萌DX", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(21000, "舞萌DX 2021", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(22000, "舞萌DX 2022", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(23000, "舞萌DX 2023", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(24000, "舞萌DX 2024", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(25000, "舞萌DX 2025", MaimaiGeneration.DELUXE),
    MaimaiVersionReference(25500, "舞萌DX 2026", MaimaiGeneration.DELUXE),
)

fun maimaiVersionReferenceFor(versionId: Int): MaimaiVersionReference? =
    knownMaimaiVersions.lastOrNull { it.versionId <= versionId }

fun maimaiVersionNameFor(versionId: Int): String? =
    maimaiVersionReferenceFor(versionId)?.officialName

fun buildMaimaiVersionReferences(
    catalogVersions: List<MaimaiMajorVersion>,
): List<MaimaiVersionReference> {
    if (catalogVersions.isEmpty()) return knownMaimaiVersions
    return catalogVersions
        .asSequence()
        .filter { it.id > 0 && it.name.isNotBlank() }
        .distinctBy { it.id }
        .map { version ->
            MaimaiVersionReference(
                versionId = version.id,
                officialName = version.name.trim(),
                generation = if (version.id >= 20000) MaimaiGeneration.DELUXE else MaimaiGeneration.CLASSIC,
            )
        }
        .sortedByDescending { it.versionId }
        .toList()
}
