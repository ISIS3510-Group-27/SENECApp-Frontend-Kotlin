package com.senecapp.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senecapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

class CampusHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CampusRepository()
    private val preferences = application.getSharedPreferences("campus_display", 0)
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
        if (userKey == uid) return
        userKey = uid
        savedEvents = preferences.getStringSet("events.$uid", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        outdoorMode = preferences.getString("outdoor.$uid", "Auto") ?: "Auto"
        refresh()
    }
    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not complete this action. Please retry." }
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
        detailRequest?.cancel(); groupId = id; source = entryPoint; recommendationId = requestId
        detail = null; detailLoading = true; error = null
        detailRequest = viewModelScope.launch {
            try { detail = repository.group(id, entryPoint, requestId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not load this group." }
            finally { detailLoading = false }
        }
    }
    fun retryGroup() = openGroup(groupId, source, recommendationId)
    fun closeGroup() { detailRequest?.cancel(); detail = null; detailLoading = false; error = null }
    fun join(motivation: String, onChanged: () -> Unit) = runAction {
        repository.join(groupId, motivation, source, recommendationId)
        detail = repository.group(groupId); onChanged()
    }
    fun leave(onChanged: () -> Unit) = runAction {
        repository.leave(groupId); detail = repository.group(groupId); onChanged()
    }
    fun toggleGroupSave(onChanged: () -> Unit) = runAction {
        val group = detail?.group ?: return@runAction
        repository.save(group.id, !group.isSaved)
        detail = detail?.copy(group = group.copy(isSaved = !group.isSaved)); onChanged()
    }
    fun requestAdmin(note: String) = runAction {
        repository.requestAdmin(groupId, note); detail = repository.group(groupId)
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
    fun createEvent(body: org.json.JSONObject, onDone: () -> Unit) = runAction {
        repository.createEvent(groupId, body); busy = false; onDone()
    }
    fun uploadCover(uri: android.net.Uri, onChanged: () -> Unit) = runAction {
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
        detail = repository.uploadCover(groupId, image); onChanged()
    }
}
