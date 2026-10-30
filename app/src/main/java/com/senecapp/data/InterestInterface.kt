package com.senecapp.data

import java.text.Normalizer
import java.util.Locale

internal class NormalizedInterest(val interest: Interest, val label: String, val terms: Set<String>)

object InterestInference {
    private const val SUGGESTIONS = 3
    private const val PHRASE_SCORE = 3
    private const val WORD_SCORE = 2
    private const val PREFIX_SCORE = 1

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "our", "are", "you", "all", "who",
        "los", "las", "una", "uno", "del", "con", "por", "para", "que", "como", "mas", "muy",
        "sus", "sin", "este", "esta", "estudiantes", "grupo", "club", "uniandes", "universidad",
        "somos", "donde", "todos", "cada", "sobre", "entre", "hacer", "tiene", "ser",
    )

    private val accents = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9]+")

    fun suggest(name: String, description: String, interests: List<Interest>): List<Interest> =
        rank(normalize("$name $description"), interests.map(::prepare))

    internal fun prepare(interest: Interest): NormalizedInterest {
        val label = normalize(interest.name)
        return NormalizedInterest(interest, label, tokenize(label) + tokenize(normalize(interest.slug.replace('-', ' '))))
    }

    internal fun rank(haystack: String, candidates: Iterable<NormalizedInterest>): List<Interest> {
        if (haystack.length < 8) return emptyList()
        val words = tokenize(haystack)
        if (words.isEmpty()) return emptyList()

        return candidates
            .map { it.interest to score(it, haystack, words) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Interest, Int>> { it.second }.thenBy { it.first.name })
            .take(SUGGESTIONS)
            .map { it.first }
    }

    private fun score(candidate: NormalizedInterest, haystack: String, words: Set<String>): Int {
        val label = candidate.label
        // A multi-word interest ("machine learning") counts most when it appears as written.
        if (label.contains(' ') && haystack.contains(label)) return PHRASE_SCORE * 2

        var score = 0
        for (term in candidate.terms) {
            when {
                words.contains(term) -> score += WORD_SCORE
                // Catches plurals and Spanish endings: "deportes" against "deporte".
                term.length >= 5 && words.any { it.startsWith(term.take(5)) } -> score += PREFIX_SCORE
            }
        }
        return score
    }

    private fun tokenize(value: String): Set<String> =
        value.split(' ')
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()

    internal fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(accents, "")
            .replace(separators, " ")
            .trim()
}
