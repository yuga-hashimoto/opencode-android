package com.opencode.android.core.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.opencode.android.core.security.OpenCodeUrl
import com.opencode.android.data.connection.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenCodeApiClient(
    private val profile: ConnectionProfile,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val gson: Gson = Gson(),
    private val eventParser: OpenCodeEventParser = OpenCodeEventParser(gson)
) {
    private val baseUrl: HttpUrl = OpenCodeUrl.normalize(profile.baseUrl).getOrThrow()

    suspend fun health(): OpenCodeHealth = get("global/health", OpenCodeHealth::class.java)

    suspend fun sessions(): List<OpenCodeSession> = getList("session")

    suspend fun createSession(title: String? = null): OpenCodeSession {
        val body = JsonObject().apply {
            title?.takeIf { it.isNotBlank() }?.let { addProperty("title", it) }
        }
        return post("session", body, OpenCodeSession::class.java)
    }

    suspend fun messages(sessionId: String): List<OpenCodeMessage> =
        getList("session/${encodePath(sessionId)}/message")

    suspend fun providers(): ProviderCatalog = get("provider", ProviderCatalog::class.java)

    suspend fun agents(): List<OpenCodeAgent> = getList("agent")

    suspend fun promptAsync(sessionId: String, request: PromptRequest) {
        val json = JsonObject().apply {
            request.agent?.takeIf { it.isNotBlank() }?.let { addProperty("agent", it) }
            if (!request.providerId.isNullOrBlank() && !request.modelId.isNullOrBlank()) {
                add("model", JsonObject().apply {
                    addProperty("providerID", request.providerId)
                    addProperty("modelID", request.modelId)
                })
            }
            if (request.noReply) addProperty("noReply", true)
            add("parts", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", request.text)
                })
            })
        }
        postWithoutResponse("session/${encodePath(sessionId)}/prompt_async", json)
    }

    suspend fun abortSession(sessionId: String): Boolean =
        post("session/${encodePath(sessionId)}/abort", JsonObject(), Boolean::class.java)

    suspend fun respondPermission(
        sessionId: String,
        permissionId: String,
        response: String
    ): Boolean {
        val json = JsonObject().apply {
            addProperty("response", response)
        }
        return post(
            "session/${encodePath(sessionId)}/permissions/${encodePath(permissionId)}",
            json,
            Boolean::class.java
        )
    }

    fun events(): Flow<OpenCodeEvent> = singleEventStream().retryWhen { cause, attempt ->
        val retryable = cause !is OpenCodeApiException || cause.statusCode >= 500
        if (!retryable) return@retryWhen false

        val backoffMillis = (500L * (1L shl attempt.toInt().coerceAtMost(5)))
            .coerceAtMost(15_000L)
        delay(backoffMillis)
        true
    }

    private fun singleEventStream(): Flow<OpenCodeEvent> = callbackFlow {
        val eventClient = httpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = requestBuilder("event").get().build()
        val source = EventSources.createFactory(eventClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    trySend(eventParser.parse(data))
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("OpenCode event stream closed"))
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    val error = response?.let {
                        OpenCodeApiException(
                            statusCode = it.code,
                            message = "OpenCode event stream failed (HTTP ${it.code})"
                        )
                    } ?: t ?: IOException("OpenCode event stream failed")
                    close(error)
                }
            }
        )
        awaitClose { source.cancel() }
    }

    private suspend fun <T> get(path: String, clazz: Class<T>): T = withContext(Dispatchers.IO) {
        execute(requestBuilder(path).get().build()) { body -> gson.fromJson(body, clazz) }
    }

    private suspend inline fun <reified T> getList(path: String): List<T> = withContext(Dispatchers.IO) {
        execute(requestBuilder(path).get().build()) { body ->
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(body, type).orEmpty()
        }
    }

    private suspend fun <T> post(path: String, body: JsonObject, clazz: Class<T>): T =
        withContext(Dispatchers.IO) {
            val request = requestBuilder(path)
                .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            execute(request) { responseBody -> gson.fromJson(responseBody, clazz) }
        }

    private suspend fun postWithoutResponse(path: String, body: JsonObject) = withContext(Dispatchers.IO) {
        val request = requestBuilder(path)
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request) { Unit }
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OpenCodeApiException(
                    statusCode = response.code,
                    message = "OpenCode request failed (HTTP ${response.code})"
                )
            }
            return parse(response.body?.string().orEmpty())
        }
    }

    private fun requestBuilder(path: String): Request.Builder {
        val url = baseUrl.resolve(path.removePrefix("/"))
            ?: throw IllegalArgumentException("Invalid OpenCode API path")
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                profile.password?.takeIf { it.isNotBlank() }?.let { password ->
                    header("Authorization", Credentials.basic(profile.username.ifBlank { "opencode" }, password))
                }
            }
    }

    private fun encodePath(value: String): String =
        value.replace("/", "%2F").replace("?", "%3F").replace("#", "%23")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
