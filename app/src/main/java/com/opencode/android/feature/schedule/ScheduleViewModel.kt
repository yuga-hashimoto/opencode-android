package com.opencode.android.feature.schedule

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val cronExpression: String,
    val workspaceId: String,
    val isActive: Boolean = true,
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null
)

class ScheduleViewModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutableSchedules = MutableStateFlow(
        listOf(
            ScheduleItem(
                name = "デイリー同期",
                prompt = "Sync all remote repositories",
                cronExpression = "0 9 * * *",
                workspaceId = "default",
                isActive = true,
                nextRunAt = System.currentTimeMillis() + 3_600_000
            ),
            ScheduleItem(
                name = "コード整形",
                prompt = "Run formatter on changed files",
                cronExpression = "0 12 * * *",
                workspaceId = "default",
                isActive = true,
                nextRunAt = System.currentTimeMillis() + 7_200_000
            ),
            ScheduleItem(
                name = "レポート生成",
                prompt = "Generate weekly summary report",
                cronExpression = "0 8 * * 1",
                workspaceId = "reports",
                isActive = false,
                lastRunAt = System.currentTimeMillis() - 86_400_000
            ),
            ScheduleItem(
                name = "バックアップ",
                prompt = "Backup workspace to cloud storage",
                cronExpression = "0 23 * * *",
                workspaceId = "default",
                isActive = true,
                nextRunAt = System.currentTimeMillis() + 14_400_000
            )
        )
    )

    private val mutableActiveOnly = MutableStateFlow(false)

    val schedules: StateFlow<List<ScheduleItem>> = mutableSchedules.asStateFlow()
    val activeOnly: StateFlow<Boolean> = mutableActiveOnly.asStateFlow()

    val filteredSchedules: StateFlow<List<ScheduleItem>> = combine(
        mutableSchedules,
        mutableActiveOnly
    ) { items, onlyActive ->
        if (onlyActive) items.filter { it.isActive } else items.filter { !it.isActive }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActiveOnly(activeOnly: Boolean) {
        mutableActiveOnly.update { activeOnly }
    }

    fun addSchedule(name: String, prompt: String, cronExpression: String, workspaceId: String) {
        if (name.isBlank() || prompt.isBlank() || cronExpression.isBlank()) return
        mutableSchedules.update {
            it + ScheduleItem(
                name = name.trim(),
                prompt = prompt.trim(),
                cronExpression = cronExpression.trim(),
                workspaceId = workspaceId.trim().ifBlank { "default" },
                isActive = true
            )
        }
    }

    fun updateSchedule(item: ScheduleItem) {
        mutableSchedules.update { list -> list.map { if (it.id == item.id) item else it } }
    }

    fun deleteSchedule(id: String) {
        mutableSchedules.update { list -> list.filter { it.id != id } }
    }

    fun toggleSchedule(id: String) {
        mutableSchedules.update { list ->
            list.map { if (it.id == id) it.copy(isActive = !it.isActive) else it }
        }
    }
}
