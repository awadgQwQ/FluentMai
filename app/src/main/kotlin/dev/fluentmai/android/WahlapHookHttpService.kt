package dev.fluentmai.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class WahlapHookHttpService : Service() {
    private var hookServer: SimpleHttpServer? = null
    private var redirectServer: SimpleHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServers()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground()
        startServers()
        return START_STICKY
    }

    override fun onDestroy() {
        stopServers()
        super.onDestroy()
    }

    private fun startServers() {
        var hookStarted = true
        var redirectStarted = true
        if (hookServer == null) {
            hookServer = SimpleHttpServer(PORT) { path -> serveHook(path) }
            hookStarted = hookServer?.start() == true
        }
        if (redirectServer == null) {
            redirectServer = SimpleHttpServer(REDIRECT_PORT) { serveCapturedPage() }
            redirectStarted = redirectServer?.start() == true
        }
        if (hookStarted && redirectStarted) {
            WahlapHookBridge.setStatus("Local Hook service is running. Open the hook link in WeChat.")
        } else {
            WahlapHookBridge.setStatus("Local Hook service failed to bind its ports.")
        }
    }

    private fun stopServers() {
        hookServer?.stop()
        redirectServer?.stop()
        hookServer = null
        redirectServer = null
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "FluentMai Hook",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the local Wahlap Hook service running"
            }
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("FluentMai is waiting for Wahlap auth")
            .setContentText("Local Hook service is running")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun serveHook(path: String): HookHttpResponse =
        when {
            path == "/0" || path.startsWith("/auth/maimai") -> serveMaimaiAuthRedirect()
            else -> HookHttpResponse.html(404, "FluentMai Hook link is invalid.")
        }

    private fun serveMaimaiAuthRedirect(): HookHttpResponse {
        if (WahlapHookBridge.isImporting()) {
            return HookHttpResponse.html(202, "Capture already started. Return to FluentMai and wait for import.")
        }
        WahlapHookBridge.setStatus("Hook link opened. Waiting for Wahlap OAuth callback traffic.")
        return HookHttpResponse.html(202, "FluentMai Hook is waiting for the Wahlap OAuth callback.")
    }

    private fun serveCapturedPage(): HookHttpResponse =
        HookHttpResponse.html(202, "Auth callback captured. Return to FluentMai and wait for import.")

    companion object {
        const val PORT = 8284
        const val REDIRECT_PORT = 9457
        const val HOOK_URL = "http://127.0.0.1:$PORT/0"
        private const val NOTIFICATION_ID = 8284
        private const val NOTIFICATION_CHANNEL_ID = "fluentmai_hook"
        private const val ACTION_STOP = "dev.fluentmai.android.action.STOP_HOOK_HTTP"
        private const val TAG = "WahlapHookHttp"

        fun start(context: Context) {
            val intent = Intent(context, WahlapHookHttpService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WahlapHookHttpService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}

private data class HookHttpResponse(
    val statusCode: Int,
    val reason: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {
    companion object {
        fun html(statusCode: Int, text: String): HookHttpResponse {
            val escapedText = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            return HookHttpResponse(
                statusCode = statusCode,
                reason = when (statusCode) {
                    202 -> "Accepted"
                    404 -> "Not Found"
                    500 -> "Internal Server Error"
                    else -> "OK"
                },
                headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
                body = "<html><body><h1>$escapedText</h1></body></html>",
            )
        }
    }
}

private class SimpleHttpServer(
    private val port: Int,
    private val handler: (String) -> HookHttpResponse,
) {
    @Volatile
    private var stopped = false
    @Volatile
    private var ready = false
    private val readyLatch = CountDownLatch(1)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    fun start(): Boolean {
        if (serverThread != null) return ready
        stopped = false
        ready = false
        serverThread = thread(name = "FluentMaiHookHttp-$port", isDaemon = true) {
            runServer()
        }
        if (!readyLatch.await(1500, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "HTTP server bind timed out on port=$port")
        }
        return ready
    }

    fun stop() {
        stopped = true
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
    }

    private fun runServer() {
        try {
            ServerSocket().use { socket ->
                serverSocket = socket
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                ready = true
                readyLatch.countDown()
                while (!stopped) {
                    val client = try {
                        socket.accept()
                    } catch (_: IOException) {
                        if (stopped) return
                        continue
                    }
                    thread(name = "FluentMaiHookHttpClient-$port", isDaemon = true) {
                        client.use { handleClient(it) }
                    }
                }
            }
        } catch (error: Exception) {
            readyLatch.countDown()
            ready = false
            if (!stopped) {
                Log.e(TAG, "HTTP server failed on port=$port", error)
                WahlapHookBridge.setStatus("Local Hook service failed: ${error::class.java.simpleName}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 3000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        val headers = mutableMapOf<String, String>()
        runCatching {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }
        }.onFailure { error ->
            if (error !is java.net.SocketTimeoutException) {
                Log.w(TAG, "Failed to read HTTP headers on port=$port", error)
            }
        }
        val path = requestLine.split(" ").getOrNull(1)?.substringBefore("?") ?: "/"
        Log.i(TAG, "HTTP hit port=$port request=$requestLine host=${headers["host"]}")
        if (port == WahlapHookHttpService.PORT) {
            WahlapHookBridge.setStatus("Hook received request: $path")
        }
        writeResponse(socket.getOutputStream(), handler(path))
    }

    private fun writeResponse(output: OutputStream, response: HookHttpResponse) {
        val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
        val headers = response.headers + mapOf(
            "Content-Length" to bodyBytes.size.toString(),
            "Connection" to "close",
        )
        val head = buildString {
            append("HTTP/1.1 ${response.statusCode} ${response.reason}\r\n")
            headers.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        output.write(head)
        output.write(bodyBytes)
        output.flush()
    }

    private companion object {
        private const val TAG = "FluentMaiHookHttp"
    }
}

