package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

class WahlapFixtureParser(
    private val songCatalog: MaimaiSongCatalog = MaimaiSongCatalog.Empty,
    private val songIdResolver: (String) -> Int? = songCatalog::idForTitle,
) {
    fun parse(html: String, difficulty: Difficulty): List<ParsedScoreRecord> {
        return parseCards(html, fixedDifficulty = difficulty)
    }

    fun parseMixedDifficultyPage(html: String): List<ParsedScoreRecord> {
        return parseCards(html, fixedDifficulty = null)
    }

    private fun parseCards(html: String, fixedDifficulty: Difficulty?): List<ParsedScoreRecord> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        val cards = document.select(SCORE_FORM_SELECTOR)

        return cards.mapNotNull { card ->
            val title = extractTitle(card)
            val levelIndex = fixedDifficulty?.levelIndex ?: extractLevelIndex(card)
            val difficulty = fixedDifficulty ?: levelIndex?.let(Difficulty::fromLevelIndex)
            val detectedSongType = extractSongType(card)
            val pageLevel = extractLevel(card)
            val songType = title
                ?.takeIf { levelIndex != null }
                ?.let { songCatalog.resolveSongType(it, levelIndex!!, detectedSongType, pageLevel) }
                ?: detectedSongType
            val level = title
                ?.takeIf { levelIndex != null }
                ?.let { songCatalog.levelForTitle(it, levelIndex!!, songType) }
                ?: pageLevel
                ?: PLACEHOLDER_LEVEL
            val achievement = extractAchievement(card)
            val dxScore = extractDxScore(card)
            val fc = extractFc(card)
            val fs = extractFs(card)
            val outerHtml = card.outerHtml()

            ParsedScoreRecord(
                title = title,
                songId = title?.let(songIdResolver),
                songType = songType,
                difficulty = difficulty,
                level = level,
                levelIndex = levelIndex,
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
        return card.select(ACHIEVEMENT_SELECTOR)
            .asSequence()
            .map { it.text().trim() }
            .mapNotNull { text ->
                ACHIEVEMENT_REGEX.find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toDoubleOrNull()
                    ?: text.replace("%", "").toDoubleOrNull()
            }
            .firstOrNull()
    }

    private fun extractDxScore(card: org.jsoup.nodes.Element): Int? {
        val text = card.select(DX_SCORE_SELECTOR).text().trim()
        return DX_SCORE_REGEX.find(text)?.value?.replace(",", "")?.toIntOrNull()
    }

    private fun extractLevel(card: org.jsoup.nodes.Element): String? {
        val selectorText = card.select(LEVEL_SELECTOR).text()
        BARE_LEVEL_REGEX.matchEntire(selectorText.trim())?.let { return it.value }
        val text = listOf(selectorText, card.text()).joinToString(" ")
        return LEVEL_REGEX.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractLevelIndex(card: org.jsoup.nodes.Element): Int? {
        val hiddenValue = card.select(DIFFICULTY_INPUT_SELECTOR)
            .asSequence()
            .mapNotNull { it.attr("value").trim().toIntOrNull() }
            .firstOrNull { it in 0..4 }
        if (hiddenValue != null) return hiddenValue

        val imageValue = card.select("img[src*=diff_]")
            .asSequence()
            .mapNotNull { DIFFICULTY_IMAGE_REGEX.find(it.attr("src"))?.groupValues?.getOrNull(1) }
            .mapNotNull(::difficultyFromToken)
            .firstOrNull()
        if (imageValue != null) return imageValue.levelIndex

        val signal = buildString {
            append(card.className())
            append(' ')
            append(card.select(DIFFICULTY_SIGNAL_SELECTOR).joinToString(" ") { element ->
                listOf(
                    element.text(),
                    element.attr("alt"),
                    element.attr("class"),
                    element.attr("src"),
                    element.className(),
                ).joinToString(" ")
            })
            append(' ')
            append(card.text())
        }

        return when {
            RE_MASTER_REGEX.containsMatchIn(signal) -> Difficulty.RE_MASTER.levelIndex
            MASTER_REGEX.containsMatchIn(signal) -> Difficulty.MASTER.levelIndex
            EXPERT_REGEX.containsMatchIn(signal) -> Difficulty.EXPERT.levelIndex
            ADVANCED_REGEX.containsMatchIn(signal) -> Difficulty.ADVANCED.levelIndex
            BASIC_REGEX.containsMatchIn(signal) -> Difficulty.BASIC.levelIndex
            else -> null
        }
    }

    private fun difficultyFromToken(token: String): Difficulty? =
        when (token.lowercase()) {
            "basic" -> Difficulty.BASIC
            "advanced" -> Difficulty.ADVANCED
            "expert" -> Difficulty.EXPERT
            "master" -> Difficulty.MASTER
            "remaster" -> Difficulty.RE_MASTER
            else -> null
        }

    private fun extractSongType(card: org.jsoup.nodes.Element): SongType {
        val chartTypeSignal = card.select(CHART_TYPE_SELECTOR).flatMap { element ->
            listOf(
                element.attr("alt"),
                element.attr("class"),
                element.attr("id"),
                element.attr("src"),
                element.select("img").attr("src"),
                element.className(),
            )
        }.joinToString(" ") + " " + card.id()
        return if (
            DX_SIGNAL_REGEX.containsMatchIn(chartTypeSignal) ||
            chartTypeSignal.contains("music_dx", ignoreCase = true)
        ) {
            SongType.DX
        } else {
            SongType.STANDARD
        }
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
        private val ACHIEVEMENT_SELECTOR =
            ".music_score_block.w_112.t_r.f_l.f_12, .music_score_block.w_150.t_l.f_r.f_12.p_r"
        private val DX_SCORE_SELECTOR = ".music_score_block.w_190.t_r.f_l.f_12"
        private val LEVEL_SELECTOR = ".music_lv_block, .music_level_block, .music_level, .level_block, .music_lv"
        private val DIFFICULTY_INPUT_SELECTOR =
            "input[name=diff], input[name=difficulty], input[name=level_index], input[name=levelIndex]"
        private val DIFFICULTY_SIGNAL_SELECTOR =
            ".music_lv_block, .music_level_block, .music_level, .level_block, .music_lv, [class*=basic], " +
                "[class*=advanced], [class*=expert], [class*=master], [class*=remaster], [class*=utage], img"
        private val CHART_TYPE_SELECTOR = ".music_kind_icon, img.music_kind_icon, img[src*=music_dx], img[src*=music_standard]"
        private val SCORE_STATUS_SELECTOR = "img.h_30.f_r, img[src*=music_icon_]"
        private val LEVEL_REGEX = Regex("""(?:等级|LEVEL|Lv\.?)\s*([0-9]{1,2}\+?)""", RegexOption.IGNORE_CASE)
        private val BARE_LEVEL_REGEX = Regex("""[0-9]{1,2}\+?""")
        private val ACHIEVEMENT_REGEX = Regex("""([0-9]{1,3}(?:\.[0-9]{1,4})?)%""")
        private val DX_SCORE_REGEX = Regex("""\d{1,3}(,\d{3})*""")
        private val CLEAR_TYPE_REGEX = Regex(".*music_icon_(.*?)\\.png.*")
        private val DX_SIGNAL_REGEX = Regex("""(^|[/_\-\s])dx([._\-\s/]|$)""", RegexOption.IGNORE_CASE)
        private val DIFFICULTY_IMAGE_REGEX =
            Regex("""(?:^|/)diff_(basic|advanced|expert|master|remaster)\.png(?:\?.*)?$""", RegexOption.IGNORE_CASE)
        private val RE_MASTER_REGEX = Regex("""(?i)(re\s*[:\-]?\s*master|remaster|re_master|宗师|宴会场)""")
        private val MASTER_REGEX = Regex("""(?i)(^|[\s_/\-.])master($|[\s_/\-.])|大师""")
        private val EXPERT_REGEX = Regex("""(?i)(^|[\s_/\-.])expert($|[\s_/\-.])|专家""")
        private val ADVANCED_REGEX = Regex("""(?i)(^|[\s_/\-.])advanced($|[\s_/\-.])|高级""")
        private val BASIC_REGEX = Regex("""(?i)(^|[\s_/\-.])basic($|[\s_/\-.])|初级""")

        private const val PLACEHOLDER_LEVEL = "?"
        private val FC_VALUES = setOf("fc", "fcp", "ap", "app")
        private val FS_VALUES = setOf("sync", "fs", "fsp", "fsd", "fsdp")
    }
}
