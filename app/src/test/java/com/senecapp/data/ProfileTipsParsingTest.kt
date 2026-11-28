package com.senecapp.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTipsParsingTest {
    private val body = """
        {
          "question": "Which group profile fields are most related to students saving or joining?",
          "answer": "Groups with a cover image convert 2x more.",
          "days": 90, "filled": 2, "total": 3,
          "fields": [
            {"field": "has_image", "filled": false, "views": 40, "correlation": 0.8, "lift": 2.0,
             "conversion_with": 0.5, "conversion_without": 0.25},
            {"field": "has_website", "filled": false, "views": 0, "correlation": null, "lift": null,
             "conversion_with": null, "conversion_without": null},
            {"field": "has_instagram", "filled": true, "views": 40, "correlation": 0.3, "lift": 1.2,
             "conversion_with": 0.4, "conversion_without": 0.33}
          ]
        }
    """.trimIndent()

    @Test fun parsesEveryFieldAndKeepsNullNumbersAsNull() {
        val tips = parseProfileTips(JSONObject(body))
        assertEquals(90, tips.days)
        assertEquals(2, tips.filled)
        assertEquals(3, tips.total)
        assertEquals(3, tips.fields.size)
        val image = tips.fields[0]
        assertEquals("has_image", image.field)
        assertFalse(image.filled)
        assertEquals(0.8, image.correlation!!, 1e-9)
        assertEquals(0.25, image.conversionWithout!!, 1e-9)
        val website = tips.fields[1]
        assertNull(website.correlation)
        assertNull(website.lift)
        assertNull(website.conversionWith)
        assertNull(website.conversionWithout)
    }

    @Test fun summaryPutsMissingFieldsFirstStrongestCorrelationFirstAndNullsLast() {
        val tips = parseProfileTips(JSONObject(body))
        val summary = summarizeProfileTips(ProfileTipsSnapshot(7, tips, syncedAt = 1L))
        assertEquals(listOf("has_image", "has_website", "has_instagram"), summary.ordered.map { it.field })
        assertEquals(1, summary.filled)
        assertEquals(3, summary.total)
        assertEquals(2, summary.missing)
        assertTrue(summary.progress in 0.33f..0.34f)
    }
}
