package com.opencode.android.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class ConnectionProfile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("username") val username: String = "opencode",
    @SerializedName("password") val password: String? = null,
    @SerializedName("allowInsecureLan") val allowInsecureLan: Boolean = false
) {
    override fun toString(): String =
        "ConnectionProfile(id=$id, name=$name, baseUrl=$baseUrl, username=$username, password=<redacted>, allowInsecureLan=$allowInsecureLan)"
}

object ConnectionProfileCodec {
    private val gson = Gson()
    private val listType = object : TypeToken<List<ConnectionProfile>>() {}.type

    fun encode(profiles: List<ConnectionProfile>): String = gson.toJson(profiles, listType)

    fun decode(json: String): List<ConnectionProfile> {
        if (json.isBlank()) return emptyList()
        return gson.fromJson<List<ConnectionProfile>>(json, listType).orEmpty()
    }
}
