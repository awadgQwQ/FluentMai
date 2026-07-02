package dev.fluentmai.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import dev.fluentmai.android.core.upload.MaimaiUploadHttpRequest
import dev.fluentmai.android.core.upload.MaimaiUploadHttpResponse
import dev.fluentmai.android.core.upload.MaimaiUploadTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL

class AndroidNetworkMaimaiUploadTransport(
    context: Context,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 25_000,
    private val maxAttempts: Int = 2,
    private val retryBackoffMs: Long = 1_000L,
) : MaimaiUploadTransport {
    private val appContext = context.applicationContext
    @Volatile
    private var successfulRouteSeen: Boolean = false

    private val connectivityManager: ConnectivityManager?
        get() = appContext.getSystemService(ConnectivityManager::class.java)

    override fun execute(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse {
        var lastError: IOException? = null
        var lastResponse: MaimaiUploadHttpResponse? = null
        val attempts = (request.maxAttempts ?: maxAttempts).coerceAtLeast(1)

        repeat(attempts) { attempt ->
            try {
                val response = executeOnce(request)
                if (!response.statusCode.shouldRetryHttpStatus() || attempt == attempts - 1) {
                    return response
                }
                lastResponse = response
                sleepBeforeRetry(attempt)
            } catch (error: IOException) {
                lastError = error
                if (attempt < attempts - 1) {
                    sleepBeforeRetry(attempt)
                }
            }
        }

        lastResponse?.let { return it }
        throw lastError ?: IOException("Upload request failed.")
    }

    private fun executeOnce(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse {
        val url = URL(request.url)
        val bodyBytes = request.body.toByteArray(Charsets.UTF_8)
        val routeFailures = mutableListOf<String>()

        for (route in connectionRoutes()) {
            try {
                val response = executeWithConnection(
                    request = request,
                    bodyBytes = bodyBytes,
                    connection = route.open(url),
                )
                successfulRouteSeen = true
                return response
            } catch (error: IOException) {
                val message = "${route.label}: ${error.routeMessage()}"
                routeFailures += message
                Log.w(TAG, "Upload route failed: $message")
                if (successfulRouteSeen && error is SocketTimeoutException) {
                    break
                }
            }
        }

        throw IOException(
            "all upload routes failed (${request.method}, ${bodyBytes.size} bytes): " +
                routeFailures.joinToString("; "),
        )
    }

    private fun executeWithConnection(
        request: MaimaiUploadHttpRequest,
        bodyBytes: ByteArray,
        connection: HttpURLConnection,
    ): MaimaiUploadHttpResponse {
        connection.apply {
            requestMethod = request.method
            doOutput = bodyBytes.isNotEmpty() && request.method != "GET" && request.method != "DELETE"
            connectTimeout = request.connectTimeoutMs ?: connectTimeoutMs
            readTimeout = request.readTimeoutMs ?: readTimeoutMs
            useCaches = false
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "FluentMai-Android")
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (doOutput) {
                setFixedLengthStreamingMode(bodyBytes.size)
            }
        }

        return try {
            if (connection.doOutput) {
                connection.outputStream.use { stream ->
                    stream.write(bodyBytes)
                }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            MaimaiUploadHttpResponse(
                statusCode = status,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class ConnectionRoute(
        val label: String,
        val open: (URL) -> HttpURLConnection,
    )

    private fun connectionRoutes(): List<ConnectionRoute> =
        buildList {
            nonVpnNetwork()?.let { network ->
                add(
                    ConnectionRoute("non-vpn-${network.describe()}") { url ->
                        network.openConnection(url) as HttpURLConnection
                    },
                )
            }
            add(
                ConnectionRoute("default-no-proxy") { url ->
                    url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                },
            )
            add(
                ConnectionRoute("default-system") { url ->
                    url.openConnection() as HttpURLConnection
                },
            )
        }

    private fun nonVpnNetwork(): Network? {
        val manager = connectivityManager ?: return null
        manager.activeNetwork?.takeIf { isUsableNonVpnNetwork(manager, it) }?.let { return it }
        return manager.allNetworks
            .filter { isUsableNonVpnNetwork(manager, it) }
            .maxByOrNull { network ->
                val caps = manager.getNetworkCapabilities(network)
                when {
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> 2
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> 1
                    else -> 0
                }
            }
    }

    private fun isUsableNonVpnNetwork(manager: ConnectivityManager, network: Network): Boolean {
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun Network.describe(): String =
        toString().replace(Regex("\\s+"), "_")

    private fun IOException.routeMessage(): String =
        "${this::class.java.simpleName}: ${(message ?: "no detail").replace(Regex("\\s+"), " ")}"

    private fun sleepBeforeRetry(attempt: Int) {
        try {
            Thread.sleep(retryBackoffMs * (attempt + 1))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Upload retry interrupted.", interrupted)
        }
    }

    private fun Int.shouldRetryHttpStatus(): Boolean =
        this == 429 || this in 500..599

    private companion object {
        private const val TAG = "FluentMaiUpload"
    }
}
