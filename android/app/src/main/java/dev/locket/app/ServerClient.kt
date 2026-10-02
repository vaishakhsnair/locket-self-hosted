package dev.locket.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

enum class ServerConnectionStatus(val label: String) {
    NOT_CONFIGURED("Not configured"),
    CHECKING("Checking server…"),
    CONNECTED("Connected"),
    UNAUTHORIZED("Access token rejected"),
    UNREACHABLE("Server unreachable"),
}

class ServerClient(context: Context) {
    private val preferences = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private val localDeviceId = DeviceIdentity.id(context)

    var displayName: String
        get() = preferences.getString("display_name", "") ?: ""
        set(value) { preferences.edit().putString("display_name", value.trim()).apply() }

    var baseUrl: String
        get() = preferences.getString("server_url", "") ?: ""
        set(value) { preferences.edit().putString("server_url", value.trim().trimEnd('/')).apply() }

    var accessToken: String
        get() = preferences.getString("access_token", "") ?: ""
        set(value) { preferences.edit().putString("access_token", value.trim()).apply() }

    var mediaKey: String
        get() = preferences.getString("media_key", "") ?: ""
        set(value) { preferences.edit().putString("media_key", value.trim()).apply() }

    fun checkConnection(): ServerConnectionStatus {
        val endpoint = baseUrl
        if (endpoint.isBlank()) return ServerConnectionStatus.NOT_CONFIGURED
        val connection = runCatching {
            (URL("$endpoint/v1/photos").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                accessToken.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Locket-Token", it) }
            }
        }.getOrNull() ?: return ServerConnectionStatus.UNREACHABLE
        return runCatching {
            when (connection.responseCode) {
                in 200..299 -> ServerConnectionStatus.CONNECTED
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> ServerConnectionStatus.UNAUTHORIZED
                else -> ServerConnectionStatus.UNREACHABLE
            }
        }.getOrDefault(ServerConnectionStatus.UNREACHABLE).also { connection.disconnect() }
    }

    fun upload(file: File, capturedAt: Long, filename: String, photoId: String, senderDeviceId: String, senderName: String): Boolean {
        val endpoint = baseUrl
        if (endpoint.isBlank()) return false
        val connection = (URL("$endpoint/v1/photos").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("X-Captured-At", java.time.Instant.ofEpochMilli(capturedAt).toString())
            setRequestProperty("X-Filename", filename)
            setRequestProperty("X-Photo-ID", photoId)
            setRequestProperty("X-Sender-Device", senderDeviceId)
            setRequestProperty("X-Sender-Name", senderName)
            accessToken.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Locket-Token", it) }
        }
        return runCatching {
            val payload = file.readBytes().let { bytes -> if (mediaKey.isBlank()) bytes else MediaCrypto.encrypt(bytes, mediaKey) }
            connection.outputStream.use { output -> output.write(payload) }
            connection.responseCode in 200..299
        }.getOrDefault(false).also { connection.disconnect() }
    }

    fun downloadMissing(knownIds: Set<String>, destination: File): List<PhotoItem>? {
        val endpoint = baseUrl
        if (endpoint.isBlank()) return emptyList()
        return runCatching {
            buildList<PhotoItem> {
                var cursor: String? = null
                do {
                    val query = cursor?.let { "?before=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                    val listConnection = (URL("$endpoint/v1/photos$query").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 15_000
                        accessToken.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Locket-Token", it) }
                    }
                    val response = runCatching {
                        if (listConnection.responseCode !in 200..299) return@runCatching null
                        listConnection.inputStream.bufferedReader().use { it.readText() }
                    }.getOrNull()
                    listConnection.disconnect()
                    if (response == null) throw IOException("photo list request failed")
                    val page = JSONObject(response)
                    val entries = page.optJSONArray("photos") ?: JSONArray()
                    for (index in 0 until entries.length()) {
                        val item = entries.getJSONObject(index)
                        val id = item.getString("id")
                        if (id in knownIds || any { existing -> existing.id == id }) continue
                        val capturedAt = runCatching { java.time.Instant.parse(item.getString("capturedAt")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                        val filename = safeFilename(item.optString("filename", "$id.jpg"), id)
                        val target = File(destination, "received_$filename")
                        if (downloadMedia(endpoint, id, target)) add(PhotoItem(id, Uri.fromFile(target), target.name, capturedAt, PhotoStatus.STORED, storedSenderId(item.optString("senderDeviceId"), target.name, localDeviceId), item.optString("senderName")))
                    }
                    cursor = page.optString("nextCursor").takeIf { it.isNotBlank() }
                } while (cursor != null)
            }
        }.getOrNull()
    }

    fun openEventStream(): HttpURLConnection? {
        val endpoint = baseUrl
        if (endpoint.isBlank()) return null
        return runCatching {
            val connection = (URL("$endpoint/v1/events").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 0
                accessToken.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Locket-Token", it) }
            }
            if (connection.responseCode in 200..299) {
                connection
            } else {
                connection.disconnect()
                null
            }
        }.getOrNull()
    }

    private fun downloadMedia(endpoint: String, id: String, target: File): Boolean {
        val connection = (URL("$endpoint/v1/photos/${URLEncoder.encode(id, "UTF-8")}/ciphertext").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 20_000
            accessToken.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Locket-Token", it) }
        }
        return runCatching {
            if (connection.responseCode !in 200..299) return@runCatching false
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, ".${target.name}.part")
            val payload = connection.inputStream.use { input -> input.readBytes() }
            val plain = if (MediaCrypto.isEncrypted(payload)) {
                if (mediaKey.isBlank()) return@runCatching false
                MediaCrypto.decrypt(payload, mediaKey)
            } else if (mediaKey.isBlank()) {
                payload
            } else {
                return@runCatching false
            }
            partial.outputStream().use { output -> output.write(plain) }
            if (target.exists()) target.delete()
            partial.renameTo(target)
        }.getOrDefault(false).also { connection.disconnect() }
    }

    private fun safeFilename(value: String, fallback: String): String {
        val cleaned = value.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "$fallback.jpg" }
    }
}
