package com.senecapp.data

import org.json.JSONObject

internal data class JoinSource(
    val entryPoint: String,
    val views: Int,
    val joins: Int,
    val viewToJoinRate: Double?,
    val shareOfJoins: Double?,
    val campusViewToJoinRate: Double?,
)

internal data class JoinSources(
    val question: String,
    val answer: String,
    val campusAnswer: String,
    val days: Int,
    val views: Int,
    val joins: Int,
    val sources: List<JoinSource>,
)

internal data class JoinSourcesVisit(val takenAt: Long, val data: JoinSources)
internal data class JoinSourcesKey(val account: String, val groupId: Int)

internal const val JOIN_VISIT_INTERVAL_MS = 6 * 60 * 60 * 1000L

internal interface JoinSourcesStorage {
    fun history(key: JoinSourcesKey): List<JoinSourcesVisit>
    fun save(key: JoinSourcesKey, visit: JoinSourcesVisit)
    fun clear(key: JoinSourcesKey)
}

internal fun previousJoinVisit(history: List<JoinSourcesVisit>, now: Long): JoinSourcesVisit? =
    history.firstOrNull { now - it.takenAt >= JOIN_VISIT_INTERVAL_MS }

// Returns the old records to remove before inserting the new visit, in a single transaction.
internal fun <T> replacedJoinVisits(history: List<T>, now: Long, takenAt: (T) -> Long): List<T> {
    val replaced = history.take(1).filter { now - takenAt(it) in 0 until JOIN_VISIT_INTERVAL_MS }
    return replaced + history.drop(replaced.size + 11)
}

internal fun parseJoinSources(response: JSONObject): JoinSources = JoinSources(
    response.getString("question"), response.getString("answer"), response.getString("campus_answer"),
    response.getInt("days"), response.getInt("views"), response.getInt("joins"),
    response.getJSONArray("sources").readItems {
        JoinSource(it.getString("entry_point"), it.getInt("views"), it.getInt("joins"),
            it.nullableRate("view_to_join_rate"), it.nullableRate("share_of_joins"),
            it.nullableRate("campus_view_to_join_rate"))
    },
)

private fun JSONObject.nullableRate(key: String): Double? = if (isNull(key)) null else getDouble(key)
