package com.coral.printagent.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.coral.printagent.model.PrintJob
import com.coral.printagent.model.PrinterResult
import com.coral.printagent.model.PrinterSettings
import com.coral.printagent.model.PrinterTarget
import org.json.JSONObject
import com.coral.printagent.printer.PrinterRepository
import com.coral.printagent.settings.PrinterSettingsRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local HTTP bridge so a web app in Chrome on this tablet can trigger printer actions.
 * Listens on loopback only (127.0.0.1:[PORT]); requires an allowed Origin for browser requests.
 */
class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val printerLock = Mutex()
    private val printer = PrinterRepository(this)
    private lateinit var settingsRepo: PrinterSettingsRepository
    private var server: ServerSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = PrinterSettingsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (server == null) startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { server?.close() }
        server = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Print bridge", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Printer Bridge")
            .setContentText("Listening for print requests on localhost:$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun startServer() {
        scope.launch {
            try {
                val ss = ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))
                server = ss
                Log.i(TAG, "Bridge listening on 127.0.0.1:$PORT")
                while (true) {
                    val client = ss.accept()
                    launch { handle(client) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Bridge stopped: ${e.message}", e)
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5000
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }

                val length = headers["content-length"]?.toIntOrNull() ?: 0
                var body = ""
                var tooLarge = false
                if (length > MAX_BODY_BYTES) {
                    tooLarge = true
                } else if (length > 0) {
                    val buf = CharArray(length)
                    var read = 0
                    while (read < length) {
                        val n = reader.read(buf, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    // Reader is ISO-8859-1, so each char is one raw byte; decode as UTF-8.
                    body = String(String(buf, 0, read).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                }

                val settings = settingsRepo.settings.first()
                val response = route(method, path, headers, settings, body, tooLarge)
                s.getOutputStream().apply { write(response); flush() }
            } catch (e: Exception) {
                Log.w(TAG, "Request failed: ${e.message}")
            }
        }
    }

    private suspend fun route(
        method: String,
        path: String,
        headers: Map<String, String>,
        settings: PrinterSettings,
        body: String,
        tooLarge: Boolean,
    ): ByteArray {
        val origin = headers["origin"]
        val allowed = settings.allowedOrigin.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (origin != null && origin !in allowed) {
            return http(403, """{"ok":false,"message":"Origin not allowed"}""")
        }
        val cors = origin != null

        if (method == "OPTIONS") return http(204, null, origin = if (cors) origin else null)

        val corsOrigin = if (cors) origin else null
        return when {
            method == "GET" && path == "/status" -> http(200, """{"ok":true,"message":"Bridge running"}""", corsOrigin)
            method == "POST" && path == "/print/test" -> {
                val target = try {
                    if (body.isBlank()) PrinterTarget.KITCHEN
                    else PrinterTarget.fromJson(JSONObject(body).optString("printer", "kitchen"))
                } catch (e: Exception) {
                    return http(400, """{"ok":false,"message":"\"printer\" must be kitchen or register"}""", corsOrigin)
                }
                result(printerLock.withLock { printer.testPrint(settings, target) }, corsOrigin)
            }
            method == "POST" && path == "/print" -> {
                if (tooLarge) return http(413, """{"ok":false,"message":"Payload too large"}""", corsOrigin)
                val job = try {
                    PrintJob.parse(body)
                } catch (e: IllegalArgumentException) {
                    val m = (e.message ?: "Invalid payload").replace("\\", "\\\\").replace("\"", "\\\"")
                    return http(400, """{"ok":false,"message":"$m"}""", corsOrigin)
                }
                result(printerLock.withLock { printer.print(settings, job) }, corsOrigin)
            }
            method == "POST" && path == "/drawer/open" ->
                result(printerLock.withLock { printer.openCashDrawer(settings) }, corsOrigin)
            else -> http(404, """{"ok":false,"message":"Not found"}""", corsOrigin)
        }
    }

    private fun result(r: PrinterResult, origin: String?): ByteArray {
        val ok = r is PrinterResult.Success
        val msg = r.message.replace("\\", "\\\\").replace("\"", "\\\"")
        return http(if (ok) 200 else 502, """{"ok":$ok,"message":"$msg"}""", origin)
    }

    private fun http(code: Int, body: String?, origin: String? = null): ByteArray {
        val reason = when (code) {
            200 -> "OK"; 204 -> "No Content"; 403 -> "Forbidden"
            400 -> "Bad Request"; 404 -> "Not Found"
            413 -> "Payload Too Large"; else -> "Bad Gateway"
        }
        val payload = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        if (origin != null) {
            sb.append("Access-Control-Allow-Origin: $origin\r\n")
            sb.append("Vary: Origin\r\n")
            sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            sb.append("Access-Control-Allow-Headers: Content-Type\r\n")
            sb.append("Access-Control-Allow-Private-Network: true\r\n")
            sb.append("Access-Control-Max-Age: 600\r\n")
        }
        sb.append("Content-Type: application/json\r\n")
        sb.append("Content-Length: ${payload.size}\r\n")
        sb.append("Connection: close\r\n\r\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1) + payload
    }

    companion object {
        const val PORT = 8765
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val TAG = "BridgeService"
        private const val CHANNEL_ID = "print_bridge"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BridgeService::class.java))
        }
    }
}
