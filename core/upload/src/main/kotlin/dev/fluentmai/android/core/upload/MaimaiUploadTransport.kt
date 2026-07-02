package dev.fluentmai.android.core.upload

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class MaimaiUploadHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val method: String = "POST",
    val connectTimeoutMs: Int? = null,
    val readTimeoutMs: Int? = null,
    val maxAttempts: Int? = null,
)

data class MaimaiUploadHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface MaimaiUploadTransport {
    fun execute(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse

    fun post(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse =
        execute(request.copy(method = "POST"))

    fun get(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse =
        execute(request.copy(method = "GET", body = ""))
}

class HttpUrlConnectionMaimaiUploadTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000,
    private val maxAttempts: Int = 2,
    private val retryBackoffMs: Long = 2_000L,
) : MaimaiUploadTransport {
    override fun execute(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse {
        var lastError: IOException? = null
        var lastResponse: MaimaiUploadHttpResponse? = null
        val attempts = request.maxAttempts ?: maxAttempts
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            try {
                val response = executeOnce(request)
                if (!response.statusCode.shouldRetryHttpStatus() || attempt == attempts - 1) return response
                lastResponse = response
                sleepBeforeRetry(attempt)
            } catch (error: IOException) {
                lastError = error
                if (attempt < attempts - 1) sleepBeforeRetry(attempt)
            }
        }
        lastResponse?.let { return it }
        throw lastError ?: IOException("Upload request failed.")
    }

    private fun sleepBeforeRetry(attempt: Int) {
        try {
            Thread.sleep(retryBackoffMs * (attempt + 1))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Upload retry interrupted.", interrupted)
        }
    }

    private fun executeOnce(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse {
        val bodyBytes = request.body.toByteArray(Charsets.UTF_8)
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            doOutput = bodyBytes.isNotEmpty() && request.method != "GET"
            connectTimeout = request.connectTimeoutMs ?: connectTimeoutMs
            readTimeout = request.readTimeoutMs ?: readTimeoutMs
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (doOutput) setFixedLengthStreamingMode(bodyBytes.size)
        }
        return try {
            if (connection.doOutput) connection.outputStream.use { it.write(bodyBytes) }
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

    private fun Int.shouldRetryHttpStatus(): Boolean =
        this == 429 || this in 500..599
}
