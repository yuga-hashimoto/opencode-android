package com.opencode.android.feature.chat

enum class SlashAction { NEW_CHAT, CLEAR, MODEL, AGENT, ATTACH, HELP }

data class SlashCommand(
    val name: String,
    val description: String,
    val action: SlashAction
)

object SlashCommandRegistry {
    val commands: List<SlashCommand> = listOf(
        SlashCommand("/new", "Start a new session", SlashAction.NEW_CHAT),
        SlashCommand("/clear", "Clear current conversation", SlashAction.CLEAR),
        SlashCommand("/model", "Switch model", SlashAction.MODEL),
        SlashCommand("/agent", "Switch agent", SlashAction.AGENT),
        SlashCommand("/attach", "Attach a file", SlashAction.ATTACH),
        SlashCommand("/help", "Show help", SlashAction.HELP)
    )

    fun filter(query: String): List<SlashCommand> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return commands
        return commands.filter { it.name.startsWith(trimmed, ignoreCase = true) }
    }
}
