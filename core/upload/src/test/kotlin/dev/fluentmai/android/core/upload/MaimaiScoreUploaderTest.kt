package dev.fluentmai.android.core.upload

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class MaimaiScoreUploaderTest {
    @Test
    fun divingFishUploadBuildsExpectedRequest() {
        val uploadedScore = score(songType = SongType.DX)
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(listOf(uploadedScore)),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish("fish-token", listOf(uploadedScore))

        assertEquals(true, result.success)
        assertEquals(MaimaiUploadPlatform.DIVING_FISH, result.platform)
        assertEquals(listOf("POST", "GET"), transport.requests.map { it.method })
        val request = transport.requests[0]
        assertEquals(
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records",
            request.url,
        )
        assertEquals(false, transport.requests.any { it.method == "DELETE" })
        assertEquals("fish-token", request.headers["Import-Token"])
        val body = JSONArray(request.body).getJSONObject(0)
        assertEquals("DX", body.getString("type"))
        assertEquals(3120, body.getInt("dxScore"))
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.createdCount)
        assertEquals(true, result.syncDiff?.isEmpty)
    }

    @Test
    fun divingFishUploadSplitsServerErrorBatchAndUploadsSmallerBatches() {
        val scores = listOf(score(title = "Bad Apple!!"), score(title = "Good bye, Merry-Go-Round."))
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(500, "temporary server error"),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(scores),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "fish-token",
            scores,
        )

        assertEquals(true, result.success)
        assertEquals(2, result.uploadedScoreCount)
        assertEquals(4, transport.requests.size)
        assertEquals(2, JSONArray(transport.requests[0].body).length())
        assertEquals(1, JSONArray(transport.requests[1].body).length())
        assertEquals(1, JSONArray(transport.requests[2].body).length())
        assertEquals("GET", transport.requests[3].method)
    }

    @Test
    fun divingFishUploadSplitsTimeoutBatchAndUploadsSmallerBatches() {
        val scores = listOf(score(title = "Slow Song"), score(title = "Healthy Song"))
        val transport = RecordingTransport(
            IOException("timeout"),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(scores),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "fish-token",
            scores,
        )

        assertEquals(true, result.success)
        assertEquals(2, result.uploadedScoreCount)
        assertEquals(4, transport.requests.size)
        assertEquals(2, JSONArray(transport.requests[0].body).length())
        assertEquals(1, JSONArray(transport.requests[1].body).length())
        assertEquals(1, JSONArray(transport.requests[2].body).length())
        assertEquals("GET", transport.requests[3].method)
    }

    @Test
    fun divingFishUploadSkipsSingleRecordThatKeepsReturningServerError() {
        val healthyScore = score(title = "Healthy Song")
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(500, "batch failed"),
            MaimaiUploadHttpResponse(500, "Import-Token=secret-token server exploded"),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(listOf(healthyScore)),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "fish-token",
            listOf(score(title = "Broken Song"), healthyScore),
        )

        assertEquals(false, result.success)
        assertEquals(200, result.statusCode)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals(true, result.message.contains("skipped 1 record"))
        assertEquals(true, result.message.contains("local-only=1"))
        assertEquals(true, result.message.contains("Broken Song"))
        assertEquals(false, result.message.contains("secret-token"))
        assertEquals(true, result.message.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun divingFishUploadSkipsSingleRecordTimeoutWhenProbeSucceeds() {
        val healthyScore = score(title = "Healthy Song")
        val transport = RecordingTransport(
            IOException("timeout"),
            IOException("SocketTimeoutException: timeout"),
            MaimaiUploadHttpResponse(200, """{"updates":0,"creates":0}"""),
            MaimaiUploadHttpResponse(200, """{"updates":1,"creates":0}"""),
            cloudRecords(listOf(healthyScore)),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "fish-token",
            listOf(score(title = "Acceleration"), healthyScore),
        )

        assertEquals(false, result.success)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals(true, result.message.contains("skipped 1 record"))
        assertEquals(true, result.message.contains("local-only=1"))
        assertEquals(true, result.message.contains("Acceleration"))
        assertEquals(5, transport.requests.size)
        assertEquals("[]", transport.requests[2].body)
        assertEquals("GET", transport.requests[4].method)
    }

    @Test
    fun divingFishUploadDoesNotSplitNonRetryableTokenFailure() {
        val transport = RecordingTransport(MaimaiUploadHttpResponse(401, """{"message":"invalid token"}"""))
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish(
            "fish-token",
            listOf(score(title = "Song A"), score(title = "Song B")),
        )

        assertEquals(false, result.success)
        assertEquals(401, result.statusCode)
        assertEquals(0, result.uploadedScoreCount)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun divingFishWahlapPageUploadRunsPageParserThenUpdateRecords() {
        val parsedBody =
            """[{"title":"Åntinomiε","type":"DX","level_index":2,"achievements":100.5386,"dxScore":1234,"fc":"","fs":"sync"}]"""
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, parsedBody),
            MaimaiUploadHttpResponse(200, """{"ok":true}"""),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadWahlapPagesToDivingFish(
            importToken = "fish-token",
            pages = listOf(DivingFishWahlapPage(label = "EXPERT", html = "<html>wahlap expert page</html>")),
        )

        assertEquals(true, result.success)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals(2, transport.requests.size)
        assertEquals("http://www.diving-fish.com:8089/page", transport.requests[0].url)
        assertEquals("text/plain; charset=utf-8", transport.requests[0].headers["Content-Type"])
        assertEquals("<html>wahlap expert page</html>", transport.requests[0].body)
        assertEquals(
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records",
            transport.requests[1].url,
        )
        assertEquals("fish-token", transport.requests[1].headers["Import-Token"])
        assertEquals(parsedBody, transport.requests[1].body)
    }

    @Test
    fun divingFishWahlapPageUploadFallsBackToHttpsParserWhenLegacyParserFails() {
        val parsedBody =
            """[{"title":"Åntinomiε","type":"DX","level_index":2,"achievements":100.5386,"dxScore":1234,"fc":"","fs":"sync"}]"""
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(502, "bad gateway"),
            MaimaiUploadHttpResponse(200, parsedBody),
            MaimaiUploadHttpResponse(200, """{"ok":true}"""),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadWahlapPagesToDivingFish(
            importToken = "fish-token",
            pages = listOf(DivingFishWahlapPage(label = "EXPERT", html = "<html>wahlap expert page</html>")),
        )

        assertEquals(true, result.success)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals("http://www.diving-fish.com:8089/page", transport.requests[0].url)
        assertEquals("https://www.diving-fish.com/api/pageparser/page", transport.requests[1].url)
        assertEquals(
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records",
            transport.requests[2].url,
        )
    }

    @Test
    fun divingFishWahlapPageUploadFailsWhenPageParserReturnsInvalidJson() {
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"error":"not a list"}"""),
            IOException("timeout"),
            MaimaiUploadHttpResponse(401, """{"message":"invalid token"}"""),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadWahlapPagesToDivingFish(
            importToken = "fish-token",
            pages = listOf(DivingFishWahlapPage(label = "MASTER", html = "<html></html>")),
        )

        assertEquals(false, result.success)
        assertEquals(true, result.message.contains("invalid JSON"))
        assertEquals(true, result.message.contains("HTTPS pageparser: timeout"))
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun divingFishRebuildFromPagesDeletesAllThenUploadsOnlyFreshRecords() {
        val freshBody =
            """[{"title":"はいよろこんで","type":"DX","level_index":4,"achievements":100.6215,"dxScore":2275,"fc":"","fs":"sync"}]"""
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, freshBody),
            MaimaiUploadHttpResponse(200, """{"message":2}"""),
            MaimaiUploadHttpResponse(200, """{"updates":0,"creates":1}"""),
            cloudRecordsFromJsonArray(freshBody),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.rebuildDivingFishRecordsFromWahlapPages(
            importToken = "fish-token",
            pages = listOf(DivingFishWahlapPage(label = "RE_MASTER", html = "<html>wahlap remaster page</html>")),
            recordsToRemove = listOf(DivingFishRecordIdentifier("PANDORA PARADOXXX", "SD", 3)),
        )

        assertEquals(true, result.success)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals("POST", transport.requests[0].method)
        assertEquals("DELETE", transport.requests[1].method)
        assertEquals("https://www.diving-fish.com/api/maimaidxprober/player/delete_records", transport.requests[1].url)
        assertEquals("POST", transport.requests[2].method)
        assertEquals("GET", transport.requests[3].method)

        val rebuiltBody = JSONArray(transport.requests[2].body)
        val titles = (0 until rebuiltBody.length()).map { rebuiltBody.getJSONObject(it).getString("title") }
        assertEquals(false, titles.contains("PANDORA PARADOXXX"))
        assertEquals(true, titles.contains("はいよろこんで"))
    }

    @Test
    fun divingFishRebuildKeepsFreshLocalRecordWhenItMatchesCloudRemovalList() {
        val freshScore = score(title = "Destr0yer", songType = SongType.STANDARD)
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"message":1}"""),
            MaimaiUploadHttpResponse(200, """{"updates":0,"creates":1}"""),
            cloudRecords(listOf(freshScore)),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.rebuildDivingFishRecords(
            importToken = "fish-token",
            freshScores = listOf(freshScore),
            recordsToRemove = listOf(DivingFishRecordIdentifier("Destr0yer", "SD", 3)),
        )

        assertEquals(true, result.success)
        assertEquals(1, result.uploadedScoreCount)
        assertEquals("DELETE", transport.requests[0].method)
        assertEquals("POST", transport.requests[1].method)
        assertEquals("GET", transport.requests[2].method)
        val rebuiltRecord = JSONArray(transport.requests[1].body).getJSONObject(0)
        assertEquals("Destr0yer", rebuiltRecord.getString("title"))
        assertEquals(100.5, rebuiltRecord.getDouble("achievements"), 0.0001)
        assertEquals("fc", rebuiltRecord.getString("fc"))
    }

    @Test
    fun divingFishRebuildFailsInsteadOfSkippingRejectedLocalRecord() {
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"message":1}"""),
            MaimaiUploadHttpResponse(500, "server rejected one local score"),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.rebuildDivingFishRecords(
            importToken = "fish-token",
            freshScores = listOf(score(title = "Must Stay Present")),
            recordsToRemove = emptyList(),
        )

        assertEquals(false, result.success)
        assertEquals(0, result.uploadedScoreCount)
        assertEquals(2, transport.requests.size)
        assertEquals("DELETE", transport.requests[0].method)
        assertEquals("POST", transport.requests[1].method)
        assertEquals(true, result.message.contains("server rejected one local score"))
    }

    @Test
    fun lxnsUploadBuildsExpectedRequestAndReadsApiFailure() {
        val transport = RecordingTransport(
            MaimaiUploadHttpResponse(200, """{"success":false,"message":"invalid token"}"""),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToLxns("lxns-token", listOf(score(songId = 834)))

        assertEquals(false, result.success)
        assertEquals("invalid token", result.message)
        val request = transport.singleRequest()
        assertEquals(
            "https://maimai.lxns.net/api/v0/user/maimai/player/scores",
            request.url,
        )
        assertEquals("lxns-token", request.headers["X-User-Token"])
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
                "Import-Token=secret-token https://example.test/private?token=secret",
            ),
        )
        val uploader = MaimaiScoreUploader(transport = transport)

        val result = uploader.uploadToDivingFish("fish-token", listOf(score()))

        assertEquals(false, result.success)
        assertEquals(false, result.message.contains("secret-token"))
        assertEquals(false, result.message.contains("https://"))
        assertEquals(true, result.message.contains("[REDACTED_SECRET]"))
        assertEquals(true, result.message.contains("[REDACTED_URL]"))
    }

    @Test
    fun divingFishDiffReportsLocalOnlyRecords() {
        val uploader = MaimaiScoreUploader(transport = RecordingTransport(MaimaiUploadHttpResponse(200, "{}")))
        val localScores = (1..5).map { index -> score(title = "Song $index") }
        val cloudScores = localScores.take(3)

        val diff = uploader.diffDivingFishRecords(
            localRecords = localScores.map(::divingFishRecord),
            cloudRecords = cloudScores.map(::divingFishRecord),
        )

        assertEquals(false, diff.isEmpty)
        assertEquals(0, diff.cloudOnly.size)
        assertEquals(2, diff.localOnly.size)
        assertEquals(listOf("Song 4", "Song 5"), diff.localOnly.map { it.title })
        assertEquals(true, diff.summaryText().contains("local-only=2"))
    }

    @Test
    fun divingFishDiffAcceptsCurrentP1AndP2RegressionRecords() {
        val uploader = MaimaiScoreUploader(transport = RecordingTransport(MaimaiUploadHttpResponse(200, "{}")))
        val localScores = listOf(
            score(
                title = "Åntinomiε",
                songType = SongType.DX,
                difficulty = Difficulty.EXPERT,
                levelIndex = Difficulty.EXPERT.levelIndex,
                achievement = 100.5386,
                dxScore = 1240,
                fs = "sync",
            ),
            score(
                title = "宙天",
                songType = SongType.DX,
                difficulty = Difficulty.EXPERT,
                levelIndex = Difficulty.EXPERT.levelIndex,
                achievement = 100.5043,
                dxScore = 1288,
                fs = "sync",
            ),
            score(title = "Destr0yer", songType = SongType.DX, achievement = 99.6112),
            score(title = "Oshama Scramble!", songType = SongType.DX, achievement = 98.9868),
        )

        val diff = uploader.diffDivingFishRecords(
            localRecords = localScores.map(::divingFishRecord),
            cloudRecords = localScores.map(::divingFishRecord),
        )

        assertEquals(true, diff.isEmpty)
    }

    private fun score(
        songId: Int? = 834,
        songType: SongType = SongType.STANDARD,
        title: String = "PANDORA PARADOXXX",
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

    private fun cloudRecordsFromJsonArray(body: String): MaimaiUploadHttpResponse =
        MaimaiUploadHttpResponse(
            statusCode = 200,
            body = JSONObject()
                .put("records", JSONArray(body))
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
