package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CadenceRepository
import com.example.model.FocusSession
import com.example.model.TaskCategory
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatusFilter
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskFilterState(
    val status: TaskStatusFilter = TaskStatusFilter.ALL,
    val category: TaskCategory? = null,
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repository: CadenceRepository,
    private val currentUserId: String
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    private val _filterState = MutableStateFlow(TaskFilterState())
    val filterState: StateFlow<TaskFilterState> = _filterState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val tasksState: StateFlow<UiState<List<TaskItem>>> = retryTrigger
        .flatMapLatest {
            repository.observeTasks(currentUserId)
                .map<List<TaskItem>, UiState<List<TaskItem>>> { tasks -> UiState.Success(tasks) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing tasks", rootError)
                    emit(UiState.Error(rootError.localizedMessage ?: "Failed to load tasks"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<UiState<List<FocusSession>>> = retryTrigger
        .flatMapLatest {
            repository.observeFocusSessions(currentUserId)
                .map<List<FocusSession>, UiState<List<FocusSession>>> { sessions -> UiState.Success(sessions) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing focus sessions", rootError)
                    emit(UiState.Error(rootError.localizedMessage ?: "Failed to load focus sessions"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val filteredTasks: StateFlow<UiState<List<TaskItem>>> = combine(
        tasksState,
        _filterState
    ) { state, filter ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val query = filter.searchQuery.trim()
                val filtered = state.data.filter { task ->
                    val statusOk = when (filter.status) {
                        TaskStatusFilter.ALL -> true
                        TaskStatusFilter.ACTIVE -> !task.completed
                        TaskStatusFilter.DONE -> task.completed
                    }
                    val categoryOk = filter.category == null || task.categoryEnum == filter.category
                    val queryOk = query.isEmpty() ||
                        task.title.contains(query, ignoreCase = true) ||
                        task.notes.contains(query, ignoreCase = true)
                    statusOk && categoryOk && queryOk
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun setStatusFilter(status: TaskStatusFilter) {
        _filterState.update { it.copy(status = status) }
    }

    fun toggleCategoryFilter(category: TaskCategory?) {
        _filterState.update { current ->
            val next = if (current.category == category) null else category
            current.copy(category = next)
        }
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun saveTask(
        editingTask: TaskItem?,
        title: String,
        notes: String,
        category: TaskCategory,
        priority: TaskPriority,
        onSuccess: () -> Unit
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _feedbackMessage.value = "Please enter a task title."
            return
        }
        if (cleanTitle.length > 120) {
            _feedbackMessage.value = "Task title must be 120 characters or fewer."
            return
        }
        if (notes.trim().length > 500) {
            _feedbackMessage.value = "Notes must be 500 characters or fewer."
            return
        }

        viewModelScope.launch {
            _isMutating.value = true
            val result = if (editingTask == null) {
                repository.createTask(
                    userId = currentUserId,
                    title = cleanTitle,
                    notes = notes,
                    category = category,
                    priority = priority
                )
            } else {
                repository.updateTask(
                    userId = currentUserId,
                    taskId = editingTask.id,
                    title = cleanTitle,
                    notes = notes,
                    category = category,
                    priority = priority,
                    completed = editingTask.completed
                )
            }
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _feedbackMessage.value = if (editingTask == null) "Task added" else "Task updated"
                    onSuccess()
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Could not save task"
                }
            )
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompleted(currentUserId, task)
            result.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to update task"
            }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.deleteTask(currentUserId, task.id)
            result.fold(
                onSuccess = { _feedbackMessage.value = "Deleted \"${task.title}\"" },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to delete task"
                }
            )
        }
    }

    fun deleteSession(session: FocusSession) {
        viewModelScope.launch {
            val result = repository.deleteFocusSession(currentUserId, session.id)
            result.fold(
                onSuccess = { _feedbackMessage.value = "Focus log removed" },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to remove focus log"
                }
            )
        }
    }

    private companion object {
        const val TAG = "TasksVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
