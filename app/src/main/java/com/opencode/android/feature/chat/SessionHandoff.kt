package com.opencode.android.feature.chat

import com.google.gson.Gson
import com.opencode.android.core.api.OpenCodeMessage
import com.opencode.android.core.api.PromptRequest
import com.opencode.android.runtime.RuntimeRegistry
import com.opencode.android.runtime.RuntimeTarget

data class SessionHandoffPackage(
    val sourceRuntimeId: String,
    val sourceRuntimeName: String,
    val sessionId: String,
    val sessionTitle: String,
    val directory: String?,
    val transcript: String,
    val messageCount: Int
)

object SessionHandoff {
    private const val FORMAT_VERSION = 1
    private const val MAX_TRANSCRIPT_CHARACTERS = 200_000
    private val gson = Gson()

    private data class Envelope(
        val version: Int,
        val payload: SessionHandoffPackage
    )

    fun buildPackage(
        sourceRuntimeId: String,
        sourceRuntimeName: String,
        sessionId: String,
        sessionTitle: String,
        directory: String?,
        messages: List<OpenCodeMessage>
    ): SessionHandoffPackage {
        require(sourceRuntimeId.isNotBlank()) { "Source runtime id is required" }
        require(sessionId.isNotBlank()) { "Source session id is required" }

        val blocks = messages.mapNotNull { message ->
            val text = message.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            val role = message.info.role.ifBlank { "unknown" }
            "### $role\n$text"
        }
        val transcript = retainMostRecentBlocks(blocks, MAX_TRANSCRIPT_CHARACTERS)
        return SessionHandoffPackage(
            sourceRuntimeId = sourceRuntimeId,
            sourceRuntimeName = sourceRuntimeName.ifBlank { sourceRuntimeId },
            sessionId = sessionId,
            sessionTitle = sessionTitle.ifBlank { sessionId },
            directory = directory?.trim()?.takeIf(String::isNotEmpty),
            transcript = transcript,
            messageCount = messages.size
        )
    }

    fun toJson(pack: SessionHandoffPackage): String {
        validate(pack)
        return gson.toJson(Envelope(FORMAT_VERSION, pack))
    }

    fun fromJson(raw: String): SessionHandoffPackage {
        require(raw.isNotBlank()) { "Handoff payload is empty" }
        val envelope = runCatching { gson.fromJson(raw, Envelope::class.java) }
            .getOrElse { throw IllegalArgumentException("Handoff payload is invalid", it) }
        requireNotNull(envelope) { "Handoff payload is invalid" }
        require(envelope.version == FORMAT_VERSION) {
            "Unsupported handoff format version: ${envelope.version}"
        }
        return envelope.payload.also(::validate)
    }

    suspend fun handoff(
        registry: RuntimeRegistry,
        pack: SessionHandoffPackage,
        targetRuntimeId: String,
        targetDirectory: String? = pack.directory
    ): Result<String> = runCatching {
        val target = registry.targets.value.firstOrNull { it.id == targetRuntimeId }
            ?: error("Target runtime not found: $targetRuntimeId")
        handoffTo(target, pack, targetDirectory).getOrThrow()
    }

    suspend fun handoffTo(
        target: RuntimeTarget,
        pack: SessionHandoffPackage,
        targetDirectory: String? = pack.directory
    ): Result<String> = runCatching {
        validate(pack)
        require(target.id != pack.sourceRuntimeId) {
            "Source and target runtime must be different"
        }
        target.connect().getOrThrow()
        val session = target.createSession(
            title = "Handoff: ${pack.sessionTitle}".take(80),
            directory = targetDirectory?.trim()?.takeIf(String::isNotEmpty)
        )
        require(session.id != pack.sessionId) {
            "Target runtime reused the source session id"
        }
        val prompt = buildString {
            appendLine("Continue this OpenCode session handed off from ${pack.sourceRuntimeName}.")
            appendLine("Original session: ${pack.sessionTitle} (${pack.sessionId})")
            appendLine()
            appendLine("<handoff_transcript>")
            appendLine(pack.transcript.ifBlank { "(empty transcript)" })
            appendLine("</handoff_transcript>")
            appendLine()
            appendLine("Acknowledge the imported context briefly and wait for the next user instruction.")
        }
        target.sendMessage(
            session.id,
            PromptRequest(text = prompt, noReply = false)
        )
        session.id
    }

    private fun validate(pack: SessionHandoffPackage) {
        require(pack.sourceRuntimeId.isNotBlank()) { "Source runtime id is required" }
        require(pack.sourceRuntimeName.isNotBlank()) { "Source runtime name is required" }
        require(pack.sessionId.isNotBlank()) { "Source session id is required" }
        require(pack.sessionTitle.isNotBlank()) { "Session title is required" }
        require(pack.messageCount >= 0) { "Message count is invalid" }
        require(pack.transcript.length <= MAX_TRANSCRIPT_CHARACTERS) {
            "Handoff transcript is too large"
        }
    }

    private fun retainMostRecentBlocks(blocks: List<String>, maxCharacters: Int): String {
        if (blocks.isEmpty()) return ""
        val retained = ArrayDeque<String>()
        var currentLength = 0
        for (block in blocks.asReversed()) {
            val separatorLength = if (retained.isEmpty()) 0 else 2
            val available = maxCharacters - currentLength - separatorLength
            if (available <= 0) break
            val value = if (block.length <= available) {
                block
            } else {
                "…${block.takeLast((available - 1).coerceAtLeast(0))}"
            }
            retained.addFirst(value)
            currentLength += value.length + separatorLength
            if (value.length < block.length) break
        }
        return retained.joinToString("\n\n")
    }
}
