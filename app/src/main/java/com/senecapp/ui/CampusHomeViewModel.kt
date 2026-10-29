package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.*
import com.senecapp.auth.FirebaseIdTokenProvider
import com.senecapp.auth.SessionChangedException
import com.senecapp.auth.SessionRequiredException
import com.senecapp.auth.invalidFirebaseSession
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

class CampusHomeViewModel internal constructor(application: Application,
    private val repository: CampusRepository, private val detailsCache: GroupDetailsCache,
    private val userId: () -> String?, private val preferences: SharedPreferences) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, CampusRepository(), GroupDetailsMemoryCache(),
        FirebaseIdTokenProvider::userId, application.getSharedPreferences("campus_display", 0))
    private val ownerUid = userId()
    private var userKey = ""
    var savedEvents by mutableStateOf(setOf<Int>()); private set
    var outdoorMode by mutableStateOf("Auto"); private set
    var notices by mutableStateOf(emptyList<CampusNotice>()); private set
    var unread by mutableIntStateOf(0); private set
    var schedule by mutableStateOf(emptyList<ClassBlock>()); private set
    var interests by mutableStateOf(emptyList<CampusChoice>()); private set
    var buildings by mutableStateOf(emptyList<CampusChoice>()); private set
    var attended by mutableStateOf<Int?>(null); private set
    var detail by mutableStateOf<GroupDetails?>(null); private set
    var detailLoading by mutableStateOf(false); private set
    var detailFromCache by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var scheduleLoaded by mutableStateOf(false); private set
    private var detailRequest: Job? = null
    private var refreshRequest: Job? = null
    private var groupId = 0
    private var source = "direct"
    private var recommendationId: String? = null

    fun start(uid: String) {
        if (uid != ownerUid || !ownsSession()) { clearGroupSession(); return }
        if (userKey == uid) return
        userKey = uid
        savedEvents = preferences.getStringSet("events.$uid", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        outdoorMode = preferences.getString("outdoor.$uid", "Auto") ?: "Auto"
        refresh()
    }
    private fun runAction(action: suspend () -> Unit) {
        if (!ownsSession()) { clearGroupSession(); return }
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!ownsSession() || sessionFailure(failure)) clearGroupSession()
                error = failure.message ?: "Could not complete this action. Please retry."
            }
            finally { busy = false }
        }
    }
    fun clearError() { error = null }
    fun refresh() {
        refreshRequest?.cancel()
        refreshRequest = viewModelScope.launch {
            loading = true; error = null
            // Each section can load independently if another endpoint is unavailable.
            suspend fun load(section: suspend () -> Unit) {
                try { section() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Some information could not be updated. Please retry." }
            }
            coroutineScope {
                launch { load { val result = repository.notices(); notices = result.first; unread = result.second } }
                launch { load { schedule = repository.schedule(); scheduleLoaded = true } }
                launch { load { interests = repository.interests() } }
                launch { load { buildings = repository.buildings() } }
                launch { load { attended = CampusEventsRepository().attendedSince(semesterStart()) } }
            }
            loading = false
        }
    }
    fun toggleEvent(id: Int) {
        savedEvents = if (id in savedEvents) savedEvents - id else savedEvents + id
        preferences.edit().putStringSet("events.$userKey", savedEvents.map { it.toString() }.toSet()).apply()
    }
    fun outdoor(mode: String) {
        outdoorMode = mode
        preferences.edit().putString("outdoor.$userKey", mode).apply()
    }
    fun openGroup(id: Int, entryPoint: String = "direct", requestId: String? = null) {
        if (!ownsSession()) { clearGroupSession(); return }
        detailRequest?.cancel(); groupId = id; source = entryPoint; recommendationId = requestId
        detail = detailsCache.get(id); detailFromCache = detail != null
        detailLoading = true; error = null
        detailRequest = viewModelScope.launch {
            try {
                // One real open still reaches the backend, including on a cache hit, for BQ attribution.
                val fresh = repository.group(id, entryPoint, requestId)
                currentCoroutineContext().ensureActive()
                if (!ownsSession()) { clearGroupSession(); return@launch }
                detailsCache.put(fresh)
                detail = fresh; detailFromCache = false; detailLoading = false
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!ownsSession() || sessionFailure(failure)) clearGroupSession()
                else {
                    // Expired previews cannot remain visible after a long failed refresh.
                    detail = detailsCache.get(id); detailFromCache = detail != null
                    detailLoading = false
                }
                error = failure.message ?: "Could not load this group."
            }
        }
    }
    fun retryGroup() = openGroup(groupId, source, recommendationId)
    fun closeGroup() { detailRequest?.cancel(); detail = null; detailFromCache = false; detailLoading = false; error = null }

    private fun invalidateGroup(id: Int) {
        detailsCache.invalidate(id)
        if (id == groupId) { detailRequest?.cancel(); detailLoading = false }
    }

    private fun mutateGroup(onChanged: () -> Unit = {}, action: suspend (Int) -> GroupDetails?) {
        if (!ownsSession()) { clearGroupSession(); return }
        if (busy || detailFromCache || detail?.group?.id != groupId) return
        val id = groupId
        invalidateGroup(id)
        runAction {
            val updated = action(id)
            currentCoroutineContext().ensureActive()
            if (!ownsSession()) { clearGroupSession(); return@runAction }
            invalidateGroup(id)
            if (groupId == id && updated != null) { detail = updated; detailFromCache = false }
            onChanged()
        }
    }

    fun join(motivation: String, onChanged: () -> Unit) {
        val entryPoint = source
        val requestId = recommendationId
        mutateGroup(onChanged) { id ->
            repository.join(id, motivation, entryPoint, requestId); repository.group(id)
        }
    }
    fun leave(onChanged: () -> Unit) = mutateGroup(onChanged) { id ->
        repository.leave(id); repository.group(id)
    }
    fun toggleGroupSave(onChanged: () -> Unit) {
        val previous = detail ?: return
        mutateGroup(onChanged) { id ->
            repository.save(id, !previous.group.isSaved)
            previous.copy(group = previous.group.copy(isSaved = !previous.group.isSaved))
        }
    }
    fun requestAdmin(note: String) = mutateGroup { id ->
        repository.requestAdmin(id, note); repository.group(id)
    }
    fun openNotice(notice: CampusNotice, onOpen: () -> Unit) = runAction {
        repository.openNotice(notice.id)
        notices = notices.map { if (it.id == notice.id) it.copy(unread = false) else it }
        unread = notices.count { it.unread }; onOpen()
    }
    fun dismiss(notice: CampusNotice) = runAction {
        repository.dismissNotice(notice.id)
        notices = notices.map { if (it.id == notice.id) it.copy(unread = false, dismissed = true) else it }
        unread = notices.count { it.unread }
    }
    fun markAllRead() = runAction {
        notices.filter { it.unread }.forEach { repository.dismissNotice(it.id) }
        notices = notices.map { if (it.unread) it.copy(unread = false, dismissed = true) else it }; unread = 0
    }
    fun saveSchedule(blocks: List<ClassBlock>, onDone: () -> Unit) {
        scheduleError(blocks)?.let { error = it; return }
        runAction { schedule = repository.saveSchedule(blocks); scheduleLoaded = true; busy = false; onDone() }
    }
    fun setLocation(enabled: Boolean, onDone: () -> Unit) = runAction { repository.location(enabled); onDone() }
    fun createEvent(body: org.json.JSONObject, onDone: () -> Unit) = mutateGroup({ busy = false; onDone() }) { id ->
        repository.createEvent(id, body); null
    }
    fun uploadCover(uri: android.net.Uri, onChanged: () -> Unit) = mutateGroup(onChanged) { id ->
        val image = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true; inSampleSize = 1 }
            resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
            check(options.outWidth > 0 && options.outHeight > 0) { "Choose a supported image." }
            options.inJustDecodeBounds = false
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1800) options.inSampleSize *= 2
            var bitmap = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                ?: error("Could not read this image.")
            val orientation = resolver.openInputStream(uri)?.use {
                runCatching { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            } ?: 1
            if (orientation in 2..8) {
                val matrix = android.graphics.Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                }
                val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            try { java.io.ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output); output.toByteArray()
            } } finally { bitmap.recycle() }
        }
        check(image.size <= 5 * 1024 * 1024) { "Choose an image smaller than 5 MB." }
        repository.uploadCover(id, image)
    }

    private fun ownsSession() = ownerUid != null && userId() == ownerUid
    private fun sessionFailure(failure: Exception) = failure is SessionChangedException ||
        failure is SessionRequiredException || invalidFirebaseSession(failure) ||
        (failure is ApiHttpException && failure.status in setOf(401, 403))

    private fun clearGroupSession() {
        detailRequest?.cancel(); detailsCache.clear()
        detail = null; detailFromCache = false; detailLoading = false
        error = "Your session has ended. Sign in again."
    }

    override fun onCleared() { detailsCache.clear(); super.onCleared() }
}
