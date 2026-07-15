package dev.fluentmai.android.feature.scores

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Converts Traditional Chinese to Simplified Chinese using OpenCC's dictionaries without
 * constructing OpenCC's general-purpose conversion pipeline. Search indexing calls this over the
 * complete chart corpus, so a compact longest-phrase trie plus code-point map avoids seconds of
 * startup work while preserving the same dictionary semantics.
 */
internal object ChineseSearchNormalizer {
    private val dictionaries: Dictionaries by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::loadDictionaries)

    fun toSimplified(value: String): String {
        if (value.isEmpty()) return value
        val data = dictionaries
        val result = StringBuilder(value.length)
        var offset = 0
        while (offset < value.length) {
            var node = data.phraseRoot
            var cursor = offset
            var phraseEnd = -1
            var phraseReplacement: String? = null
            while (cursor < value.length) {
                val codePoint = value.codePointAt(cursor)
                node = node.children[codePoint] ?: break
                cursor += Character.charCount(codePoint)
                node.replacement?.let { replacement ->
                    phraseEnd = cursor
                    phraseReplacement = replacement
                }
            }

            if (phraseEnd >= 0) {
                result.append(phraseReplacement)
                offset = phraseEnd
            } else {
                val codePoint = value.codePointAt(offset)
                result.append(data.characters[codePoint] ?: String(Character.toChars(codePoint)))
                offset += Character.charCount(codePoint)
            }
        }
        return result.toString()
    }

    private fun loadDictionaries(): Dictionaries {
        val characters = HashMap<Int, String>(4_500)
        readDictionary(CHARACTER_DICTIONARY).forEach { (traditional, simplified) ->
            if (traditional.codePointCount(0, traditional.length) == 1) {
                characters[traditional.codePointAt(0)] = simplified
            }
        }

        val phraseRoot = PhraseNode()
        readDictionary(PHRASE_DICTIONARY).forEach { (traditional, simplified) ->
            var node = phraseRoot
            traditional.codePoints().forEach { codePoint ->
                node = node.children.getOrPut(codePoint) { PhraseNode() }
            }
            node.replacement = simplified
        }
        return Dictionaries(characters, phraseRoot)
    }

    private fun readDictionary(path: String): List<Pair<String, String>> {
        val stream = requireNotNull(ChineseSearchNormalizer::class.java.classLoader?.getResourceAsStream(path)) {
            "Missing Chinese search dictionary: $path"
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            lines.mapNotNull { line ->
                val separator = line.indexOf('\t')
                if (separator <= 0 || separator >= line.lastIndex) {
                    null
                } else {
                    line.substring(0, separator) to line.substring(separator + 1).trimEnd('\r')
                }
            }.toList()
        }
    }

    private data class Dictionaries(
        val characters: Map<Int, String>,
        val phraseRoot: PhraseNode,
    )

    private class PhraseNode(
        val children: MutableMap<Int, PhraseNode> = HashMap(),
        var replacement: String? = null,
    )

    private const val CHARACTER_DICTIONARY = "data/dictionary/TSCharacters.txt"
    private const val PHRASE_DICTIONARY = "data/dictionary/TSPhrases.txt"
}
