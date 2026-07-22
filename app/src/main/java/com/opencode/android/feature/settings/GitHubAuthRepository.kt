package com.opencode.android.feature.settings

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.opencode.android.data.connection.SecureSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class GitHubDeviceCode(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_uri") val verificationUri: String,
    @SerializedName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerializedName("interval") val intervalSeconds: Long = 5L,
    @SerializedName("expires_in") val expiresInSeconds: Long = 900L
)

data class GitHubAccount(val login: String, val name: String? = null)

data class GitHubRepo(
    val name: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("clone_url") val cloneUrl: String,
    @SerializedName("private") val isPrivate: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null
)

class GitHubAuthRepository(
    private val settings: SecureSettingsRepository,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val clientId: String
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()
    val token: String? get() = settings.githubToken
    val accountLogin: String? get() = settings.githubLogin

    suspend fun requestDeviceCode(): GitHubDeviceCode = withContext(Dispatchers.IO) {
        require(isConfigured) { "GitHub client ID is not configured" }
        val body = FormBody.Builder().add("client_id", clientId).add("scope", "read:user repo").build()
        executeJson("https://github.com/login/device/code", body)
    }

    suspend fun pollToken(
        deviceCode: String,
        intervalSeconds: Long,
        expiresInSeconds: Long
    ): String = withContext(Dispatchers.IO) {
        require(isConfigured) { "GitHub client ID is not configured" }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        var delaySeconds = intervalSeconds.coerceAtLeast(1L)
        val deadline = System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(1L) * 1000L
        while (System.currentTimeMillis() < deadline) {
            val response: Map<String, Any?> = executeJson("https://github.com/login/oauth/access_token", body)
            val accessToken = response["access_token"]?.toString()
            if (!accessToken.isNullOrBlank()) {
                return@withContext accessToken
            }
            when (response["error"]?.toString()) {
                null, "authorization_pending" -> Unit
                "slow_down" -> delaySeconds += 5L
                "access_denied" -> error("GitHub authorization was denied")
                "expired_token" -> error("GitHub authorization expired")
                else -> error("GitHub authorization failed")
            }
            delaySeconds = (response["interval"]?.toString()?.toLongOrNull() ?: delaySeconds)
                .coerceAtLeast(1L)
            kotlinx.coroutines.delay(delaySeconds * 1000L)
        }
        error("GitHub authorization timed out")
    }

    suspend fun refreshAccount(accessToken: String? = token): GitHubAccount? = withContext(Dispatchers.IO) {
        accessToken ?: return@withContext null
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val account = gson.fromJson(response.body?.string().orEmpty(), GitHubAccount::class.java)
            settings.githubLogin = account.login
            account
        }
    }

    suspend fun listRepos(): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val accessToken = token ?: return@withContext emptyList()
        val request = Request.Builder()
            .url("https://api.github.com/user/repos?sort=updated&per_page=100&affiliation=owner,collaborator,organization_member")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<GitHubRepo>>() {}.type
            gson.fromJson<List<GitHubRepo>>(response.body?.string().orEmpty(), type).orEmpty()
        }
    }

    fun saveToken(value: String) {
        settings.githubToken = value.trim().takeIf { it.isNotEmpty() }
    }

    fun disconnect() {
        settings.githubToken = null
        settings.githubLogin = null
    }

    private inline fun <reified T> executeJson(url: String, body: FormBody): T {
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub request failed: ${response.code}" }
            return gson.fromJson(response.body?.string().orEmpty(), T::class.java)
        }
    }
}
