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

data class DivingFishWahlapPage(
    val label: String,
    val html: String,
)

data class DivingFishRecordIdentifier(
    val title: String,
    val type: String,
    val levelIndex: Int,
)

data class DivingFishValueMismatch(
    val key: DivingFishRecordIdentifier,
    val localSummary: String,
    val cloudSummary: String,
)

data class DivingFishSyncDiff(
    val cloudOnly: List<DivingFishRecordIdentifier>,
    val localOnly: List<DivingFishRecordIdentifier>,
    val valueMismatches: List<DivingFishValueMismatch> = emptyList(),
) {
    val isEmpty: Boolean
        get() = cloudOnly.isEmpty() && localOnly.isEmpty() && valueMismatches.isEmpty()

    fun summaryText(maxSamples: Int = 5): String =
        if (isEmpty) {
            "Cloud and local records match exactly."
        } else {
            buildString {
                append("Cloud/local diff: cloud-only=${cloudOnly.size}, local-only=${localOnly.size}")
                if (valueMismatches.isNotEmpty()) {
                    append(", mismatched=${valueMismatches.size}")
                }
                append(".")
                appendSamples(" cloud-only", cloudOnly, maxSamples)
                appendSamples(" local-only", localOnly, maxSamples)
                if (valueMismatches.isNotEmpty()) {
                    val samples = valueMismatches.take(maxSamples).joinToString("; ") {
                        "${it.key.displayLabel()} local=${it.localSummary} cloud=${it.cloudSummary}"
                    }
                    append(" mismatched samples: ")
                    append(samples)
                    val remaining = valueMismatches.size - maxSamples
                    if (remaining > 0) append("; +$remaining more")
                    append(".")
                }
            }
        }

    private fun StringBuilder.appendSamples(
        label: String,
        records: List<DivingFishRecordIdentifier>,
        maxSamples: Int,
    ) {
        if (records.isEmpty()) return
        append(label)
        append(" samples: ")
        append(records.take(maxSamples).joinToString("; ") { it.displayLabel() })
        val remaining = records.size - maxSamples
        if (remaining > 0) append("; +$remaining more")
        append(".")
    }
}

private fun DivingFishRecordIdentifier.displayLabel(): String =
    "${title.trim()}/${type.trim().uppercase()}/$levelIndex"

private data class DivingFishParsedPage(
    val body: String,
    val recordCount: Int,
    val statusCode: Int,
    val parserName: String,
)

private data class DivingFishDirectHtmlResult(
    val success: Boolean,
    val statusCode: Int,
    val uploadedCount: Int,
    val message: String,
)

private data class DivingFishJsonUploadResult(
    val success: Boolean,
    val statusCode: Int,
    val uploadedCount: Int,
    val updatedCount: Int,
    val createdCount: Int,
    val skippedRecords: List<DivingFishSkippedRecord>,
    val message: String,
)

private data class DivingFishComparableRecord(
    val key: DivingFishRecordIdentifier,
    val achievements: Double?,
    val dxScore: Int,
    val fc: String,
    val fs: String,
) {
    fun valueSummary(): String =
        "achievements=${achievements ?: "?"},dxScore=$dxScore,fc=$fc,fs=$fs"
}

private data class DivingFishSkippedRecord(
    val record: JSONObject,
    val statusCode: Int,
    val reason: String,
)

