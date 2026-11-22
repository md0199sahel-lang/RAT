package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CadenceRepository
import com.example.model.TaskItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusTimerUiState(
    val durationMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val selectedTaskId: String? = null,
    val isSaving: Boolean = false,
    val feedbackMessage: String? = null
) {
    val totalSeconds: Int get() = (durationMinutes * 60).coerceAtLeast(1)
    val progress: Float
        get() = ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val elapsedMinutes: Int
        get() = ((totalSeconds - remainingSeconds) / 60).coerceAtLeast(0)
}

class FocusViewModel(
    private val repository: CadenceRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusTimerUiState())
    val uiState: StateFlow<FocusTimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun selectTask(taskId: String?) {
        _uiState.update { it.copy(selectedTaskId = taskId) }
    }

    fun selectDuration(minutes: Int) {
        if (_uiState.value.isRunning) return
        val safeMinutes = minutes.coerceIn(1, 180)
        _uiState.update {
            it.copy(
                durationMinutes = safeMinutes,
                remainingSeconds = safeMinutes * 60,
                hasStarted = false
            )
        }
    }

    fun toggleStartPause(linkedTaskResolver: () -> TaskItem?) {
        val current = _uiState.value
        if (current.isRunning) {
            pauseTimer()
        } else {
            startTimer(linkedTaskResolver)
        }
    }

    private fun startTimer(linkedTaskResolver: () -> TaskItem?) {
        if (_uiState.value.remainingSeconds <= 0) {
            _uiState.update {
                it.copy(remainingSeconds = it.durationMinutes * 60)
            }
        }
        timerJob?.cancel()
        _uiState.update { it.copy(isRunning = true, hasStarted = true) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0 && _uiState.value.isRunning) {
                delay(1000L)
                _uiState.update { state ->
                    val next = (state.remainingSeconds - 1).coerceAtLeast(0)
                    state.copy(remainingSeconds = next)
                }
            }
            if (_uiState.value.remainingSeconds == 0 && _uiState.value.isRunning) {
                _uiState.update { it.copy(isRunning = false) }
                persistSession(
                    linkedTask = linkedTaskResolver(),
                    minutes = _uiState.value.durationMinutes
                )
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update {
            it.copy(
                isRunning = false,
                hasStarted = false,
                remainingSeconds = it.durationMinutes * 60
            )
        }
    }

    fun logCurrentSession(linkedTask: TaskItem?) {
        val current = _uiState.value
        if (current.isSaving) return
        val minutesToLog = if (current.elapsedMinutes >= 1) {
            current.elapsedMinutes
        } else {
            current.durationMinutes
        }.coerceIn(1, 180)

        pauseTimer()
        persistSession(linkedTask = linkedTask, minutes = minutesToLog)
    }

    private fun persistSession(linkedTask: TaskItem?, minutes: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.recordFocusSession(
                userId = currentUserId,
                task = linkedTask,
                durationMinutes = minutes
            )
            _uiState.update { state ->
                val message = result.fold(
                    onSuccess = { "Logged ${minutes}m focus session" },
                    onFailure = { err -> err.localizedMessage ?: "Failed to log focus session" }
                )
                state.copy(
                    isSaving = false,
                    hasStarted = false,
                    remainingSeconds = state.durationMinutes * 60,
                    feedbackMessage = message
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
