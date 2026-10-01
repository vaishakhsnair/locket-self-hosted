package dev.locket.app

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class PhotoStatus { PENDING, UPLOADING, SENT, STORED }

data class PhotoItem(
    val id: String,
    val uri: Uri,
    val filename: String,
    val capturedAt: Long,
    val status: PhotoStatus = PhotoStatus.STORED,
    val senderDeviceId: String = "",
    val senderName: String = "",
) {
    val dayLabel: String get() = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(capturedAt))
    val timeLabel: String get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(capturedAt))
}

class LocketViewModel : ViewModel() {
    private val context = AppContextHolder.context
    private val store = context?.let { PhotoStore(it) }
    private val server = context?.let { ServerClient(it) }
    val localDeviceId: String = context?.let { DeviceIdentity.id(it) }.orEmpty()
    private val _photos = MutableStateFlow(store?.load().orEmpty())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    fun capture(capture: ImageCapture, onCaptured: (File, Long) -> Unit, onError: (String) -> Unit = {}) {
        val context = AppContextHolder.context ?: return
        val now = System.currentTimeMillis()
        val file = File(context.filesDir, "photos/${now}.jpg").apply { parentFile?.mkdirs() }
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { onCaptured(file, now) }
            override fun onError(exception: ImageCaptureException) { file.delete(); onError("Could not take photo") }
        })
    }

    fun confirmCapture(file: File, capturedAt: Long) {
        val photo = PhotoItem(file.nameWithoutExtension, Uri.fromFile(file), "${file.nameWithoutExtension}.jpg", capturedAt, PhotoStatus.PENDING, localDeviceId, server?.displayName.orEmpty())
        _photos.value = listOf(photo) + _photos.value
        store?.save(_photos.value)
        sync(photo)
    }

    fun discardCapture(file: File) { file.delete() }

    fun serverUrl(): String = server?.baseUrl.orEmpty()

    fun accessToken(): String = server?.accessToken.orEmpty()

    fun mediaKey(): String = server?.mediaKey.orEmpty()

    fun displayName(): String = server?.displayName.orEmpty()

    fun saveDisplayName(value: String) { server?.displayName = value }

    fun doubleTapRevealEnabled(): Boolean = context?.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)?.getBoolean("double_tap_reveal", true) ?: true

    fun setDoubleTapRevealEnabled(enabled: Boolean) { context?.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)?.edit()?.putBoolean("double_tap_reveal", enabled)?.apply() }

    fun connectionInvite(): String? {
        return connectionInvite(server?.baseUrl.orEmpty(), server?.accessToken.orEmpty(), server?.mediaKey.orEmpty(), server?.displayName.orEmpty())
    }

    fun connectionInvite(url: String, token: String, key: String, name: String = ""): String? {
        val normalizedUrl = url.trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        return Uri.Builder().scheme("locket").authority("connect")
            .appendQueryParameter("server", normalizedUrl)
            .apply {
                token.trim().takeIf { it.isNotBlank() }?.let { appendQueryParameter("token", it) }
                key.trim().takeIf { it.isNotBlank() }?.let { appendQueryParameter("key", it) }
                name.trim().takeIf { it.isNotBlank() }?.let { appendQueryParameter("name", it) }
            }.build().toString()
    }

    fun saveServerUrl(value: String) {
        server?.baseUrl = value
        val appContext = AppContextHolder.context ?: return
        if (server?.baseUrl.isNullOrBlank()) DeliveryService.stop(appContext) else DeliveryService.start(appContext)
    }

    fun saveAccessToken(value: String) { server?.accessToken = value }

    fun saveMediaKey(value: String) { server?.mediaKey = value }

    private fun sync(photo: PhotoItem) {
        if (server?.baseUrl.isNullOrBlank()) return
        updateStatus(photo.id, PhotoStatus.UPLOADING)
        viewModelScope.launch(Dispatchers.IO) {
            val sent = server?.upload(File(photo.uri.path.orEmpty()), photo.capturedAt, photo.filename, photo.id, localDeviceId, photo.senderName) == true
            updateStatus(photo.id, if (sent) PhotoStatus.SENT else PhotoStatus.PENDING)
        }
    }

    fun refreshFromServer() {
        val context = AppContextHolder.context ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val current = _photos.value
            val incoming = server?.downloadMissing(current.map { it.id }.toSet(), File(context.filesDir, "photos")).orEmpty()
            if (incoming.isNotEmpty()) {
                val merged = (incoming + current).distinctBy { it.id }.sortedByDescending { it.capturedAt }
                _photos.value = merged
                store?.save(merged)
                StealthWidget().updateAll(context)
            }
        }
    }

    private fun updateStatus(id: String, status: PhotoStatus) {
        val updated = _photos.value.map { if (it.id == id) it.copy(status = status) else it }
        _photos.value = updated
        store?.save(updated)
    }

    fun export(context: Context, photo: PhotoItem, destination: Uri) {
        viewModelScope.launch { context.contentResolver.openOutputStream(destination)?.use { output -> context.contentResolver.openInputStream(photo.uri)?.use { input -> input.copyTo(output) } } }
    }
}

class PhotoStore(context: Context) {
    private val preferences = context.getSharedPreferences("photo_archive", Context.MODE_PRIVATE)
    private val localDeviceId = DeviceIdentity.id(context)

    fun load(): List<PhotoItem> {
        val raw = preferences.getString("photos", null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val file = File(item.getString("path"))
                    if (file.exists()) {
                        val filename = item.getString("filename")
                        add(PhotoItem(item.getString("id"), Uri.fromFile(file), filename, item.getLong("capturedAt"), PhotoStatus.valueOf(item.getString("status")), storedSenderId(item.optString("senderDeviceId"), filename, localDeviceId), item.optString("senderName")))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(photos: List<PhotoItem>) {
        val json = JSONArray()
        photos.forEach { photo ->
            json.put(JSONObject().apply {
                put("id", photo.id)
                put("path", photo.uri.path)
                put("filename", photo.filename)
                put("capturedAt", photo.capturedAt)
                put("status", photo.status.name)
                put("senderDeviceId", photo.senderDeviceId)
                put("senderName", photo.senderName)
            })
        }
        preferences.edit().putString("photos", json.toString()).apply()
    }
}

class PhotoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = PhotoStore(context)
        val server = ServerClient(context)
        val current = store.load()
        val incoming = server.downloadMissing(current.map { it.id }.toSet(), File(context.filesDir, "photos"))
        if (incoming.isNotEmpty()) {
            store.save((incoming + current).distinctBy { it.id }.sortedByDescending { it.capturedAt })
            StealthWidget().updateAll(context)
        }
        return Result.success()
    }
}

object PhotoSyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PhotoSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("photo-sync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PhotoSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork("photo-sync-now", ExistingWorkPolicy.REPLACE, request)
    }
}

object AppContextHolder { var context: Context? = null }

object DeviceIdentity {
    fun id(context: Context): String {
        val preferences = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        return preferences.getString("id", null) ?: java.util.UUID.randomUUID().toString().also {
            preferences.edit().putString("id", it).apply()
        }
    }
}

fun storedSenderId(value: String, filename: String, localDeviceId: String): String =
    value.ifBlank { if (filename.startsWith("received_")) "legacy-remote" else localDeviceId }