private data class DivingFishPageParserEndpoint(
    val name: String,
    val url: String,
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
        return uploadDivingFishScoreChunks(
            token = token,
            scores = scores,
            onProgress = onProgress,
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
        return replaceDivingFishRecordsWithFreshRecords(
            token = token,
            records = freshRecords,
            sourceLabel = "本地",
            onProgress = onProgress,
        )
    }

    private fun uploadDivingFishScoreChunks(
        token: String,
        scores: List<ScoreRecord>,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        val records = scores.toDivingFishJsonObjects()
        if (records.isEmpty()) {
            onProgress(MaimaiUploadProgress(1, 1, "没有可上传的本地成绩"))
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = true,
                statusCode = 200,
                uploadedScoreCount = 0,
                message = "No local records to upload.",
            )
        }

        onProgress(MaimaiUploadProgress(0, records.size, "准备分批上传 ${records.size} 条本地成绩到水鱼"))
        val uploadResult = uploadDivingFishJsonRecordsAdaptively(
            token = token,
            records = records,
            completedStepOffset = 0,
            totalSteps = records.size,
            progressPrefix = "上传水鱼",
            onProgress = onProgress,
        )

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

    private fun uploadDivingFishJsonRecordsAdaptively(
        token: String,
        records: List<JSONObject>,
        completedStepOffset: Int,
        totalSteps: Int,
        progressPrefix: String,
        allowSkippingSingleRecords: Boolean = true,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): DivingFishJsonUploadResult {
        val queue = ArrayDeque<List<JSONObject>>()
        records.chunked(DIVING_FISH_UPDATE_BATCH_SIZE).forEach(queue::addLast)

        var uploadedCount = 0
        var updatedCount = 0
        var createdCount = 0
        var lastStatusCode = 200
        val skippedRecords = mutableListOf<DivingFishSkippedRecord>()
        val safeTotalSteps = totalSteps.coerceAtLeast(1)

        fun processedCount(): Int = uploadedCount + skippedRecords.size
        fun progressSteps(): Int = (completedStepOffset + processedCount()).coerceAtMost(safeTotalSteps)

        while (queue.isNotEmpty()) {
            val chunk = queue.removeFirst()
            onProgress(
                MaimaiUploadProgress(
                    progressSteps(),
                    safeTotalSteps,
                    "$progressPrefix：已处理 ${processedCount()}/${records.size} 条，当前批 ${chunk.size} 条",
                ),
            )

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
                onProgress(
                    MaimaiUploadProgress(
                        progressSteps(),
                        safeTotalSteps,
                        "$progressPrefix：已上传 $uploadedCount 条，updates=$updatedCount creates=$createdCount",
                    ),
                )
                continue
            }

            if (response.statusCode.shouldSplitDivingFishChunk() && chunk.size > 1) {
                val splitIndex = (chunk.size / 2).coerceAtLeast(1)
                val left = chunk.subList(0, splitIndex).toList()
                val right = chunk.subList(splitIndex, chunk.size).toList()
                onProgress(
                    MaimaiUploadProgress(
                        progressSteps(),
                        safeTotalSteps,
                        "水鱼返回 HTTP ${response.statusCode}，正在缩小批次重试（${chunk.size} -> ${left.size}/${right.size}）",
                    ),
                )
                queue.addFirst(right)
                queue.addFirst(left)
                continue
            }

            if (
                allowSkippingSingleRecords &&
                chunk.size == 1 &&
                response.shouldSkipDivingFishRecord(token) &&
                skippedRecords.size < MAX_DIVING_FISH_SKIPPED_RECORDS
            ) {
                val skipped = DivingFishSkippedRecord(
                    record = chunk.single(),
                    statusCode = response.statusCode,
                    reason = safeMessage(response.body).ifBlank { "HTTP ${response.statusCode}" },
                )
                skippedRecords += skipped
                onProgress(
                    MaimaiUploadProgress(
                        progressSteps(),
                        safeTotalSteps,
                        "跳过水鱼服务端无法接收的单条成绩：${skipped.record.uploadLabel()}",
                    ),
                )
                continue
            }

            return DivingFishJsonUploadResult(
                success = false,
                statusCode = response.statusCode,
                uploadedCount = uploadedCount,
                updatedCount = updatedCount,
                createdCount = createdCount,
                skippedRecords = skippedRecords,
                message = httpMessage("$progressPrefix update_records failed", response.statusCode, response.body) +
                    skippedRecords.failureSuffix(),
            )
        }

        if (uploadedCount == 0 && skippedRecords.isNotEmpty()) {
            val lastSkipped = skippedRecords.last()
            return DivingFishJsonUploadResult(
                success = false,
                statusCode = lastSkipped.statusCode,
                uploadedCount = 0,
                updatedCount = updatedCount,
                createdCount = createdCount,
                skippedRecords = skippedRecords,
                message = "Diving Fish rejected all records. " + skippedRecords.summaryText(),
            )
        }

        return DivingFishJsonUploadResult(
            success = true,
            statusCode = lastStatusCode,
            uploadedCount = uploadedCount,
            updatedCount = updatedCount,
            createdCount = createdCount,
            skippedRecords = skippedRecords,
            message = if (skippedRecords.isEmpty()) {
                "Diving Fish uploaded $uploadedCount records (updates=$updatedCount, creates=$createdCount)."
            } else {
                "Diving Fish uploaded $uploadedCount records (updates=$updatedCount, creates=$createdCount); ${skippedRecords.summaryText()}"
            },
        )
    }

    private fun replaceDivingFishRecordsWithFreshRecords(
        token: String,
        records: List<JSONObject>,
        sourceLabel: String,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): MaimaiUploadResult {
        val totalSteps = (records.size + 1).coerceAtLeast(1)
        onProgress(
            MaimaiUploadProgress(
                0,
                totalSteps,
                "即将清空水鱼 maimai 成绩，并用$sourceLabel ${records.size} 条成绩重建",
            ),
        )
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
            onProgress(MaimaiUploadProgress(1, 1, "水鱼旧成绩已清空，本地没有可上传成绩"))
            val verifyResult = verifyDivingFishMatchesLocalJson(token, records, onProgress)
                .getOrElse { error ->
                    return MaimaiUploadResult(
                        platform = MaimaiUploadPlatform.DIVING_FISH,
                        success = false,
                        statusCode = deleteResponse.statusCode,
                        uploadedScoreCount = 0,
                        message = "Diving Fish cleared cloud records, but verification failed: ${safeThrowableMessage(error)}",
                    )
                }
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = verifyResult.isEmpty,
                statusCode = deleteResponse.statusCode,
                uploadedScoreCount = 0,
                message = "Diving Fish mirrored 0 local records after clearing cloud records. ${verifyResult.summaryText()}",
                syncDiff = verifyResult,
            )
        }

        onProgress(
            MaimaiUploadProgress(
                1,
                totalSteps,
                "水鱼旧成绩已清空，正在重传$sourceLabel ${records.size} 条成绩",
            ),
        )
        val uploadResult = uploadDivingFishJsonRecordsAdaptively(
            token = token,
            records = records,
            completedStepOffset = 1,
            totalSteps = totalSteps,
            progressPrefix = "水鱼镜像",
            allowSkippingSingleRecords = false,
            onProgress = onProgress,
        )

        if (!uploadResult.success) {
            return MaimaiUploadResult(
                platform = MaimaiUploadPlatform.DIVING_FISH,
                success = false,
                statusCode = uploadResult.statusCode,
                uploadedScoreCount = uploadResult.uploadedCount,
                message = "Diving Fish cloud records were cleared, but upload did not complete. " +
                    "Please retry upload/rebuild. ${uploadResult.message}",
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
                    message = "Diving Fish mirrored ${uploadResult.uploadedCount} local records, " +
                        "but verification failed: ${safeThrowableMessage(error)}",
                    updatedCount = uploadResult.updatedCount,
                    createdCount = uploadResult.createdCount,
                )
            }

        return MaimaiUploadResult(
            platform = MaimaiUploadPlatform.DIVING_FISH,
            success = verifyResult.isEmpty,
            statusCode = uploadResult.statusCode,
            uploadedScoreCount = uploadResult.uploadedCount,
            message = "Diving Fish mirrored ${uploadResult.uploadedCount} local records after clearing cloud records " +
                "(updates=${uploadResult.updatedCount}, creates=${uploadResult.createdCount}). ${verifyResult.summaryText()}",
            updatedCount = uploadResult.updatedCount,
            createdCount = uploadResult.createdCount,
            syncDiff = verifyResult,
        )
    }

    private fun List<ScoreRecord>.toDivingFishJsonObjects(): List<JSONObject> {
        val array = JSONArray(exporter.toDivingFishUpdateRecordsJson(this))
        return buildList {
            array.forEachObject { add(it) }
        }
    }

    private fun Int.shouldSplitDivingFishChunk(): Boolean =
        this == 0 || this == 413 || this == 429 || this in 500..599

    private fun Int.shouldSkipDivingFishRecord(): Boolean =
        this in 500..599

    private fun MaimaiUploadHttpResponse.shouldSkipDivingFishRecord(token: String): Boolean =
        when {
            statusCode.shouldSkipDivingFishRecord() -> true
            statusCode == 0 && body.contains("timeout", ignoreCase = true) -> {
                postDivingFishProbe(token).statusCode in 200..299
            }
            else -> false
        }

    private fun postDivingFishProbe(token: String): MaimaiUploadHttpResponse =
        try {
            transport.post(
                MaimaiUploadHttpRequest(
                    url = DIVING_FISH_UPDATE_RECORDS_URL,
                    headers = jsonHeaders + ("Import-Token" to token),
                    body = "[]",
                    readTimeoutMs = PROBE_READ_TIMEOUT_MS,
                    maxAttempts = 1,
                ),
            )
        } catch (error: Exception) {
            MaimaiUploadHttpResponse(statusCode = 0, body = safeThrowableMessage(error))
        }

    private fun List<DivingFishSkippedRecord>.failureSuffix(): String =
        if (isEmpty()) {
            ""
        } else {
            " Already skipped ${size} record(s): ${takeSkippedLabels()}."
        }

    private fun List<DivingFishSkippedRecord>.summaryText(): String =
        "skipped ${size} record(s): ${takeSkippedLabels()}."

    private fun List<DivingFishSkippedRecord>.takeSkippedLabels(): String {
        val labels = take(MAX_SKIPPED_RECORD_LABELS_IN_MESSAGE).joinToString("; ") { skipped ->
            "${skipped.record.uploadLabel()} (HTTP ${skipped.statusCode}, ${skipped.reason.limitForMessage(80)})"
        }
        val remaining = size - MAX_SKIPPED_RECORD_LABELS_IN_MESSAGE
        return if (remaining > 0) {
            "$labels; +$remaining more"
        } else {
            labels
        }
    }

    private fun JSONObject.uploadLabel(): String {
        val title = optString("title", "unknown").trim().ifBlank { "unknown" }.limitForMessage(48)
        val type = optString("type", "?").trim().ifBlank { "?" }
        val levelIndex = if (has("level_index")) optInt("level_index") else -1
        return "$title/$type/$levelIndex"
    }

    private fun String.limitForMessage(maxLength: Int): String =
        if (length <= maxLength) {
            this
        } else {
            take((maxLength - 3).coerceAtLeast(0)) + "..."
        }

    fun uploadWahlapPagesToDivingFish(
        importToken: String,
        pages: List<DivingFishWahlapPage>,
        onProgress: (MaimaiUploadProgress) -> Unit = {},
    ): MaimaiUploadResult {
        val token = requireToken(importToken, "Diving Fish import token")
        require(pages.isNotEmpty()) { "Wahlap score pages are required." }

        var uploadedScoreCount = 0
        var lastStatusCode = 200
        val totalSteps = pages.size * STEPS_PER_PAGE
        var completedSteps = 0

        onProgress(MaimaiUploadProgress(completedSteps, totalSteps, "准备上传 ${pages.size} 个华立成绩页到水鱼"))

        for (page in pages) {
            val parsedPageResult = parseWahlapPage(
                page = page,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                onProgress = onProgress,
            )
            if (parsedPageResult.isFailure) {
                onProgress(
                    MaimaiUploadProgress(
                        completedSteps,
                        totalSteps,
                        "${page.label} 解析失败，尝试 update_records_html 直传",
                    ),
                )
                val directResult = uploadWahlapPageHtmlDirect(token, page)
                lastStatusCode = directResult.statusCode
                if (directResult.success) {
                    completedSteps += STEPS_PER_PAGE
                    uploadedScoreCount += directResult.uploadedCount
                    onProgress(
                        MaimaiUploadProgress(
                            completedSteps,
                            totalSteps,
                            "${page.label} 直传完成，累计 $uploadedScoreCount 条",
                        ),
                    )
                    continue
                }
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = directResult.statusCode,
                    uploadedScoreCount = uploadedScoreCount,
                    message = "Diving Fish parser failed on ${page.label}: " +
                        "${safeThrowableMessage(parsedPageResult.exceptionOrNull())}. " +
                        "Direct HTML fallback: ${directResult.message}",
                )
            }

            val parsedPage = parsedPageResult.getOrThrow()
            lastStatusCode = parsedPage.statusCode
            completedSteps += 1
            onProgress(
                MaimaiUploadProgress(
                    completedSteps,
                    totalSteps,
                    "${page.label} 解析完成：${parsedPage.recordCount} 条（${parsedPage.parserName}）",
                ),
            )
            if (parsedPage.recordCount == 0) {
                completedSteps += 1
                onProgress(
                    MaimaiUploadProgress(
                        completedSteps,
                        totalSteps,
                        "${page.label} 没有可上传记录，跳过",
                    ),
                )
                continue
            }

            onProgress(
                MaimaiUploadProgress(
                    completedSteps,
                    totalSteps,
                    "正在上传 ${page.label}：${parsedPage.recordCount} 条记录",
                ),
            )
            val updateResponse = try {
                transport.post(
                    MaimaiUploadHttpRequest(
                        url = DIVING_FISH_UPDATE_RECORDS_URL,
                        headers = jsonHeaders + ("Import-Token" to token),
                        body = parsedPage.body,
                        readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                        maxAttempts = UPDATE_MAX_ATTEMPTS,
                    ),
                )
            } catch (error: Exception) {
                onProgress(
                    MaimaiUploadProgress(
                        completedSteps,
                        totalSteps,
                        "${page.label} update_records 请求失败，尝试直传",
                    ),
                )
                val directResult = uploadWahlapPageHtmlDirect(token, page)
                lastStatusCode = directResult.statusCode
                if (directResult.success) {
                    completedSteps += 1
                    uploadedScoreCount += directResult.uploadedCount
                    onProgress(
                        MaimaiUploadProgress(
                            completedSteps,
                            totalSteps,
                            "${page.label} 直传完成，累计 $uploadedScoreCount 条",
                        ),
                    )
                    continue
                }
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = directResult.statusCode,
                    uploadedScoreCount = uploadedScoreCount,
                    message = "Diving Fish update_records request failed on ${page.label} " +
                        "after ${parsedPage.parserName}: ${safeThrowableMessage(error)}. " +
                        "Direct HTML fallback: ${directResult.message}",
                )
            }

            lastStatusCode = updateResponse.statusCode
            if (updateResponse.statusCode !in 200..299) {
                onProgress(
                    MaimaiUploadProgress(
                        completedSteps,
                        totalSteps,
                        "${page.label} update_records 返回 HTTP ${updateResponse.statusCode}，尝试直传",
                    ),
                )
                val directResult = uploadWahlapPageHtmlDirect(token, page)
                if (directResult.success) {
                    lastStatusCode = directResult.statusCode
                    completedSteps += 1
                    uploadedScoreCount += directResult.uploadedCount
                    onProgress(
                        MaimaiUploadProgress(
                            completedSteps,
                            totalSteps,
                            "${page.label} 直传完成，累计 $uploadedScoreCount 条",
                        ),
                    )
                    continue
                }
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = directResult.statusCode.takeIf { it != 0 } ?: updateResponse.statusCode,
                    uploadedScoreCount = uploadedScoreCount,
                    message = httpMessage(
                        "Diving Fish update_records failed on ${page.label} after ${parsedPage.parserName}",
                        updateResponse.statusCode,
                        updateResponse.body,
                    ) + ". Direct HTML fallback: ${directResult.message}",
                )
            }

            uploadedScoreCount += parsedPage.recordCount
            completedSteps += 1
            onProgress(
                MaimaiUploadProgress(
                    completedSteps,
                    totalSteps,
                    "${page.label} 上传完成，累计 $uploadedScoreCount 条",
                ),
            )
        }

        return MaimaiUploadResult(
            platform = MaimaiUploadPlatform.DIVING_FISH,
            success = true,
            statusCode = lastStatusCode,
            uploadedScoreCount = uploadedScoreCount,
            message = "Diving Fish HTML uploaded $uploadedScoreCount records from ${pages.size} pages.",
        )
    }

    fun rebuildDivingFishRecordsFromWahlapPages(
        importToken: String,
        pages: List<DivingFishWahlapPage>,
        recordsToRemove: List<DivingFishRecordIdentifier>,
        supplementalScores: List<ScoreRecord> = emptyList(),
        onProgress: (MaimaiUploadProgress) -> Unit = {},
    ): MaimaiUploadResult {
        val token = requireToken(importToken, "Diving Fish import token")
        require(pages.isNotEmpty()) { "Wahlap score pages are required." }

        val totalSteps = pages.size + 3
        var completedSteps = 0
        val freshRecords = mutableListOf<JSONObject>()
        if (supplementalScores.isNotEmpty()) {
            JSONArray(exporter.toDivingFishUpdateRecordsJson(supplementalScores)).forEachObject { freshRecords += it }
        }

        onProgress(MaimaiUploadProgress(0, totalSteps, "正在解析本次华立成绩页"))
        for (page in pages) {
            val parsedPage = parseWahlapPage(
                page = page,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                onProgress = onProgress,
            ).getOrElse { error ->
                return MaimaiUploadResult(
                    platform = MaimaiUploadPlatform.DIVING_FISH,
                    success = false,
                    statusCode = 0,
                    uploadedScoreCount = freshRecords.size,
                    message = "Diving Fish rebuild parser failed on ${page.label}: ${safeThrowableMessage(error)}",
                )
            }
            JSONArray(parsedPage.body).forEachObject { freshRecords += it }
            completedSteps += 1
            onProgress(
                MaimaiUploadProgress(
                    completedSteps,
                    totalSteps,
                    "${page.label} 解析完成：${parsedPage.recordCount} 条",
                ),
            )
        }

        return replaceDivingFishRecordsWithFreshRecords(
            token = token,
            records = freshRecords,
            sourceLabel = "本次导入",
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
        onProgress(MaimaiUploadProgress(0, 1, "正在上传 ${scores.size} 条本地成绩到 LXNS"))
        val response = transport.post(
            MaimaiUploadHttpRequest(
                url = LXNS_UPDATE_RECORDS_URL,
                headers = jsonHeaders + ("X-User-Token" to token),
                body = body,
                readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                maxAttempts = UPDATE_MAX_ATTEMPTS,
            ),
        )
        onProgress(MaimaiUploadProgress(1, 1, "LXNS 上传完成，HTTP ${response.statusCode}"))
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

    private fun requireToken(value: String, label: String): String =
        value.trim().ifBlank {
            throw IllegalArgumentException("$label is required.")
        }

    private fun lxnsSuccess(body: String): Boolean? =
        runCatching { JSONObject(body).optBoolean("success") }.getOrNull()

    private fun lxnsMessage(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrDefault("")

    fun diffDivingFishRecords(
        localRecords: List<JSONObject>,
        cloudRecords: List<JSONObject>,
    ): DivingFishSyncDiff {
        val localByKey = localRecords
            .mapNotNull { it.comparableRecordOrNull() }
            .associateBy { it.key }
        val cloudByKey = cloudRecords
            .mapNotNull { it.comparableRecordOrNull() }
            .associateBy { it.key }

        val localKeys = localByKey.keys
        val cloudKeys = cloudByKey.keys
        val cloudOnly = (cloudKeys - localKeys).sortedDivingFishIdentifiers()
        val localOnly = (localKeys - cloudKeys).sortedDivingFishIdentifiers()
        val mismatches = localKeys
            .intersect(cloudKeys)
            .mapNotNull { key ->
                val local = localByKey.getValue(key)
                val cloud = cloudByKey.getValue(key)
                if (local.hasSameValuesAs(cloud)) {
                    null
                } else {
                    DivingFishValueMismatch(
                        key = key,
                        localSummary = local.valueSummary(),
                        cloudSummary = cloud.valueSummary(),
                    )
                }
            }
            .sortedBy { it.key.displayLabel() }

        return DivingFishSyncDiff(
            cloudOnly = cloudOnly,
            localOnly = localOnly,
            valueMismatches = mismatches,
        )
    }

    private fun verifyDivingFishMatchesLocalJson(
        token: String,
        localRecords: List<JSONObject>,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): Result<DivingFishSyncDiff> {
        onProgress(MaimaiUploadProgress(0, 1, "正在拉取水鱼云端全量记录做 diff 校验"))
        return fetchDivingFishRecords(token).map { cloudRecords ->
            val diff = diffDivingFishRecords(localRecords, cloudRecords)
            onProgress(
                MaimaiUploadProgress(
                    1,
                    1,
                    if (diff.isEmpty) {
                        "水鱼云端与本地完全一致"
                    } else {
                        diff.summaryText()
                    },
                ),
            )
            diff
        }
    }

    private fun fetchDivingFishRecords(token: String): Result<List<JSONObject>> =
        runCatching {
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
            val records = JSONObject(response.body).getJSONArray("records")
            buildList {
                records.forEachObject { add(it) }
            }
        }

    private fun JSONObject.comparableRecordOrNull(): DivingFishComparableRecord? {
        val key = recordIdentifierOrNull() ?: return null
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
        return achievementMatches &&
            dxScore == other.dxScore &&
            fc == other.fc &&
            fs == other.fs
    }

    private fun MaimaiUploadHttpResponse.updateRecordsCounts(): Pair<Int, Int> =
        runCatching {
            val body = JSONObject(body)
            body.optInt("updates", 0) to body.optInt("creates", 0)
        }.getOrDefault(0 to 0)

    private fun JSONObject.optionalString(name: String): String =
        if (has(name) && !isNull(name)) optString(name) else ""

    private fun Collection<DivingFishRecordIdentifier>.sortedDivingFishIdentifiers(): List<DivingFishRecordIdentifier> =
        sortedWith(
            compareBy<DivingFishRecordIdentifier> { it.title.lowercase() }
                .thenBy { it.type }
                .thenBy { it.levelIndex },
        )

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

    private fun parseWahlapPage(
        page: DivingFishWahlapPage,
        completedSteps: Int,
        totalSteps: Int,
        onProgress: (MaimaiUploadProgress) -> Unit,
    ): Result<DivingFishParsedPage> {
        val failures = mutableListOf<String>()

        pageParserEndpoints.forEach { endpoint ->
            onProgress(
                MaimaiUploadProgress(
                    completedSteps,
                    totalSteps,
                    "正在解析 ${page.label}：${endpoint.name}",
                ),
            )
            val response = try {
                transport.post(
                    MaimaiUploadHttpRequest(
                        url = endpoint.url,
                        headers = plainTextHeaders,
                        body = page.html,
                        readTimeoutMs = PARSER_READ_TIMEOUT_MS,
                        maxAttempts = PARSER_MAX_ATTEMPTS,
                    ),
                )
            } catch (error: Exception) {
                failures += "${endpoint.name}: ${safeThrowableMessage(error)}"
                return@forEach
            }

            if (response.statusCode !in 200..299) {
                failures += httpMessage(endpoint.name, response.statusCode, response.body)
                return@forEach
            }

            val parsedBody = response.body.trim()
            val parsedCount = try {
                JSONArray(parsedBody).length()
            } catch (error: Exception) {
                failures += "${endpoint.name}: invalid JSON ${error::class.java.simpleName}"
                return@forEach
            }

            return Result.success(
                DivingFishParsedPage(
                    body = parsedBody,
                    recordCount = parsedCount,
                    statusCode = response.statusCode,
                    parserName = endpoint.name,
                ),
            )
        }

        return Result.failure(
            IllegalStateException(failures.joinToString("; ").ifBlank { "no parser endpoint was attempted" }),
        )
    }

    private fun uploadWahlapPageHtmlDirect(
        token: String,
        page: DivingFishWahlapPage,
    ): DivingFishDirectHtmlResult {
        val response = try {
            transport.post(
                MaimaiUploadHttpRequest(
                    url = DIVING_FISH_UPDATE_RECORDS_HTML_URL,
                    headers = htmlHeaders + ("Import-Token" to token),
                    body = page.html,
                    readTimeoutMs = UPDATE_READ_TIMEOUT_MS,
                    maxAttempts = UPDATE_MAX_ATTEMPTS,
                ),
            )
        } catch (error: Exception) {
            return DivingFishDirectHtmlResult(
                success = false,
                statusCode = 0,
                uploadedCount = 0,
                message = "update_records_html request failed on ${page.label}: ${safeThrowableMessage(error)}",
            )
        }

        if (response.statusCode !in 200..299) {
            return DivingFishDirectHtmlResult(
                success = false,
                statusCode = response.statusCode,
                uploadedCount = 0,
                message = httpMessage("update_records_html failed on ${page.label}", response.statusCode, response.body),
            )
        }

        val uploadedCount = runCatching {
            val body = JSONObject(response.body)
            body.optInt("updates", 0) + body.optInt("creates", 0)
        }.getOrDefault(0)
        return DivingFishDirectHtmlResult(
            success = true,
            statusCode = response.statusCode,
            uploadedCount = uploadedCount,
            message = safeMessage(response.body).ifBlank { "HTTP ${response.statusCode}" },
        )
    }

    private fun httpMessage(stage: String, statusCode: Int, body: String): String {
        val message = safeMessage(body)
        return if (message.isBlank()) {
            "$stage: HTTP $statusCode"
        } else {
            "$stage: HTTP $statusCode: $message"
        }
    }

    private fun safeThrowableMessage(error: Throwable?): String =
        if (error == null) {
            "unknown error"
        } else {
            safeMessage(error.message ?: error::class.java.simpleName).ifBlank {
                error::class.java.simpleName
            }
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

    private fun JSONObject.recordIdentifierOrNull(): DivingFishRecordIdentifier? {
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val type = optString("type").takeIf { it.isNotBlank() } ?: return null
        if (!has("level_index")) return null
        return DivingFishRecordIdentifier(
            title = title,
            type = type,
            levelIndex = optInt("level_index"),
        ).normalized()
    }

    private fun DivingFishRecordIdentifier.normalized(): DivingFishRecordIdentifier =
        copy(
            title = title.trim(),
            type = when (type.trim().uppercase()) {
                "STANDARD" -> "SD"
                "STD" -> "SD"
                else -> type.trim().uppercase()
            },
        )

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(block)
        }
    }

    private companion object {
        private const val DIVING_FISH_UPDATE_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
        private const val DIVING_FISH_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/records"
        private const val DIVING_FISH_DELETE_RECORDS_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/delete_records"
        private const val DIVING_FISH_UPDATE_RECORDS_HTML_URL =
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records_html"
        private const val DIVING_FISH_LEGACY_PAGEPARSER_URL =
            "http://www.diving-fish.com:8089/page"
        private const val DIVING_FISH_HTTPS_PAGEPARSER_URL =
            "https://www.diving-fish.com/api/pageparser/page"
        private const val LXNS_UPDATE_RECORDS_URL =
            "https://maimai.lxns.net/api/v0/user/maimai/player/scores"
        private const val MAX_MESSAGE_LENGTH = 240
        private const val STEPS_PER_PAGE = 2
        private const val PARSER_READ_TIMEOUT_MS = 35_000
        private const val UPDATE_READ_TIMEOUT_MS = 20_000
        private const val PROBE_READ_TIMEOUT_MS = 5_000
        private const val PARSER_MAX_ATTEMPTS = 2
        private const val UPDATE_MAX_ATTEMPTS = 2
        private const val DIVING_FISH_UPDATE_BATCH_SIZE = 200
        private const val MAX_DIVING_FISH_SKIPPED_RECORDS = 100
        private const val MAX_SKIPPED_RECORD_LABELS_IN_MESSAGE = 5

        private val jsonHeaders = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json; charset=utf-8",
        )

        private val acceptJsonHeaders = mapOf(
            "Accept" to "application/json",
        )

        private val plainTextHeaders = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "text/plain; charset=utf-8",
        )

        private val htmlHeaders = mapOf(
            "Accept" to "application/json",
            "Content-Type" to "text/html; charset=utf-8",
        )

        private val pageParserEndpoints = listOf(
            DivingFishPageParserEndpoint(
                name = "8089/page",
                url = DIVING_FISH_LEGACY_PAGEPARSER_URL,
            ),
            DivingFishPageParserEndpoint(
                name = "HTTPS pageparser",
                url = DIVING_FISH_HTTPS_PAGEPARSER_URL,
            ),
        )
    }
}
