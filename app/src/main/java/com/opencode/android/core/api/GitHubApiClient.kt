package com.opencode.android.core.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.opencode.android.feature.workspace.GitHubReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubApiClient(private val token: String?) {

    private val client: OkHttpClient = OkHttpClient()
    private val gson: Gson = Gson()

    suspend fun getPullRequests(owner: String, repo: String, branch: String): List<GitHubReference> =
        withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(
                    "https://api.github.com/repos/$owner/$repo/pulls".toHttpUrl().newBuilder()
                        .addQueryParameter("head", "$owner:$branch")
                        .build().toString()
                )
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    val items = gson.fromJson(body, Array<GitHubPull>::class.java) ?: emptyArray()
                    items.map { pull ->
                        GitHubReference(
                            type = "PR",
                            number = pull.number,
                            title = pull.title,
                            url = pull.htmlUrl
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun searchIssues(owner: String, repo: String, query: String): List<GitHubReference> =
        withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(
                    "https://api.github.com/search/issues".toHttpUrl().newBuilder()
                        .addQueryParameter("q", "repo:$owner/$repo $query")
                        .build().toString()
                )
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    val result = gson.fromJson(body, GitHubSearchResult::class.java)
                        ?: return@withContext emptyList()
                    result.items.map { item ->
                        GitHubReference(
                            type = "Issue",
                            number = item.number,
                            title = item.title,
                            url = item.htmlUrl
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .apply {
            token?.takeIf { it.isNotBlank() }?.let {
                header("Authorization", "Bearer $it")
            }
        }
        .build()

    private data class GitHubPull(
        val number: Int = 0,
        val title: String = "",
        @SerializedName("html_url") val htmlUrl: String = ""
    )

    private data class GitHubSearchResult(
        val items: List<GitHubIssue> = emptyList()
    )

    private data class GitHubIssue(
        val number: Int = 0,
        val title: String = "",
        @SerializedName("html_url") val htmlUrl: String = ""
    )
}
