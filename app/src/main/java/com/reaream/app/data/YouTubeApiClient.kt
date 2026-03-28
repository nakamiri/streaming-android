package com.reaream.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class YouTubeApiClient(private val authManager: YouTubeAuthManager) {

    companion object {
        private const val TAG = "YouTubeApi"
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ChannelInfo(
        val id: String,
        val title: String,
        val thumbnailUrl: String,
    )

    data class BroadcastInfo(
        val id: String,
        val title: String,
        val status: String,
    )

    data class StreamIngestion(
        val rtmpUrl: String,
        val streamKey: String,
        val streamId: String,
    )

    suspend fun listMyChannels(): Result<List<ChannelInfo>> = apiCall {
        val token = getToken()
        val request = Request.Builder()
            .url("$BASE_URL/channels?part=snippet&mine=true&maxResults=50")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val body = execute(request)
        val items = body.jsonObject["items"]?.jsonArray ?: return@apiCall emptyList()

        items.map { item ->
            val obj = item.jsonObject
            val snippet = obj["snippet"]!!.jsonObject
            val thumbnails = snippet["thumbnails"]?.jsonObject
            val thumbUrl = thumbnails?.get("default")?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
            ChannelInfo(
                id = obj["id"]!!.jsonPrimitive.content,
                title = snippet["title"]!!.jsonPrimitive.content,
                thumbnailUrl = thumbUrl,
            )
        }
    }

    suspend fun listUpcomingBroadcasts(): Result<List<BroadcastInfo>> = apiCall {
        val token = getToken()
        val request = Request.Builder()
            .url("$BASE_URL/liveBroadcasts?part=snippet,status&broadcastStatus=upcoming&maxResults=20")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val body = execute(request)
        val items = body.jsonObject["items"]?.jsonArray ?: return@apiCall emptyList()

        items.map { item ->
            val obj = item.jsonObject
            BroadcastInfo(
                id = obj["id"]!!.jsonPrimitive.content,
                title = obj["snippet"]!!.jsonObject["title"]!!.jsonPrimitive.content,
                status = obj["status"]!!.jsonObject["lifeCycleStatus"]!!.jsonPrimitive.content,
            )
        }
    }

    suspend fun createBroadcast(
        title: String,
        privacyStatus: String,
    ): Result<String> = apiCall {
        val token = getToken()
        val requestBody = buildJsonObject {
            put("snippet", buildJsonObject {
                put("title", title)
                put("scheduledStartTime", java.time.Instant.now().toString())
            })
            put("status", buildJsonObject {
                put("privacyStatus", privacyStatus)
                put("selfDeclaredMadeForKids", JsonPrimitive(false))
            })
            put("contentDetails", buildJsonObject {
                put("enableAutoStart", JsonPrimitive(true))
                put("enableAutoStop", JsonPrimitive(true))
            })
        }.toString()

        val request = Request.Builder()
            .url("$BASE_URL/liveBroadcasts?part=snippet,status,contentDetails")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val body = execute(request)
        body.jsonObject["id"]!!.jsonPrimitive.content
    }

    suspend fun createStream(title: String, resolution: String = "1080p", fps: Int = 30): Result<StreamIngestion> = apiCall {
        val token = getToken()
        val frameRate = if (fps >= 60) "60fps" else "30fps"
        val requestBody = buildJsonObject {
            put("snippet", buildJsonObject {
                put("title", title)
            })
            put("cdn", buildJsonObject {
                put("frameRate", frameRate)
                put("ingestionType", "rtmp")
                put("resolution", resolution)
            })
        }.toString()

        val request = Request.Builder()
            .url("$BASE_URL/liveStreams?part=snippet,cdn")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val body = execute(request)
        val cdn = body.jsonObject["cdn"]!!.jsonObject
        val ingestion = cdn["ingestionInfo"]!!.jsonObject
        StreamIngestion(
            rtmpUrl = ingestion["ingestionAddress"]!!.jsonPrimitive.content,
            streamKey = ingestion["streamName"]!!.jsonPrimitive.content,
            streamId = body.jsonObject["id"]!!.jsonPrimitive.content,
        )
    }

    suspend fun bindBroadcast(broadcastId: String, streamId: String): Result<Unit> = apiCall {
        val token = getToken()
        val request = Request.Builder()
            .url("$BASE_URL/liveBroadcasts/bind?id=$broadcastId&part=id,contentDetails&streamId=$streamId")
            .addHeader("Authorization", "Bearer $token")
            .post("".toRequestBody(jsonMediaType))
            .build()

        execute(request)
    }

    suspend fun transitionBroadcast(broadcastId: String, status: String): Result<Unit> = apiCall {
        val token = getToken()
        val request = Request.Builder()
            .url("$BASE_URL/liveBroadcasts/transition?broadcastStatus=$status&id=$broadcastId&part=status")
            .addHeader("Authorization", "Bearer $token")
            .post("".toRequestBody(jsonMediaType))
            .build()

        execute(request)
    }

    suspend fun setupAndGetIngestion(
        title: String,
        privacyStatus: String,
        resolution: String,
        fps: Int = 30,
        existingBroadcastId: String? = null,
    ): Result<Pair<String, StreamIngestion>> {
        // Step 1: Create or use existing broadcast
        val broadcastId = if (existingBroadcastId != null) {
            existingBroadcastId
        } else {
            val result = createBroadcast(title, privacyStatus)
            result.getOrElse { return Result.failure(it) }
        }

        // Step 2: Create stream
        val stream = createStream(title, resolution, fps).getOrElse {
            return Result.failure(it)
        }

        // Step 3: Bind broadcast to stream
        bindBroadcast(broadcastId, stream.streamId).getOrElse {
            return Result.failure(it)
        }

        return Result.success(broadcastId to stream)
    }

    private suspend fun getToken(): String {
        return authManager.getValidAccessToken()
            ?: throw IllegalStateException("Not authenticated. Please sign in first.")
    }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw Exception("YouTube API error ${response.code}: ${parseErrorMessage(responseBody)}")
        }

        json.parseToJsonElement(responseBody)
    }

    private fun parseErrorMessage(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val error = obj["error"]?.jsonObject
            error?.get("message")?.jsonPrimitive?.content ?: body
        } catch (e: Exception) {
            body
        }
    }

    private suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            Result.failure(e)
        }
    }
}
