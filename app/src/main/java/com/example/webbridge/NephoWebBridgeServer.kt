package com.example.webbridge

import android.util.Log
import com.example.data.ExtractedLinkItem
import com.example.data.PulseNotificationItem
import com.example.data.PulseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lightweight, zero-dependency HTTP/1.1 + Server-Sent Events (SSE) bridge hosted directly on the
 * device's local Wi-Fi network. Requires a 6-digit pairing PIN to exchange for a session token.
 */
class NephoWebBridgeServer(
    private val repository: PulseRepository,
    private val htmlProvider: () -> String,
    private val requestedPort: Int = DEFAULT_PORT,
    private val bindAddress: InetAddress? = null,
    private val onClientCountChanged: (Int) -> Unit = {}
) {
    private val random = SecureRandom()
    private val activeTokens = ConcurrentHashMap.newKeySet<String>()
    private val sseClients = CopyOnWriteArraySet<SseClient>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var stateBroadcastJob: Job? = null

    @Volatile
    var pairingPin: String = generatePin()
        private set

    @Volatile
    var boundPort: Int = -1
        private set

    @Volatile
    private var latestStateJson: String = emptyStateJson()

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    val connectedClientCount: Int
        get() = sseClients.size

    fun start(): Int {
        if (isRunning) return boundPort
        val socket = openServerSocket()
        serverSocket = socket
        boundPort = socket.localPort
        pairingPin = generatePin()
        activeTokens.clear()

        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope

        stateBroadcastJob = serverScope.launch {
            combine(
                repository.activeNotificationsFlow,
                repository.activeLinksFlow,
                repository.sessionPreferences.sessionOpenedAtFlow
            ) { notifications, links, sessionOpenedAt ->
                serializeStateJson(notifications, links, sessionOpenedAt)
            }.collect { payload ->
                latestStateJson = payload
                broadcastSseEvent("state", payload)
            }
        }

        serverScope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val clientSocket = socket.accept()
                    serverScope.launch {
                        handleConnection(clientSocket)
                    }
                } catch (_: SocketException) {
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Accept error on web bridge socket", e)
                }
            }
        }

        return boundPort
    }

    fun stop() {
        activeTokens.clear()
        disconnectAllSseClients()
        stateBroadcastJob?.cancel()
        stateBroadcastJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = -1
        scope?.cancel()
        scope = null
        onClientCountChanged(0)
    }

    fun rotatePin(): String {
        val nextPin = generatePin()
        pairingPin = nextPin
        activeTokens.clear()
        disconnectAllSseClients()
        return nextPin
    }

    private fun openServerSocket(): ServerSocket {
        return try {
            ServerSocket(requestedPort, 24, bindAddress).apply {
                reuseAddress = true
            }
        } catch (_: IOException) {
            if (requestedPort == 0) throw IOException("Unable to bind ephemeral port")
            ServerSocket(0, 24, bindAddress).apply {
                reuseAddress = true
            }
        }
    }

    private suspend fun handleConnection(socket: Socket) {
        var keepOpenForSse = false
        try {
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val request = readHttpRequest(input) ?: return
            val path = request.path
            val method = request.method

            when {
                method == "OPTIONS" -> {
                    writeResponse(output, 204, "No Content", "text/plain; charset=utf-8", ByteArray(0))
                }

                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = htmlProvider().toByteArray(Charsets.UTF_8)
                    writeResponse(output, 200, "OK", "text/html; charset=utf-8", html)
                }

                method == "GET" && path == "/api/ping" -> {
                    val body = JSONObject().put("ok", true).put("app", "Nepho").toString()
                    writeJson(output, 200, "OK", body)
                }

                method == "POST" && path == "/api/auth" -> {
                    handleAuth(request.body, output)
                }

                path.startsWith("/api/") -> {
                    val token = extractToken(request)
                    if (token == null || token !in activeTokens) {
                        writeJson(
                            output,
                            401,
                            "Unauthorized",
                            JSONObject().put("error", "Invalid or expired session. Enter the 6-digit PIN from your phone.").toString()
                        )
                        return
                    }
                    when {
                        method == "GET" && path == "/api/state" -> {
                            val freshJson = buildFreshStateJson()
                            latestStateJson = freshJson
                            writeJson(output, 200, "OK", freshJson)
                        }

                        method == "GET" && path == "/api/events" -> {
                            keepOpenForSse = true
                            handleSseStream(socket, output)
                        }

                        method == "POST" && path == "/api/mark-read" -> {
                            handleMarkRead(request.body, output)
                        }

                        method == "POST" && path == "/api/star" -> {
                            handleToggleStar(request.body, output)
                        }

                        method == "POST" && path == "/api/dismiss" -> {
                            handleDismiss(request.body, output)
                        }

                        method == "POST" && path == "/api/reply" -> {
                            handleReply(request.body, output)
                        }

                        method == "POST" && path == "/api/secondary-action" -> {
                            handleSecondaryAction(request.body, output)
                        }

                        else -> {
                            writeJson(output, 404, "Not Found", JSONObject().put("error", "Unknown endpoint").toString())
                        }
                    }
                }

                else -> {
                    writeJson(output, 404, "Not Found", JSONObject().put("error", "Not found").toString())
                }
            }
        } catch (_: IOException) {
            // Client closed connection
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error handling web bridge request", e)
        } finally {
            if (!keepOpenForSse) {
                runCatching { socket.close() }
            }
        }
    }

    private fun handleAuth(rawBody: String, output: OutputStream) {
        val candidatePin = runCatching {
            JSONObject(rawBody).optString("pin").replace("\\s+".toRegex(), "")
        }.getOrDefault("")
        val matches = candidatePin.isNotEmpty() && MessageDigest.isEqual(
            candidatePin.toByteArray(Charsets.UTF_8),
            pairingPin.toByteArray(Charsets.UTF_8)
        )
        if (!matches) {
            writeJson(
                output,
                401,
                "Unauthorized",
                JSONObject().put("error", "Incorrect PIN. Check Nepho on your phone and try again.").toString()
            )
            return
        }
        val token = "${UUID.randomUUID()}-${UUID.randomUUID()}"
        activeTokens.add(token)
        writeJson(
            output,
            200,
            "OK",
            JSONObject().put("ok", true).put("token", token).toString()
        )
    }

    private suspend fun handleSseStream(socket: Socket, output: OutputStream) {
        socket.soTimeout = 0
        val client = SseClient(socket, output)
        sseClients.add(client)
        onClientCountChanged(sseClients.size)
        try {
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/event-stream; charset=utf-8\r\n")
                append("Cache-Control: no-cache, no-store\r\n")
                append("Connection: keep-alive\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append("X-Accel-Buffering: no\r\n")
                append("\r\n")
            }
            client.write(headers.toByteArray(Charsets.UTF_8))

            val initialState = buildFreshStateJson()
            latestStateJson = initialState
            client.write(formatSseFrame("state", initialState))

            val currentScope = scope
            while (currentScope?.isActive == true && !socket.isClosed && client in sseClients) {
                delay(20_000L)
                client.write(": keepalive\n\n".toByteArray(Charsets.UTF_8))
            }
        } catch (_: IOException) {
            // Browser disconnected
        } finally {
            sseClients.remove(client)
            runCatching { socket.close() }
            onClientCountChanged(sseClients.size)
        }
    }

    private suspend fun handleMarkRead(rawBody: String, output: OutputStream) {
        val keys = parseKeysFromBody(rawBody)
        if (keys.isEmpty()) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Missing notification key").toString())
            return
        }
        keys.forEach { repository.markAsRead(it) }
        writeJson(output, 200, "OK", JSONObject().put("ok", true).put("count", keys.size).toString())
    }

    private suspend fun handleToggleStar(rawBody: String, output: OutputStream) {
        val obj = runCatching { JSONObject(rawBody) }.getOrNull()
        if (obj == null) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Invalid JSON").toString())
            return
        }
        val linkId = obj.optString("linkId").trim()
        if (linkId.isNotEmpty()) {
            val currentlyStarred = obj.optBoolean("currentlyStarred", false)
            val next = repository.toggleLinkStar(linkId, currentlyStarred)
            writeJson(output, 200, "OK", JSONObject().put("ok", true).put("isStarred", next).toString())
            return
        }
        val key = obj.optString("key").trim()
        if (key.isEmpty()) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Missing key or linkId").toString())
            return
        }
        val next = repository.toggleNotificationStar(key)
        writeJson(output, 200, "OK", JSONObject().put("ok", true).put("isStarred", next).toString())
    }

    private suspend fun handleDismiss(rawBody: String, output: OutputStream) {
        val keys = parseKeysFromBody(rawBody)
        if (keys.isEmpty()) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Missing notification key").toString())
            return
        }
        keys.forEach { repository.dismissNotification(it) }
        writeJson(output, 200, "OK", JSONObject().put("ok", true).put("count", keys.size).toString())
    }

    private suspend fun handleReply(rawBody: String, output: OutputStream) {
        val obj = runCatching { JSONObject(rawBody) }.getOrNull()
        val key = obj?.optString("key")?.trim().orEmpty()
        val text = obj?.optString("text")?.trim().orEmpty()
        if (key.isEmpty() || text.isEmpty()) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Reply text and notification key are required.").toString())
            return
        }
        val result = repository.sendReplyWithResult(key = key, replyText = text)
        if (result.success) {
            writeJson(output, 200, "OK", JSONObject().put("ok", true).toString())
        } else {
            writeJson(
                output,
                409,
                "Conflict",
                JSONObject().put("ok", false).put("error", "This notification is no longer available to reply to on your phone.").toString()
            )
        }
    }

    private suspend fun handleSecondaryAction(rawBody: String, output: OutputStream) {
        val obj = runCatching { JSONObject(rawBody) }.getOrNull()
        val key = obj?.optString("key")?.trim().orEmpty()
        val actionLabel = obj?.optString("actionLabel")?.trim().orEmpty()
        if (key.isEmpty() || actionLabel.isEmpty()) {
            writeJson(output, 400, "Bad Request", JSONObject().put("error", "Missing key or actionLabel").toString())
            return
        }
        repository.executeSecondaryAction(key, actionLabel)
        writeJson(output, 200, "OK", JSONObject().put("ok", true).toString())
    }

    private fun parseKeysFromBody(rawBody: String): List<String> {
        val obj = runCatching { JSONObject(rawBody) }.getOrNull() ?: return emptyList()
        val array = obj.optJSONArray("keys")
        if (array != null) {
            return (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        val single = obj.optString("key").trim()
        return if (single.isNotEmpty()) listOf(single) else emptyList()
    }

    private suspend fun buildFreshStateJson(): String {
        val notifications = repository.activeNotificationsFlow.first()
        val links = repository.activeLinksFlow.first()
        val sessionOpenedAt = repository.sessionPreferences.sessionOpenedAtFlow.first()
        return serializeStateJson(notifications, links, sessionOpenedAt)
    }

    private fun broadcastSseEvent(eventName: String, jsonData: String) {
        if (sseClients.isEmpty()) return
        val frame = formatSseFrame(eventName, jsonData)
        val dead = mutableListOf<SseClient>()
        for (client in sseClients) {
            try {
                client.write(frame)
            } catch (_: IOException) {
                dead.add(client)
            }
        }
        if (dead.isNotEmpty()) {
            dead.forEach {
                sseClients.remove(it)
                runCatching { it.socket.close() }
            }
            onClientCountChanged(sseClients.size)
        }
    }

    private fun disconnectAllSseClients() {
        val clients = sseClients.toList()
        sseClients.clear()
        clients.forEach { client ->
            runCatching { client.socket.close() }
        }
        onClientCountChanged(0)
    }

    private fun formatSseFrame(eventName: String, data: String): ByteArray {
        val safeSingleLine = data.replace("\r", "").replace("\n", "")
        return "event: $eventName\ndata: $safeSingleLine\n\n".toByteArray(Charsets.UTF_8)
    }

    private fun extractToken(request: ParsedHttpRequest): String? {
        val authHeader = request.headers["authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return authHeader.substring(7).trim().takeIf { it.isNotEmpty() }
        }
        return request.queryParams["token"]?.takeIf { it.isNotBlank() }
    }

    private fun writeJson(output: OutputStream, status: Int, reason: String, json: String) {
        writeResponse(output, status, reason, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
    }

    private fun writeResponse(
        output: OutputStream,
        status: Int,
        reason: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun readHttpRequest(input: BufferedInputStream): ParsedHttpRequest? {
        val requestLine = readAsciiLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val rawTarget = parts[1]
        val querySplit = rawTarget.split("?", limit = 2)
        val path = querySplit[0]
        val queryParams = parseQueryParams(querySplit.getOrNull(1))

        val headers = LinkedHashMap<String, String>()
        var headerCount = 0
        while (headerCount < 64) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
            headerCount++
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
        val bodyBytes = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(buf, offset, contentLength - offset)
                if (read <= 0) break
                offset += read
            }
            buf.copyOf(offset)
        } else {
            ByteArray(0)
        }

        return ParsedHttpRequest(
            method = method,
            path = path,
            queryParams = queryParams,
            headers = headers,
            body = String(bodyBytes, Charsets.UTF_8)
        )
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1 && out.size() < 8192) {
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
            b = input.read()
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        rawQuery.split("&").forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            val decodedVal = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            result[decodedKey] = decodedVal
        }
        return result
    }

    private fun generatePin(): String = "%06d".format(random.nextInt(1_000_000))

    private class SseClient(
        val socket: Socket,
        private val output: OutputStream
    ) {
        @Synchronized
        fun write(bytes: ByteArray) {
            output.write(bytes)
            output.flush()
        }
    }

    private data class ParsedHttpRequest(
        val method: String,
        val path: String,
        val queryParams: Map<String, String>,
        val headers: Map<String, String>,
        val body: String
    )

    companion object {
        private const val TAG = "NephoWebBridgeServer"
        const val DEFAULT_PORT = 8765
        private const val MAX_BODY_BYTES = 131_072

        fun emptyStateJson(): String = JSONObject()
            .put("sessionOpenedAt", System.currentTimeMillis())
            .put("unreadTotal", 0)
            .put("notifications", JSONArray())
            .put("links", JSONArray())
            .toString()

        fun serializeStateJson(
            notifications: List<PulseNotificationItem>,
            links: List<ExtractedLinkItem>,
            sessionOpenedAt: Long
        ): String {
            val notifArray = JSONArray()
            notifications.forEach { item ->
                val repliesArray = JSONArray()
                item.replies.forEach { reply ->
                    repliesArray.put(
                        JSONObject()
                            .put("text", reply.text)
                            .put("sentAt", reply.sentAt)
                    )
                }
                val urlsArray = JSONArray()
                item.urls.forEach { urlsArray.put(it) }
                val actionsArray = JSONArray()
                item.secondaryActions.forEach { actionsArray.put(it) }

                notifArray.put(
                    JSONObject()
                        .put("key", item.key)
                        .put("threadId", item.threadId)
                        .put("packageName", item.packageName)
                        .put("appLabel", item.appLabel)
                        .put("shortBadge", item.supportedApp.shortBadge)
                        .put("title", item.title)
                        .put("body", item.body)
                        .put("postTime", item.postTime)
                        .put("isRead", item.isRead)
                        .put("isStarred", item.isStarred)
                        .put("canReply", item.canReply)
                        .put("secondaryActions", actionsArray)
                        .put("replies", repliesArray)
                        .put("urls", urlsArray)
                )
            }

            val linksArray = JSONArray()
            links.forEach { link ->
                linksArray.put(
                    JSONObject()
                        .put("id", link.id)
                        .put("url", link.url)
                        .put("domain", link.domain)
                        .put("packageName", link.packageName)
                        .put("appLabel", link.appLabel)
                        .put("shortBadge", link.supportedApp.shortBadge)
                        .put("senderTitle", link.senderTitle)
                        .put("postTime", link.postTime)
                        .put("isStarred", link.isStarred)
                )
            }

            return JSONObject()
                .put("sessionOpenedAt", sessionOpenedAt)
                .put("unreadTotal", notifications.count { !it.isRead })
                .put("notifications", notifArray)
                .put("links", linksArray)
                .toString()
        }
    }
}
