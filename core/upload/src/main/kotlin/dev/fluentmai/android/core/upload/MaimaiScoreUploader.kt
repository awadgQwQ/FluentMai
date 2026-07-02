package dev.fluentmai.android.core.upload

import dev.fluentmai.android.core.exporter.MaimaiScoreExporter
import dev.fluentmai.android.core.model.ScoreRecord
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

enum class MaimaiUploadPlatform(val displayName: String) {
    DIVING_FISH("Diving Fish"),
    LXNS("LXNS"),
}

data class MaimaiUploadResult(
    val platform: MaimaiUploadPlatform,
    val success: Boolean,
    val statusCode: Int,
    val uploadedScoreCount: Int,
    val message: String,
    val updatedCount: Int = 0,
    val createdCount: Int = 0,
    val syncDiff: DivingFishSyncDiff? = null,
) {
    val hasCloudLocalDiff: Boolean
        get() = syncDiff?.isEmpty == false
}

data class MaimaiUploadProgress(
    val completedSteps: Int,
    val totalSteps: Int,
    val message: String,
) {
    val fraction: Float
        get() = if (totalSteps <= 0) 0f else completedSteps.toFloat() / totalSteps
}

data class DivingFishRecordIdentifier(
    val title: String,
    val type: String,
    val levelIndex: Int,
)

data class DivingFishSyncDiff(
    val cloudOnly: List<DivingFishRecordIdentifier>,
    val localOnly: List<DivingFishRecordIdentifier>,
) {
    val isEmpty: Boolean
        get() = cloudOnly.isEmpty() && localOnly.isEmpty()

    fun summaryText(): String =
        if (isEmpty) {
            "Cloud and local records match exactly."
        } else {
            "Cloud/local diff: cloud-only=${cloudOnly.size}, local-only=${localOnly.size}."
        }
}

private data class DivingFishComparableRecord(
    val key: DivingFishRecordIdentifier,
    val achievements: Double?,
    val dxScore: Int,
    val fc: String,
    val fs: String,
)

private data class DivingFishJsonUploadResult(
    val success: Boolean,
    val statusCode: Int,
    val uploadedCount: Int,
    val updatedCount: Int,
    val createdCount: Int,
    val message: String,
)

