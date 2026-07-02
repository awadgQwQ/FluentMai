package dev.fluentmai.android.core.model

enum class SongType(
    val exportName: String,
    val divingFishName: String,
) {
    STANDARD("standard", "SD"),
    DX("dx", "DX");

    companion object {
        fun fromWireName(value: String?): SongType =
            when (value?.trim()?.uppercase()?.replace("-", "_")) {
                "DX", "DELUXE" -> DX
                "STANDARD", "STD", "SD" -> STANDARD
                else -> STANDARD
            }
    }
}
