package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskCategory(val label: String) {
    WORK("Work"),
    PERSONAL("Personal"),
    WELLNESS("Wellness");

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.name == raw } ?: WORK
    }
}

enum class TaskPriority(val label: String) {
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class TaskStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DONE("Done")
}

@Keep
@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = TaskCategory.WORK.name,
    val priority: String = TaskPriority.NORMAL.name,
    val completed: Boolean = false,
    val focusMinutes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: TaskCategory get() = TaskCategory.fromRaw(category)
    val priorityEnum: TaskPriority get() = TaskPriority.fromRaw(priority)
}

@Keep
@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val durationMinutes: Long = 25L,
    val category: String = TaskCategory.WORK.name,
    @ServerTimestamp val completedAt: Timestamp? = null
) {
    val categoryEnum: TaskCategory get() = TaskCategory.fromRaw(category)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