class MaimaiScoreUploader(
    private val exporter: MaimaiScoreExporter = MaimaiScoreExporter(),
    private val transport: MaimaiUploadTransport = HttpUrlConnectionMaimaiUploadTransport(),
) {
    fun uploadToDivingFish(
        importToken: String,
        scores: List<ScoreRecord>,
        onProgress: (MaimaiUploadProgress) -> Unit = {},
    ): MaimaiUploadResult {
        val token = requireToken(importToken, "Diving Fish import token")
        val records = scores.toDivingFishJsonObjects()
        if (records.isEmpty()) {
            return MaimaiUploadResult(MaimaiUploadPlatform.DIVING_FISH, true, 200, 0, "No local records to upload.")
        }

        onProgress(MaimaiUploadProgress(0, records.size, "Uploading ${records.size} local records to Diving Fish."))
        val uploadResult = uploadDivingFishJsonRecordsAdaptively(token, records, onProgress)
        if (!uploadResult.success) {
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = false,
                statusCode = uploadResult.statusCode,
                uploadedScoreCount = uploadResult.uploadedCount,
                message = uploadResult.message,
                updatedCount = uploadResult.updatedCount,
                createdCount = uploadResult.createdCount,
            )
        }

        val verifyResult = verifyDivingFishMatchesLocalJson(token, records, onProgress)
            .getOrElse { error ->
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = uploadResult.statusCode,
                    uploadedScoreCount = uploadResult.uploadedCount,
                    message = "${uploadResult.message} Verification failed: ${safeThrowableMessage(error)}",
                    updatedCount = uploadResult.updatedCount,
                    createdCount = uploadResult.createdCount,
                )
            }

        return MaimaiUploadResult(
            platform = MaimaiUploadPlatform.DIVING_FISH,
            success = verifyResult.isEmpty,
            statusCode = uploadResult.statusCode,
            uploadedScoreCount = uploadResult.uploadedCount,
            message = "${uploadResult.message} ${verifyResult.summaryText()}",
            updatedCount = uploadResult.updatedCount,
            createdCount = uploadResult.createdCount,
            syncDiff = verifyResult,
        )
    }


    fun rebuildDivingFishRecords(
        importToken: String,
        freshScores: List<ScoreRecord>,
        recordsToRemove: List<DivingFishRecordIdentifier>,
        onProgress: (MaimaiUploadProgress) -> Unit = {},
    ): MaimaiUploadResult {
        val token = requireToken(importToken, "Diving Fish import token")
        val freshRecords = freshScores.toDivingFishJsonObjects()
        onProgress(
            MaimaiUploadProgress(
                0,
                (freshRecords.size + 1).coerceAtLeast(1),
                "Starting guarded Diving Fish rebuild for ${freshRecords.size} records; candidates=${recordsToRemove.size}.",
            ),
        )
        return replaceDivingFishRecordsWithFreshRecords(
            token = token,
            records = freshRecords,
            onProgress = onProgress,
        )
    }
    fun uploadToLxns(
        userToken: String,
        scores: List<ScoreRecord>,
        onProgress: (MaimaiUploadProgress) -> Unit = {},
    ): MaimaiUploadResult {
        val token = requireToken(userToken, "LXNS user token")
        val body = exporter.toLxnsUserScoresJson(scores)
        onProgress(MaimaiUploadProgress(0, 1, "Uploading ${scores.size} local records to LXNS."))
        val response = transport.post(
            MaimaiUploadHttpRequest(
                url = LXNS_UPDATE_RECORDS_URL,
                headers = jsonHeaders + ("X-User-Token" to token),
                body = body,
                readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                maxAttempts = UPDATE_MAX_ATTEMPTS,
            ),
        )
        val apiSuccess = lxnsSuccess(response.body) ?: (response.statusCode in 200..299)
        return MaimaiUploadResult(
            platform = MaimaiUploadPlatform.LXNS,
            success = response.statusCode in 200..299 && apiSuccess,
            statusCode = response.statusCode,
            uploadedScoreCount = scores.size,
            message = lxnsMessage(response.body).ifBlank { safeMessage(response.body) }
                .ifBlank { "HTTP ${response.statusCode}" },
        )
    }
    fun diffDivingFishRecords(
        localRecords: List<JSONObject>,
        cloudRecords: List<JSONObject>,
    ): DivingFishSyncDiff {
        val localByKey = localRecords.mapNotNull { it.comparableRecordOrNull() }.associateBy { it.key }
        val cloudByKey = cloudRecords.mapNotNull { it.comparableRecordOrNull() }.associateBy { it.key }
        return DivingFishSyncDiff(
            cloudOnly = (cloudByKey.keys - localByKey.keys).sortedDivingFishIdentifiers(),
            localOnly = (localByKey.keys - cloudByKey.keys).sortedDivingFishIdentifiers(),
        )
    }

    private fun uploadDivingFishJsonRecordsAdaptively(
        token: String,
        records: List<JSONObject>,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): DivingFishJsonUploadResult {
        var uploadedCount = 0
        var updatedCount = 0
        var createdCount = 0
        var lastStatusCode = 200
        val queue = ArrayDeque<List<JSONObject>>()
        records.chunked(DIVING_FISH_UPDATE_BATCH_SIZE).forEach(queue::addLast)

        while (queue.isNotEmpty()) {
            val chunk = queue.removeFirst()
            onProgress(MaimaiUploadProgress(uploadedCount, records.size, "Uploading batch of ${chunk.size} records."))
            val response = try {
                postDivingFishJsonChunk(token, chunk)
            } catch (error: Exception) {
                MaimaiUploadHttpResponse(statusCode = 0, body = safeThrowableMessage(error))
            }

            if (response.statusCode in 200..299) {
                lastStatusCode = response.statusCode
                uploadedCount += chunk.size
                response.updateRecordsCounts().let { counts ->
                    updatedCount += counts.first
                    createdCount += counts.second
                }
                continue
            }

            if (response.statusCode.shouldSplitDivingFishChunk() && chunk.size > 1) {
                val splitIndex = (chunk.size / 2).coerceAtLeast(1)
                queue.addFirst(chunk.subList(splitIndex, chunk.size).toList())
                queue.addFirst(chunk.subList(0, splitIndex).toList())
                continue
            }

            return DivingFishJsonUploadResult(
                success = false,
                statusCode = response.statusCode,
                uploadedCount = uploadedCount,
                updatedCount = updatedCount,
                createdCount = createdCount,
                message = httpMessage("Diving Fish update_records failed", response.statusCode, response.body),
            )
        }

        return DivingFishJsonUploadResult(
            success = true,
            statusCode = lastStatusCode,
            uploadedCount = uploadedCount,
            updatedCount = updatedCount,
            createdCount = createdCount,
            message = "Diving Fish uploaded $uploadedCount records (updates=$updatedCount, creates=$createdCount).",
        )
    }

    private fun postDivingFishJsonChunk(token: String, records: List<JSONObject>): MaimaiUploadHttpResponse =
        transport.post(
            MaimaiUploadHttpRequest(
                url = DIVING_FISH_UPDATE_RECORDS_URL,
                headers = jsonHeaders + ("Import-Token" to token),
                body = JSONArray().apply { records.forEach(::put) }.toString(),
                readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                maxAttempts = UPDATE_MAX_ATTEMPTS,
            ),
        )


    private fun replaceDivingFishRecordsWithFreshRecords(
        token: String,
        records: List<JSONObject>,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        val deleteResponse = deleteDivingFishRecords(token)
        if (deleteResponse.statusCode !in 200..299) {
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = false,
                statusCode = deleteResponse.statusCode,
                uploadedScoreCount = 0,
                message = httpMessage("Diving Fish delete_records failed", deleteResponse.statusCode, deleteResponse.body),
            )
        }

        if (records.isEmpty()) {
            val verifyResult = verifyDivingFishMatchesLocalJson(token, records, onProgress)
                .getOrElse { error ->
                    return MaimaiUploadResult(
                        platform = MaimaiUploadPlatform.DIVING_FISH,
                        success = false,
                        statusCode = deleteResponse.statusCode,
                        uploadedScoreCount = 0,
                        message = "Diving Fish rebuild deleted records, but verification failed: ${safeThrowableMessage(error)}",
                    )
                }
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = verifyResult.isEmpty,
                statusCode = deleteResponse.statusCode,
                uploadedScoreCount = 0,
                message = "Diving Fish rebuilt 0 records after guarded delete_records. ${verifyResult.summaryText()}",
                syncDiff = verifyResult,
            )
        }

        val uploadResult = uploadDivingFishJsonRecordsAdaptively(token, records, onProgress)
        if (!uploadResult.success) {
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = false,
                statusCode = uploadResult.statusCode,
                uploadedScoreCount = uploadResult.uploadedCount,
                message = "Diving Fish rebuild deleted records, but full upload failed. ${uploadResult.message}",
                updatedCount = uploadResult.updatedCount,
                createdCount = uploadResult.createdCount,
            )
        }

        val verifyResult = verifyDivingFishMatchesLocalJson(token, records, onProgress)
            .getOrElse { error ->
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = uploadResult.statusCode,
                    uploadedScoreCount = uploadResult.uploadedCount,
                    message = "Diving Fish rebuild uploaded ${uploadResult.uploadedCount} records, but verification failed: ${safeThrowableMessage(error)}",
                    updatedCount = uploadResult.updatedCount,
                    createdCount = uploadResult.createdCount,
                )
            }

        return MaimaiUploadResult(
            platform = MaimaiUploadPlatform.DIVING_FISH,
            success = verifyResult.isEmpty,
            statusCode = uploadResult.statusCode,
            uploadedScoreCount = uploadResult.uploadedCount,
            message = "Diving Fish rebuilt ${uploadResult.uploadedCount} records after guarded delete_records " +
                "(updates=${uploadResult.updatedCount}, creates=${uploadResult.createdCount}). ${verifyResult.summaryText()}",
            updatedCount = uploadResult.updatedCount,
            createdCount = uploadResult.createdCount,
            syncDiff = verifyResult,
        )
    }

    private fun deleteDivingFishRecords(token: String): MaimaiUploadHttpResponse =
        transport.delete(
            MaimaiUploadHttpRequest(
                url = DIVING_FISH_DELETE_RECORDS_URL,
                headers = acceptJsonHeaders + ("Import-Token" to token),
                body = "",
                readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                maxAttempts = UPDATE_MAX_ATTEMPTS,
            ),
        )
    private fun verifyDivingFishMatchesLocalJson(
        token: String,
        localRecords: List<JSONObject>,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): Result<DivingFishSyncDiff> =
        runCatching {
            onProgress(MaimaiUploadProgress(0, 1, "Fetching Diving Fish records for verification."))
            val response = transport.get(
                MaimaiUploadHttpRequest(
                    url = DIVING_FISH_RECORDS_URL,
                    headers = acceptJsonHeaders + ("Import-Token" to token),
                    body = "",
                    readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                    maxAttempts = UPDATE_MAX_ATTEMPTS,
                ),
            )
            if (response.statusCode !in 200..299) {
                throw IllegalStateException(httpMessage("Diving Fish player records failed", response.statusCode, response.body))
            }
            diffDivingFishRecords(localRecords, JSONObject(response.body).getJSONArray("records").objectList())
        }
    private fun List<ScoreRecord>.toDivingFishJsonObjects(): List<JSONObject> =
        JSONArray(exporter.toDivingFishUpdateRecordsJson(this)).objectList()

    private fun Int.shouldSplitDivingFishChunk(): Boolean =
        this == 0 || this == 413 || this == 429 || this in 500..599

    private fun MaimaiUploadHttpResponse.updateRecordsCounts(): Pair<Int, Int> =
        runCatching {
            val body = JSONObject(body)
            body.optInt("updates", 0) to body.optInt("creates", 0)
        }.getOrDefault(0 to 0)

    private fun lxnsSuccess(body: String): Boolean? =
        runCatching { JSONObject(body).optBoolean("success") }.getOrNull()

    private fun lxnsMessage(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrDefault("")

    private fun requireToken(value: String, label: String): String =
        value.trim().ifBlank { throw IllegalArgumentException("$label is required.") }

    private fun Collection<DivingFishRecordIdentifier>.sortedDivingFishIdentifiers(): List<DivingFishRecordIdentifier> =
        sortedWith(compareBy<DivingFishRecordIdentifier> { it.title.lowercase() }.thenBy { it.type }.thenBy { it.levelIndex })

    private fun JSONObject.comparableRecordOrNull(): DivingFishComparableRecord? {
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val type = optString("type").takeIf { it.isNotBlank() } ?: return null
        if (!has("level_index")) return null
        val key = DivingFishRecordIdentifier(title.trim(), type.normalizedDivingFishType(), optInt("level_index"))
        val achievements = if (has("achievements") && !isNull("achievements")) {
            runCatching { getDouble("achievements") }.getOrNull()
        } else {
            null
        }
        return DivingFishComparableRecord(
            key = key,
            achievements = achievements,
            dxScore = optInt("dxScore", 0),
            fc = optionalString("fc").trim().lowercase(),
            fs = optionalString("fs").trim().lowercase(),
        )
    }

    private fun DivingFishComparableRecord.hasSameValuesAs(other: DivingFishComparableRecord): Boolean {
        val achievementMatches = when {
            achievements == null && other.achievements == null -> true
            achievements == null || other.achievements == null -> false
            else -> abs(achievements - other.achievements) < 0.0001
        }
        return achievementMatches && dxScore == other.dxScore && fc == other.fc && fs == other.fs
    }

    private fun String.normalizedDivingFishType(): String =
        when (trim().uppercase()) {
            "STANDARD" -> "SD"
            "STD" -> "SD"
            else -> trim().uppercase()
        }

    private fun JSONObject.optionalString(name: String): String =
        if (has(name) && !isNull(name)) optString(name) else ""

    private fun JSONArray.objectList(): List<JSONObject> =
        buildList {
            for (index in 0 until length()) optJSONObject(index)?.let(::add)
        }

    private fun httpMessage(stage: String, statusCode: Int, body: String): String {
        val message = safeMessage(body)
        return if (message.isBlank()) "$stage: HTTP $statusCode" else "$stage: HTTP $statusCode: $message"
    }

    private fun safeThrowableMessage(error: Throwable?): String =
        if (error == null) {
            "unknown error"
        } else {
            safeMessage(error.message ?: error::class.java.simpleName).ifBlank { error::class.java.simpleName }
        }

    private fun safeMessage(body: String): String =
        body
            .replace(Regex("https?://[^\\s\"'<>]+"), "[REDACTED_URL]")
            .replace(Regex("(?i)\\b(cookie|set-cookie|token|authorization|x-user-token|import-token)\\b\\s*[:=]\\s*([^\\s;,}]+)")) {
                "${it.groupValues[1]}=[REDACTED_SECRET]"
            }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)

    private companion object {
        private const val DIVING_FISH_UPDATE_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
        private const val DIVING_FISH_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/records"
        private const val DIVING_FISH_DELETE_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/delete_records"
        private const val LXNS_UPDATE_RECORDS_URL =
            "https://maimai.lxns.net/api/v0/user/maimai/player/scores"
        private const val UPDATE_READ_TIMEOUT_MS = 20_000
        private const val UPDATE_MAX_ATTEMPTS = 2
        private const val DIVING_FISH_UPDATE_BATCH_SIZE = 200
        private const val MAX_MESSAGE_LENGTH = 240
        private val jsonHeaders = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json; charset=utf-8",
        )
        private val acceptJsonHeaders = mapOf("Accept" to "application/json")
    }
}
