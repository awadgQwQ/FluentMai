package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

class WahlapFixtureParser {
    fun parse(html: String, difficulty: Difficulty): List<ParsedScoreRecord> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        val cards = document.select(SCORE_FORM_SELECTOR)

        return cards.mapNotNull { card ->
            val title = extractTitle(card)
            val achievement = extractAchievement(card)
            val dxScore = extractDxScore(card)
            val fc = extractFc(card)
            val fs = extractFs(card)
            val outerHtml = card.outerHtml()

            ParsedScoreRecord(
                title = title,
                difficulty = difficulty,
                level = PLACEHOLDER_LEVEL,
                levelIndex = difficulty.levelIndex,
                achievement = achievement,
                dxScore = dxScore,
                fc = fc,
                fs = fs,
                rawFingerprint = Hashing.sha256(outerHtml),
            )
        }
    }

    private fun extractTitle(card: org.jsoup.nodes.Element): String? {
        val elements = card.select(TITLE_SELECTOR)
        if (elements.isEmpty()) return null
        return Entities.unescape(elements.html()).trim().ifBlank { null }
    }

    private fun extractAchievement(card: org.jsoup.nodes.Element): Double? {
        val text = card.select(ACHIEVEMENT_SELECTOR).text().trim()
        return text.replace("%", "").toDoubleOrNull()
    }

    private fun extractDxScore(card: org.jsoup.nodes.Element): Int? {
        val text = card.select(DX_SCORE_SELECTOR).text().trim()
        return DX_SCORE_REGEX.find(text)?.value?.replace(",", "")?.toIntOrNull()
    }

    private fun extractFc(card: org.jsoup.nodes.Element): String? {
        return card.select(SCORE_STATUS_SELECTOR)
            .mapNotNull { img -> extractClearType(img.attr("src")) }
            .firstOrNull { it in FC_VALUES }
    }

    private fun extractFs(card: org.jsoup.nodes.Element): String? {
        return card.select(SCORE_STATUS_SELECTOR)
            .mapNotNull { img -> extractClearType(img.attr("src")) }
            .firstOrNull { it in FS_VALUES }
    }

    private fun extractClearType(src: String): String? {
        val match = CLEAR_TYPE_REGEX.find(src) ?: return null
        return match.groupValues[1]
    }

    companion object {
        private val SCORE_FORM_SELECTOR = "form[action*=\"musicDetail\"]"
        private val TITLE_SELECTOR = ".music_name_block"
        private val ACHIEVEMENT_SELECTOR = ".music_score_block.w_112.t_r.f_l.f_12"
        private val DX_SCORE_SELECTOR = ".music_score_block.w_190.t_r.f_l.f_12"
        private val SCORE_STATUS_SELECTOR = "img.h_30.f_r, img[src*=music_icon_]"
        private val DX_SCORE_REGEX = Regex("""\d{1,3}(,\d{3})*""")
        private val CLEAR_TYPE_REGEX = Regex(".*music_icon_(.*?)\\.png.*")

        private const val PLACEHOLDER_LEVEL = "?"
        private val FC_VALUES = setOf("fc", "fcp", "ap", "app")
        private val FS_VALUES = setOf("sync", "fs", "fsp", "fsd", "fsdp")
    }
}
