package com.senecapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CheckInResult(
    val eventId: Int,
    val checkedInAt: String,
    /** Metres from the venue, or null when no coordinates were sent or the event has no building. */
    val distanceM: Double?,
    /** True when this student had already checked in: a success, not an error. */
    val alreadyCheckedIn: Boolean,
)

/** A refusal the student can act on. */
class CheckInRejectedException(message: String) : IllegalStateException(message)

/**
 * POST /events/{id}/check-in.
 *
 * The backend validates in a fixed order (app/services/events.py), and one of the outcomes is not
 * an error:
 *  - 404 the event is gone;
 *  - 422 the code does not match this event;
 *  - 200 with `already_checked_in: true` when there is already an attendance row. It is checked
 *    before the window and distance rules, so a second scan always succeeds;
 *  - 409 cancelled, outside the window (30 min before the start to 15 min after the end), or more
 *    than 500 m away.
 *
 * Coordinates are optional. Sent, they must be close enough; omitted, the check-in is accepted and
 * `distance_m` comes back null, which is what happens when the student declines location.
 *
 * The shared client never exposes the error body, so the three 409 cases cannot be told apart and
 * share one message.
 */
class EventCheckInRepository {
    private val api = backendClient()

    suspend fun checkIn(
        eventId: Int,
        code: String,
        coordinates: EventCoordinates?,
    ): CheckInResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code)
        // The request schema is extra="forbid", so send the pair or neither, never one half.
        coordinates?.let {
            body.put("latitude", it.latitude).put("longitude", it.longitude)
        }

        val raw = try {
            api.request("/events/$eventId/check-in", "POST", body.toString())
        } catch (failure: ApiHttpException) {
            when (failure.status) {
                404 -> throw CheckInRejectedException("This event no longer exists.")
                422 -> throw CheckInRejectedException("That code is not valid for this event.")
                409 -> throw CheckInRejectedException(
                    "Check-in is not available: the event may be cancelled, outside its check-in " +
                            "window (30 min before the start to 15 min after the end), or you are " +
                            "more than 500 m from the venue.",
                )
                else -> throw failure
            }
        }

        val result = JSONObject(raw)
        CheckInResult(
            eventId = result.optInt("event_id", eventId),
            checkedInAt = result.optString("checked_in_at"),
            distanceM = if (result.isNull("distance_m")) null else result.optDouble("distance_m"),
            alreadyCheckedIn = result.optBoolean("already_checked_in", false),
        )
    }
}