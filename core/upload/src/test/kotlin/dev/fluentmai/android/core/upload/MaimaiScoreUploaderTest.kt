package dev.fluentmai.android.core.upload

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MaimaiScoreUploaderTest {
    @Test
    fun divingFishUploadBuildsExpectedRequest() {
        val uploadedScore = score(songType = SongType.DX)
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(listOf(uploadedScore)),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish("synthetic-fish-token", listOf(uploadedScore))

        assertEquals(true, result.success)
        assertEquals(MaimaiUploadPlatform.DIVING_FISH, result.platform)
        assertEquals(listOf("POST", "GET"), transport.requests.map { it.method })
        val request = transport.requests[0]
        assertEquals("https://www.diving-fish.com/api/maimaidxprober/player/update_records", request.url)
        assertEquals("synthetic-fish-token", request.headers["Import-Token"])
        val body = JSONArray(request.body).getJSONObject(0)
        assertEquals("DX", body.getString("type"))
        assertEquals(3120, body.getInt("dxScore"))
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.createdCount)
        assertEquals(true, result.syncDiff?.isEmpty)
    }

    @Test
    fun divingFishUploadSplitsServerErrorBatchAndUploadsSmallerBatches() {
        val scores = listOf(score(title = "Synthetic Song A"), score(title = "Synthetic Song B"))
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(500, "temporary server error"),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(scores),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish("synthetic-fish-token", scores)

        assertEquals(true, result.success)
        assertEquals(2, result.uploadedScoreCount)
        assertEquals(4, transport.requests.size)
        assertEquals(2, JSONArray(transport.requests[0].body).length())
        assertEquals(1, JSONArray(transport.requests[1].body).length())
        assertEquals(1, JSONArray(transport.requests[2].body).length())
        assertEquals("GET", transport.requests[3].method)
    }

    @Test
    fun divingFishUploadDoesNotSplitNonRetryableTokenFailure() {
        val transport = RecordingTransport(MaimaiUploadHttpResponse(401, """{"message":"invalid token"}"""))
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "synthetic-fish-token",
            listOf(score(title = "Synthetic Song A"), score(title = "Synthetic Song B")),
        )

        assertEquals(false, result.success)
        assertEquals(401, result.statusCode)
        assertEquals(0, result.uploadedScoreCount)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun lxnsUploadBuildsExpectedRequestAndReadsApiFailure() {
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"success":false,"message":"invalid token"}"""),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToLxns("synthetic-lxns-token", listOf(score(songId = 834)))

        assertEquals(false, result.success)
        assertEquals("invalid token", result.message)
        val request = transport.singleRequest()
        assertEquals("https://maimai.lxns.net/api/v0/user/maimai/player/scores", request.url)
        assertEquals("synthetic-lxns-token", request.headers["X-User-Token"])
        val body = JSONObject(request.body).getJSONArray("scores").getJSONObject(0)
        assertEquals(834, body.getInt("id"))
        assertEquals("standard", body.getString("type"))
        assertEquals(3120, body.getInt("dx_score"))
    }
    @Test
    fun blankTokensAreRejectedBeforeNetworkCall() {
        val transport = RecordingTransport(MaimaiUploadHttpResponse(200, "{}"))
        val uploader = MaimaiScoreUploader(transport = transport)

        assertThrows(IllegalArgumentException::class.java) {
            uploader.uploadToDivingFish("   ", listOf(score()))
        }

        assertEquals(0, transport.requests.size)
    }

    @Test
    fun responseMessagesAreRedacted() {
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(
                500,
                "Import-Token=synthetic-secret-token https://example.test/private?token=synthetic-secret",
            ),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish("synthetic-fish-token", listOf(score()))

        assertEquals(false, result.success)
        assertEquals(false, result.message.contains("synthetic-secret-token"))
        assertEquals(false, result.message.contains("https://"))
        assertEquals(true, result.message.contains("[REDACTED_SECRET]"))
        assertEquals(true, result.message.contains("[REDACTED_URL]"))
    }

    @Test
    fun divingFishDiffReportsLocalOnlyRecords() {
        val uploader = MaimaiScoreUploader(transport = RecordingTransport(MaimaiUploadHttpResponse(200, "{}")))
        val localScores = (1..5).map { index -> score(title = "Synthetic Song $index") }
        val cloudScores = localScores.take(3)

        val diff = uploader.diffDivingFishRecords(
            localRecords = localScores.map(::divingFishRecord),
            cloudRecords = cloudScores.map(::divingFishRecord),
        )

        assertEquals(false, diff.isEmpty)
        assertEquals(0, diff.cloudOnly.size)
        assertEquals(2, diff.localOnly.size)
        assertEquals(listOf("Synthetic Song 4", "Synthetic Song 5"), diff.localOnly.map { it.title })
        assertEquals(true, diff.summaryText().contains("local-only=2"))
    }

    private fun score(
        songId: Int? = 834,
        songType: SongType = SongType.STANDARD,
        title: String = "Synthetic Score Song",
        difficulty: Difficulty = Difficulty.MASTER,
        levelIndex: Int = difficulty.levelIndex,
        achievement: Double = 100.5,
        dxScore: Int? = 3120,
        fc: String? = "fc",
        fs: String? = "fs",
    ): ScoreRecord =
        ScoreRecord(
            id = "score-$title-${songType.name}-$levelIndex",
            songId = songId,
            title = title,
            songType = songType,
            difficulty = difficulty,
            level = "14+",
            levelIndex = levelIndex,
            achievement = achievement,
            dxScore = dxScore,
            fc = fc,
            fs = fs,
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )

    private fun cloudRecords(scores: List<ScoreRecord>): MaimaiUploadHttpResponse =
        MaimaiUploadHttpResponse(
            statusCode = 200,
            body = JSONObject()
                .put("records", JSONArray().apply { scores.forEach { put(divingFishRecord(it)) } })
                .toString(),
        )

    private fun divingFishRecord(score: ScoreRecord): JSONObject =
        JSONObject()
            .put("achievements", score.achievement)
            .put("dxScore", score.dxScore ?: 0)
            .put("fc", score.fc.orEmpty().trim().lowercase())
            .put("fs", score.fs.orEmpty().trim().lowercase())
            .put("level_index", score.levelIndex)
            .put("title", score.title)
            .put("type", score.songType.divingFishName)
}

private class RecordingTransport(
    private vararg val steps: Any,
) : MaimaiUploadTransport {
    val requests = mutableListOf<MaimaiUploadHttpRequest>()
    private var responseIndex = 0

    override fun execute(request: MaimaiUploadHttpRequest): MaimaiUploadHttpResponse {
        requests += request
        val index = responseIndex.coerceAtMost(steps.lastIndex)
        responseIndex += 1
        return when (val step = steps[index]) {
            is MaimaiUploadHttpResponse -> step
            is IOException -> throw step
            is RuntimeException -> throw step
            is Exception -> throw RuntimeException(step)
            else -> error("Unsupported transport step: $step")
        }
    }

    fun singleRequest(): MaimaiUploadHttpRequest =
        requests.single()
}
