package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.TaskItem
import com.example.model.UiState
import com.example.ui.theme.MonoTimerDisplayStyle
import com.example.viewmodel.FocusTimerUiState

private val FOCUS_PRESETS = listOf(15, 25, 45)

@Composable
fun FocusScreen(
    focusState: FocusTimerUiState,
    tasksState: UiState<List<TaskItem>>,
    onSelectTask: (String?) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onToggleStartPause: (() -> TaskItem?) -> Unit,
    onReset: () -> Unit,
    onLogSession: (TaskItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTasks = remember(tasksState) {
        ((tasksState as? UiState.Success)?.data.orEmpty()).filter { !it.completed }
    }
    val selectedTask = remember(activeTasks, focusState.selectedTaskId) {
        focusState.selectedTaskId?.let { id -> activeTasks.firstOrNull { it.id == id } }
    }
    var showTaskMenu by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = focusState.progress,
        animationSpec = spring(stiffness = 280f),
        label = "focus_arc_progress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val arcColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Focus Studio",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Commit to one block of uninterrupted work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Target Task Selector
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCUS TARGET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedTask?.title ?: stringResource(R.string.general_focus),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { showTaskMenu = true },
                        modifier = Modifier.testTag("select_focus_task_button")
                    ) {
                        Text("Change")
                    }
                }

                DropdownMenu(
                    expanded = showTaskMenu,
                    onDismissRequest = { showTaskMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.general_focus)) },
                        onClick = {
                            onSelectTask(null)
                            showTaskMenu = false
                        }
                    )
                    activeTasks.forEach { task ->
                        DropdownMenuItem(
                            text = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                onSelectTask(task.id)
                                showTaskMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Duration Presets
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FOCUS_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = focusState.durationMinutes == minutes,
                    onClick = { onSelectDuration(minutes) },
                    enabled = !focusState.isRunning,
                    label = { Text("${minutes}m") },
                    modifier = Modifier.testTag("preset_${minutes}m")
                )
            }
        }

        // Radial Progress Canvas
        Box(
            modifier = Modifier
                .size(260.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                val diameter = minOf(size.width, size.height) - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = focusState.remainingSeconds / 60
                val secs = focusState.remainingSeconds % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    style = MonoTimerDisplayStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("focus_timer_readout")
                )
                Text(
                    text = if (focusState.isRunning) "In session" else "${focusState.durationMinutes} min block",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Primary Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onToggleStartPause { selectedTask } },
                enabled = !focusState.isSaving,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("focus_start_pause_button")
            ) {
                Icon(
                    imageVector = if (focusState.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        focusState.isRunning -> stringResource(R.string.focus_pause)
                        focusState.hasStarted -> stringResource(R.string.focus_resume)
                        else -> stringResource(R.string.focus_start)
                    }
                )
            }

            OutlinedButton(
                onClick = onReset,
                enabled = !focusState.isSaving && (focusState.hasStarted || focusState.isRunning),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("focus_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.focus_reset)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.focus_reset))
            }
        }

        FilledTonalButton(
            onClick = { onLogSession(selectedTask) },
            enabled = !focusState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("focus_log_session_button")
        ) {
            if (focusState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.focus_log_session))
        }
    }
}
