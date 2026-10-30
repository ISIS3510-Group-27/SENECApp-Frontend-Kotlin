package com.senecapp.data


import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestInferenceTest {
    private val catalogue = listOf(
        Interest(1, "machine-learning", "Machine Learning", "technology"),
        Interest(2, "robotics", "Robótica", "technology"),
        Interest(3, "football", "Fútbol", "sports"),
        Interest(4, "music", "Música", "arts"),
    )

    @Test fun prenormalizedRankingMatchesTheOriginalSuggestion() {
        val name = "Club de Robótica"
        val description = "Construimos robots y aprendemos machine learning cada semana"
        val prepared = catalogue.map(InterestInference::prepare)
        assertEquals(InterestInference.suggest(name, description, catalogue),
            InterestInference.rank(InterestInference.normalize("$name $description"), prepared))
    }

    @Test fun phrasesAndAccentsAreMatchedAfterNormalizing() {
        val ids = InterestInference.suggest("Grupo de musica", "Ensayamos MÚSICA y hablamos de machine learning", catalogue)
            .map { it.id }
        assertTrue(1 in ids)
        assertTrue(4 in ids)
    }

    @Test fun shortTextSuggestsNothing() {
        assertEquals(emptyList<Interest>(), InterestInference.suggest("Club", "", catalogue))
    }
}
