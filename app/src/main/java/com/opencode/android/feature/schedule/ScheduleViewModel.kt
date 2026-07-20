package com.opencode.android.feature.schedule

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class ScheduleRepeat { DAILY, WEEKLY }

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val time: String,
    val repeat: ScheduleRepeat,
    val dayOfWeek: String? = null,
    val enabled: Boolean
)

/**
 * PLACEHOLDER VIEWMODEL: holds an in-memory list of "scheduled tasks" so the new
 * Schedule screen has something real to render and toggle. This is not backed by
 * any persistence or actual task runner yet.
 *
 * TODO: replace with a real scheduling backend (e.g. WorkManager-driven automation
 * that talks to the OpenCode runtime) once that feature is designed. Until then,
 * items reset to the seed data below every time the app process restarts.
 */
class ScheduleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(
        listOf(
            ScheduleItem(name = "デイリー同期", time = "09:00", repeat = ScheduleRepeat.DAILY, enabled = true),
            ScheduleItem(name = "コード整形", time = "12:00", repeat = ScheduleRepeat.DAILY, enabled = true),
            ScheduleItem(name = "レポート生成", time = "08:00", repeat = ScheduleRepeat.WEEKLY, dayOfWeek = "月曜", enabled = false),
            ScheduleItem(name = "バックアップ", time = "23:00", repeat = ScheduleRepeat.DAILY, enabled = true)
        )
    )
    val state: StateFlow<List<ScheduleItem>> = mutableState.asStateFlow()

    fun toggle(id: String) {
        mutableState.update { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
    }

    fun add(name: String, time: String, repeat: ScheduleRepeat) {
        if (name.isBlank() || time.isBlank()) return
        mutableState.update { it + ScheduleItem(name = name.trim(), time = time.trim(), repeat = repeat, enabled = true) }
    }
}
