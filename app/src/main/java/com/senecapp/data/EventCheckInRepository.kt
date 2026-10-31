package com.senecapp.data

import android.content.Context
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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
class CheckInRejectedException(message: String, val status: Int = 0) : IllegalStateException(message)

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
class EventCheckInRepository(context: Context) {
    private val api = backendClient()
    private val applicationContext = context.applicationContext
    private val pendingStore = PendingCheckInsStore(RSODatabase.get(applicationContext))

    fun online(): Boolean = isOnline(applicationContext)

    // The QR code and the GPS fix do not need internet; only this request does. Without it the check-in is saved.
    suspend fun queue(eventId: Int, code: String, coordinates: EventCoordinates?) = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: throw SessionRequiredException()
        pendingStore.save(uid, eventId, code, coordinates)
    }

    suspend fun pending(): List<PendingCheckIn> = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: return@withContext emptyList()
        pendingStore.all(uid)
    }

    suspend fun retry(localId: Long) = withContext(Dispatchers.IO) { pendingStore.setStatus(localId, "pending") }

    suspend fun discard(localId: Long) = withContext(Dispatchers.IO) { pendingStore.delete(localId) }

    // Same rules as the proposals: oldest first, 'sending' before the request so it is never sent twice,
    // a refusal is 'failed' with its reason and not retried alone, and no connection stops the pass.
    suspend fun sendPending(): Int = withContext(Dispatchers.IO) {
        val uid = FirebaseIdTokenProvider.userId() ?: return@withContext 0
        if (!sendLock.tryLock()) return@withContext 0
        try {
            pendingStore.resetSending(uid)
            var sent = 0
            for (saved in pendingStore.all(uid).filter { it.status == "pending" }) {
                pendingStore.setStatus(saved.localId, "sending")
                try {
                    checkIn(saved.eventId, saved.code, saved.coordinates)
                    pendingStore.delete(saved.localId)
                    sent++
                } catch (cancelled: CancellationException) {
                    pendingStore.setStatus(saved.localId, "pending")
                    throw cancelled
                } catch (rejected: CheckInRejectedException) {
                    // The backend only accepts check-ins until 15 minutes after the event ends.
                    pendingStore.setStatus(saved.localId, "failed", if (rejected.status == 409)
                        "Not sent: the check-in window closed (15 min after the event ends) before you were " +
                                "back online, the event was cancelled, or you were more than 500 m away."
                    else rejected.message)
                } catch (_: Exception) {
                    pendingStore.setStatus(saved.localId, "pending")
                    break
                }
            }
            sent
        } finally {
            sendLock.unlock()
        }
    }

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
                404 -> throw CheckInRejectedException("This event no longer exists.", 404)
                422 -> throw CheckInRejectedException("That code is not valid for this event.", 422)
                409 -> throw CheckInRejectedException(
                    "Check-in is not available: the event may be cancelled, outside its check-in " +
                            "window (30 min before the start to 15 min after the end), or you are " +
                            "more than 500 m from the venue.",
                    409,
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

    private companion object {
        // One pass at a time for the whole app.
        val sendLock = Mutex()
    }
}
