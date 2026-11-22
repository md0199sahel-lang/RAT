package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.FocusSession
import com.example.model.TaskCategory
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatusFilter
import com.example.model.UiState
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.MonoStatStyle
import com.example.viewmodel.TaskFilterState
import com.example.viewmodel.TasksViewModel

@Composable
fun MainScreen(
    userName: String,
    viewModel: TasksViewModel,
    onStartFocusForTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val filteredTasksState by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()

    MainScreenContent(
        userName = userName,
        tasksState = tasksState,
        filteredTasksState = filteredTasksState,
        sessionsState = sessionsState,
        filterState = filterState,
        isMutating = isMutating,
        onSearchQueryChange = viewModel::setSearchQuery,
        onSelectStatusFilter = viewModel::setStatusFilter,
        onToggleCategoryFilter = viewModel::toggleCategoryFilter,
        onToggleCompleted = viewModel::toggleTaskCompleted,
        onSaveTask = viewModel::saveTask,
        onDeleteTask = viewModel::deleteTask,
        onRetry = viewModel::retry,
        onStartFocusForTask = onStartFocusForTask,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    userName: String,
    tasksState: UiState<List<TaskItem>>,
    filteredTasksState: UiState<List<TaskItem>>,
    sessionsState: UiState<List<FocusSession>>,
    filterState: TaskFilterState,
    isMutating: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectStatusFilter: (TaskStatusFilter) -> Unit,
    onToggleCategoryFilter: (TaskCategory?) -> Unit,
    onToggleCompleted: (TaskItem) -> Unit,
    onSaveTask: (TaskItem?, String, String, TaskCategory, TaskPriority, () -> Unit) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onRetry: () -> Unit,
    onStartFocusForTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditorSheet by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val editingTask = remember(allTasks, editingTaskId) {
        editingTaskId?.let { id -> allTasks.firstOrNull { it.id == id } }
    }

    val totalFocusMinutes = remember(sessionsState) {
        (sessionsState as? UiState.Success)?.data?.sumOf { it.durationMinutes } ?: 0L
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTaskId = null
                    showEditorSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_task)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("tasks_lazy_column"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                val firstName = userName.trim().substringBefore(" ").ifBlank { "there" }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Today’s Cadence",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Ready for a focused day, $firstName.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = "Synced with Firestore",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item(key = "summary_card") {
                DailySummaryCard(
                    completedCount = allTasks.count { it.completed },
                    totalCount = allTasks.size,
                    focusMinutes = totalFocusMinutes
                )
            }

            item(key = "search_bar") {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search tasks…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search tasks"
                        )
                    },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_search_input")
                )
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskStatusFilter.entries.forEach { status ->
                        FilterChip(
                            selected = filterState.status == status,
                            onClick = { onSelectStatusFilter(status) },
                            label = { Text(status.label) },
                            modifier = Modifier.testTag("filter_status_${status.name.lowercase()}")
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TaskCategory.entries.forEach { category ->
                        FilterChip(
                            selected = filterState.category == category,
                            onClick = { onToggleCategoryFilter(category) },
                            label = { Text(category.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("filter_category_${category.name.lowercase()}")
                        )
                    }
                }
            }

            when (filteredTasksState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .testTag("tasks_loading_indicator"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tasks_error_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Unable to sync tasks",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = filteredTasksState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("retry_tasks_button")
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val tasks = filteredTasksState.data
                    if (tasks.isEmpty()) {
                        item(key = "empty") {
                            val hasActiveFilters = filterState.status != TaskStatusFilter.ALL ||
                                filterState.category != null ||
                                filterState.searchQuery.isNotBlank()
                            EmptyTasksCard(
                                hasActiveFilters = hasActiveFilters,
                                onAddTaskClick = {
                                    editingTaskId = null
                                    showEditorSheet = true
                                }
                            )
                        }
                    } else {
                        items(tasks, key = { it.id }) { task ->
                            TaskRowCard(
                                task = task,
                                onToggleCompleted = { onToggleCompleted(task) },
                                onEdit = {
                                    editingTaskId = task.id
                                    showEditorSheet = true
                                },
                                onDelete = { onDeleteTask(task) },
                                onFocus = { onStartFocusForTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditorSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TaskEditorSheetContent(
                editingTask = editingTask,
                isSaving = isMutating,
                onCancel = { showEditorSheet = false },
                onSave = { title, notes, category, priority ->
                    onSaveTask(editingTask, title, notes, category, priority) {
                        showEditorSheet = false
                    }
                }
            )
        }
    }
}

@Composable
private fun DailySummaryCard(
    completedCount: Int,
    totalCount: Int,
    focusMinutes: Long
) {
    val ratio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "PROGRESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$completedCount / $totalCount completed",
                        style = MonoStatStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${focusMinutes}m",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

@Composable
private fun TaskRowCard(
    task: TaskItem,
    onToggleCompleted: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFocus: () -> Unit
) {
    val priorityColor = when (task.priorityEnum) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.primary
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.secondary
        TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.completed) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("task_toggle_${task.id}")
            ) {
                Icon(
                    imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (task.completed) "Mark ${task.title} incomplete" else "Mark ${task.title} complete",
                    tint = if (task.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedVisibility(visible = task.notes.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = task.categoryEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(priorityColor)
                        )
                        Text(
                            text = task.priorityEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor
                        )
                    }

                    if (task.focusMinutes > 0L) {
                        Text(
                            text = "• ${task.focusMinutes}m focused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (!task.completed) {
                    IconButton(
                        onClick = onFocus,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("task_focus_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Focus on ${task.title}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("task_edit_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit ${task.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("task_delete_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete ${task.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksCard(
    hasActiveFilters: Boolean,
    onAddTaskClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_tasks_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_tasks),
                contentDescription = "Calm desk illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = if (hasActiveFilters) "No matching tasks" else stringResource(R.string.empty_tasks_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasActiveFilters) {
                    "Try adjusting your search or category filters."
                } else {
                    stringResource(R.string.empty_tasks_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasActiveFilters) {
                Button(
                    onClick = onAddTaskClick,
                    modifier = Modifier.testTag("empty_add_task_button")
                ) {
                    Text(stringResource(R.string.add_task))
                }
            }
        }
    }
}

@Composable
private fun TaskEditorSheetContent(
    editingTask: TaskItem?,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String, TaskCategory, TaskPriority) -> Unit
) {
    var title by remember(editingTask) { mutableStateOf(editingTask?.title.orEmpty()) }
    var notes by remember(editingTask) { mutableStateOf(editingTask?.notes.orEmpty()) }
    var category by remember(editingTask) { mutableStateOf(editingTask?.categoryEnum ?: TaskCategory.WORK) }
    var priority by remember(editingTask) { mutableStateOf(editingTask?.priorityEnum ?: TaskPriority.NORMAL) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (editingTask == null) stringResource(R.string.new_task) else stringResource(R.string.edit_task),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 120) title = it },
            label = { Text(stringResource(R.string.task_title_label)) },
            singleLine = true,
            supportingText = { Text("${title.length}/120") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task_title_input")
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { if (it.length <= 500) notes = it },
            label = { Text(stringResource(R.string.task_notes_label)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("task_notes_input")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskCategory.entries.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("editor_category_${option.name.lowercase()}")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Priority",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { option ->
                    FilterChip(
                        selected = priority == option,
                        onClick = { priority = option },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("editor_priority_${option.name.lowercase()}")
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("cancel_task_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = { onSave(title, notes, category, priority) },
                enabled = !isSaving && title.trim().isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_task_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.save_task))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenTasksPreview() {
    val sampleTasks = listOf(
        TaskItem(
            id = "t1",
            title = "Finalize architecture review",
            notes = "Verify Firestore security rules & Compose layout.",
            category = TaskCategory.WORK.name,
            priority = TaskPriority.HIGH.name,
            completed = false,
            focusMinutes = 25L
        ),
        TaskItem(
            id = "t2",
            title = "Evening mobility & stretch",
            notes = "",
            category = TaskCategory.WELLNESS.name,
            priority = TaskPriority.NORMAL.name,
            completed = true,
            focusMinutes = 15L
        )
    )
    CadenceTheme {
        MainScreenContent(
            userName = "Alex",
            tasksState = UiState.Success(sampleTasks),
            filteredTasksState = UiState.Success(sampleTasks),
            sessionsState = UiState.Success(emptyList()),
            filterState = TaskFilterState(),
            isMutating = false,
            onSearchQueryChange = {},
            onSelectStatusFilter = {},
            onToggleCategoryFilter = {},
            onToggleCompleted = {},
            onSaveTask = { _, _, _, _, _, _ -> },
            onDeleteTask = {},
            onRetry = {},
            onStartFocusForTask = {}
        )
    }
}
