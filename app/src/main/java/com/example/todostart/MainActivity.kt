package com.example.todostart

import com.example.todostart.ui.theme.TodoStartTheme
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class Priority { LOW, MEDIUM, HIGH }

data class Task(
    val id: Long,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val dueAtMillis: Long? = null
)

sealed class Screen(val route: String) {
    data object List : Screen("list")
    data object Add : Screen("add")
    data object Details : Screen("details/{taskId}") {
        fun createRoute(taskId: Long) = "details/$taskId"
    }
}

enum class SortBy { DUE_DATE, CREATED_DATE }
enum class SortDirection { ASC, DESC }

sealed class PriorityFilter {
    data object ALL : PriorityFilter()
    data class ONLY(val priority: Priority) : PriorityFilter()
}

class MainActivity : ComponentActivity() {

    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { TodoStartTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var nextId by remember { mutableLongStateOf(1L) }
    var loaded by remember { mutableStateOf(false) }

    // Filtry / sort / UI
    var showOnlyUndone by rememberSaveable { mutableStateOf(false) }
    var onlyWithDueDate by rememberSaveable { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.DUE_DATE) }
    var sortDirection by rememberSaveable { mutableStateOf(SortDirection.ASC) }
    var query by rememberSaveable { mutableStateOf("") }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }
    var priorityFilterCode by rememberSaveable { mutableStateOf("ALL") }

    val priorityFilter: PriorityFilter = remember(priorityFilterCode) {
        when (priorityFilterCode) {
            "LOW" -> PriorityFilter.ONLY(Priority.LOW)
            "MEDIUM" -> PriorityFilter.ONLY(Priority.MEDIUM)
            "HIGH" -> PriorityFilter.ONLY(Priority.HIGH)
            else -> PriorityFilter.ALL
        }
    }

    // Load + schedule reminders
    LaunchedEffect(Unit) {
        val loadedTasks = TaskStorage.load(context)
        tasks = loadedTasks
        nextId = (loadedTasks.maxOfOrNull { it.id } ?: 0L) + 1L
        loaded = true

        val now = System.currentTimeMillis()
        for (t in loadedTasks) {
            val due = t.dueAtMillis
            if (due != null && due > now) {
                ReminderScheduler.schedule(context, t.id, due, t.title)
            } else {
                ReminderScheduler.cancel(context, t.id)
            }
        }
    }

    fun persist(newTasks: List<Task>) {
        tasks = newTasks
        if (loaded) TaskStorage.save(context, newTasks)
    }

    fun addTask(title: String) {
        val newTask = Task(id = nextId, title = title)
        nextId += 1L
        persist(listOf(newTask) + tasks)
    }

    fun updateTask(updated: Task) {
        val updatedList = tasks.map { if (it.id == updated.id) updated else it }
        persist(updatedList)

        val due = updated.dueAtMillis
        if (due != null && due > System.currentTimeMillis()) {
            ReminderScheduler.schedule(context, updated.id, due, updated.title)
        } else {
            ReminderScheduler.cancel(context, updated.id)
        }
    }

    fun deleteTask(id: Long) {
        ReminderScheduler.cancel(context, id)
        persist(tasks.filterNot { it.id == id })
    }

    // Filter + sort
    val filteredSorted = remember(
        tasks,
        showOnlyUndone,
        onlyWithDueDate,
        sortBy,
        sortDirection,
        query,
        priorityFilterCode
    ) {
        val q = query.trim().lowercase(Locale.getDefault())

        val byDone = if (showOnlyUndone) tasks.filter { !it.isDone } else tasks

        val byQuery =
            if (q.isEmpty()) byDone
            else byDone.filter {
                it.title.lowercase(Locale.getDefault()).contains(q) ||
                        it.description.lowercase(Locale.getDefault()).contains(q)
            }

        val pf = when (priorityFilterCode) {
            "LOW" -> PriorityFilter.ONLY(Priority.LOW)
            "MEDIUM" -> PriorityFilter.ONLY(Priority.MEDIUM)
            "HIGH" -> PriorityFilter.ONLY(Priority.HIGH)
            else -> PriorityFilter.ALL
        }

        val byPriority = when (pf) {
            PriorityFilter.ALL -> byQuery
            is PriorityFilter.ONLY -> byQuery.filter { it.priority == pf.priority }
        }

        val byDue = if (!onlyWithDueDate) byPriority else byPriority.filter { it.dueAtMillis != null }

        fun dueKey(t: Task): Long = t.dueAtMillis ?: Long.MAX_VALUE
        val comparator = Comparator<Task> { a, b ->
            val cmp = when (sortBy) {
                SortBy.DUE_DATE -> dueKey(a).compareTo(dueKey(b))
                SortBy.CREATED_DATE -> a.id.compareTo(b.id)
            }
            if (sortDirection == SortDirection.ASC) cmp else -cmp
        }

        byDue.sortedWith(comparator)
    }

    val undoneTasks = remember(filteredSorted) { filteredSorted.filter { !it.isDone } }
    val doneTasks = remember(filteredSorted) { filteredSorted.filter { it.isDone } }

    NavHost(navController = nav, startDestination = Screen.List.route) {

        composable(Screen.List.route) {
            TaskListScreen(
                undoneTasks = undoneTasks,
                doneTasks = doneTasks,
                doneExpanded = doneExpanded,
                onToggleDoneExpanded = { doneExpanded = !doneExpanded },

                showOnlyUndone = showOnlyUndone,
                onToggleOnlyUndone = { showOnlyUndone = it },

                onlyWithDueDate = onlyWithDueDate,
                onToggleOnlyWithDueDate = { onlyWithDueDate = it },

                query = query,
                onQueryChange = { query = it },

                priorityFilter = priorityFilter,
                onPriorityFilterChange = { pf2 ->
                    priorityFilterCode = when (pf2) {
                        PriorityFilter.ALL -> "ALL"
                        is PriorityFilter.ONLY -> when (pf2.priority) {
                            Priority.LOW -> "LOW"
                            Priority.MEDIUM -> "MEDIUM"
                            Priority.HIGH -> "HIGH"
                        }
                    }
                },

                sortBy = sortBy,
                onSortByChange = { sortBy = it },
                sortDirection = sortDirection,
                onSortDirectionToggle = {
                    sortDirection =
                        if (sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                },

                onClearFilters = {
                    query = ""
                    showOnlyUndone = false
                    onlyWithDueDate = false
                    priorityFilterCode = "ALL"
                    sortBy = SortBy.DUE_DATE
                    sortDirection = SortDirection.ASC
                },

                onToggleDone = { id, checked ->
                    val t = tasks.find { it.id == id } ?: return@TaskListScreen
                    updateTask(t.copy(isDone = checked))
                },
                onDelete = { id -> deleteTask(id) },
                onAddClick = { nav.navigate(Screen.Add.route) },
                onTaskClick = { id -> nav.navigate(Screen.Details.createRoute(id)) }
            )
        }

        composable(Screen.Add.route) {
            AddTaskScreen(
                onBack = { nav.popBackStack() },
                onAddTask = { title ->
                    addTask(title)
                    nav.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType })
        ) { entry ->
            val taskId = entry.arguments?.getLong("taskId") ?: -1L
            val task = tasks.find { it.id == taskId }

            TaskDetailsScreen(
                task = task,
                onBack = { nav.popBackStack() },
                onUpdate = { updateTask(it) },
                onDelete = {
                    deleteTask(taskId)
                    nav.popBackStack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    undoneTasks: List<Task>,
    doneTasks: List<Task>,

    doneExpanded: Boolean,
    onToggleDoneExpanded: () -> Unit,

    showOnlyUndone: Boolean,
    onToggleOnlyUndone: (Boolean) -> Unit,

    onlyWithDueDate: Boolean,
    onToggleOnlyWithDueDate: (Boolean) -> Unit,

    query: String,
    onQueryChange: (String) -> Unit,

    priorityFilter: PriorityFilter,
    onPriorityFilterChange: (PriorityFilter) -> Unit,

    sortBy: SortBy,
    onSortByChange: (SortBy) -> Unit,
    sortDirection: SortDirection,
    onSortDirectionToggle: () -> Unit,

    onClearFilters: () -> Unit,

    onToggleDone: (taskId: Long, checked: Boolean) -> Unit,
    onDelete: (taskId: Long) -> Unit,
    onAddClick: () -> Unit,
    onTaskClick: (taskId: Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    // ✅ spójne tła
    val appBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)

    var panelOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun priorityLabel(pf: PriorityFilter): String = when (pf) {
        PriorityFilter.ALL -> "Wszystkie"
        is PriorityFilter.ONLY -> when (pf.priority) {
            Priority.LOW -> "Niski"
            Priority.MEDIUM -> "Średni"
            Priority.HIGH -> "Wysoki"
        }
    }

    val activeFiltersCount = remember(showOnlyUndone, onlyWithDueDate, priorityFilter, sortBy, sortDirection) {
        var c = 0
        if (showOnlyUndone) c++
        if (onlyWithDueDate) c++
        if (priorityFilter !is PriorityFilter.ALL) c++
        val isDefaultSort = (sortBy == SortBy.DUE_DATE && sortDirection == SortDirection.ASC)
        if (!isDefaultSort) c++
        c
    }

    val subtitle = remember(undoneTasks.size, doneTasks.size, activeFiltersCount) {
        val total = undoneTasks.size + doneTasks.size
        val f = if (activeFiltersCount > 0) " • filtry: $activeFiltersCount" else ""
        "$total zadań$f"
    }

    // ✅ Panel przeniesiony do BottomSheet + SCROLL (to jest ta poprawka)
    if (panelOpen) {
        ModalBottomSheet(
            onDismissRequest = { panelOpen = false },
            sheetState = sheetState
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Szukaj / Filtry / Sortowanie", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Szukaj (tytuł lub opis)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showOnlyUndone, onCheckedChange = onToggleOnlyUndone)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tylko niezrobione")
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = onlyWithDueDate, onCheckedChange = onToggleOnlyWithDueDate)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tylko z terminem")
                }

                HorizontalDivider()

                Text("Priorytet: ${priorityLabel(priorityFilter)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onPriorityFilterChange(PriorityFilter.ALL) }, label = { Text("Wszystkie") })
                    AssistChip(onClick = { onPriorityFilterChange(PriorityFilter.ONLY(Priority.LOW)) }, label = { Text("Niski") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onPriorityFilterChange(PriorityFilter.ONLY(Priority.MEDIUM)) }, label = { Text("Średni") })
                    AssistChip(onClick = { onPriorityFilterChange(PriorityFilter.ONLY(Priority.HIGH)) }, label = { Text("Wysoki") })
                }

                HorizontalDivider()

                Text("Sortowanie")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onSortByChange(SortBy.DUE_DATE) }, label = { Text("Termin") })
                    AssistChip(onClick = { onSortByChange(SortBy.CREATED_DATE) }, label = { Text("Data dodania") })
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Kierunek: ")
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onSortDirectionToggle) {
                        Text(if (sortDirection == SortDirection.ASC) "Rosnąco" else "Malejąco")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = onClearFilters) { Text("Wyczyść") }
                    TextButton(onClick = { panelOpen = false }) { Text("Zamknij") }
                }

                // ✅ żeby dół się nie ucinał
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            if (isLandscape) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Lista zadań • ${undoneTasks.size + doneTasks.size}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Lista zadań",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Text("+") }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->

        Surface(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            color = appBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                if (!isLandscape) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                label = { Text("Szukaj (tytuł lub opis)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { panelOpen = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(if (activeFiltersCount > 0) "Filtry ($activeFiltersCount)" else "Filtry")
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "Priorytet: ${priorityLabel(priorityFilter)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(
                                    onClick = onClearFilters,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text("Wyczyść") }
                            }
                        }
                    }
                } else {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { panelOpen = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(if (activeFiltersCount > 0) "Panel ($activeFiltersCount)" else "Panel")
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = priorityLabel(priorityFilter),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(
                                onClick = onClearFilters,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) { Text("Wyczyść") }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (undoneTasks.isEmpty() && doneTasks.isEmpty()) {
                        item { EmptyStateCard(cardBg) }
                    } else {
                        items(undoneTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                dateFmt = dateFmt,
                                onTaskClick = onTaskClick,
                                onToggleDone = onToggleDone,
                                onDelete = onDelete,
                                cardBg = cardBg
                            )
                        }

                        if (doneTasks.isNotEmpty()) {
                            item {
                                DoneHeaderCompact(
                                    count = doneTasks.size,
                                    expanded = doneExpanded,
                                    onClick = onToggleDoneExpanded,
                                    cardBg = cardBg
                                )
                            }

                            item {
                                AnimatedVisibility(
                                    visible = doneExpanded,
                                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        doneTasks.forEach { task ->
                                            TaskCard(
                                                task = task,
                                                dateFmt = dateFmt,
                                                onTaskClick = onTaskClick,
                                                onToggleDone = onToggleDone,
                                                onDelete = onDelete,
                                                cardBg = cardBg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(cardBg: Color) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Brak zadań", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dodaj pierwsze zadanie przyciskiem +.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DoneHeaderCompact(count: Int, expanded: Boolean, onClick: () -> Unit, cardBg: Color) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Zrobione ($count)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    dateFmt: SimpleDateFormat,
    onTaskClick: (Long) -> Unit,
    onToggleDone: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    cardBg: Color
) {
    val shape = RoundedCornerShape(22.dp)
    val stripeColor = priorityStripeColor(task.priority)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTaskClick(task.id) },
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardBg)
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = { checked -> onToggleDone(task.id, checked) }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(priorityEmoji(task.priority))
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        PriorityBadgeChip(task.priority)
                    }

                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (task.dueAtMillis != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text("Termin: ${dateFmt.format(task.dueAtMillis)}") }
                        )
                    }
                }

                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                }
            }
        }
    }
}

fun priorityEmoji(priority: Priority): String = when (priority) {
    Priority.LOW -> "⬇️"
    Priority.MEDIUM -> "➖"
    Priority.HIGH -> "⬆️"
}

@Composable
fun priorityStripeColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> MaterialTheme.colorScheme.error
    Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    Priority.LOW -> MaterialTheme.colorScheme.primary
}

@Composable
fun PriorityBadgeChip(priority: Priority) {
    val label = when (priority) {
        Priority.LOW -> "Niski"
        Priority.MEDIUM -> "Średni"
        Priority.HIGH -> "Wysoki"
    }

    val (bg, fg) = when (priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f) to MaterialTheme.colorScheme.tertiary
        Priority.LOW -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
    }

    AssistChip(
        onClick = { },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = bg,
            labelColor = fg
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    onAddTask: (title: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dodaj zadanie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (error) error = false
                },
                label = { Text("Treść zadania") },
                isError = error,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (error) Text("Wpisz treść zadania (nie może być pusta).")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onBack) { Text("Anuluj") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) error = true else onAddTask(trimmed)
                }) { Text("Dodaj") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsScreen(
    task: Task?,
    onBack: () -> Unit,
    onUpdate: (Task) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Szczegóły") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń")
                    }
                }
            )
        }
    ) { padding ->

        if (task == null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) { Text("Nie znaleziono zadania.") }
            return@Scaffold
        }

        var desc by remember(task.id) { mutableStateOf(task.description) }
        var priority by remember(task.id) { mutableStateOf(task.priority) }
        var dueAt by remember(task.id) { mutableStateOf(task.dueAtMillis) }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Treść zadania",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(task.title, style = MaterialTheme.typography.titleLarge)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { checked -> onUpdate(task.copy(isDone = checked)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (task.isDone) "Zadanie zrobione" else "Zadanie niezrobione")
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    OutlinedTextField(
                        value = desc,
                        onValueChange = {
                            desc = it
                            onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                        },
                        label = { Text("Opis (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    Text(
                        "Priorytet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = priority == Priority.LOW,
                            onClick = {
                                priority = Priority.LOW
                                onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                            },
                            label = { Text("Niski") }
                        )
                        FilterChip(
                            selected = priority == Priority.MEDIUM,
                            onClick = {
                                priority = Priority.MEDIUM
                                onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                            },
                            label = { Text("Średni") }
                        )
                        FilterChip(
                            selected = priority == Priority.HIGH,
                            onClick = {
                                priority = Priority.HIGH
                                onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                            },
                            label = { Text("Wysoki") }
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Termin",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(if (dueAt == null) "Brak terminu" else dateFmt.format(dueAt!!))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val cal = Calendar.getInstance()
                            if (dueAt != null) cal.timeInMillis = dueAt!!

                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val cal2 = Calendar.getInstance()
                                    cal2.timeInMillis = cal.timeInMillis
                                    cal2.set(Calendar.YEAR, year)
                                    cal2.set(Calendar.MONTH, month)
                                    cal2.set(Calendar.DAY_OF_MONTH, day)

                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            cal2.set(Calendar.HOUR_OF_DAY, hour)
                                            cal2.set(Calendar.MINUTE, minute)
                                            cal2.set(Calendar.SECOND, 0)
                                            cal2.set(Calendar.MILLISECOND, 0)

                                            dueAt = cal2.timeInMillis
                                            onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                                        },
                                        cal2.get(Calendar.HOUR_OF_DAY),
                                        cal2.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) { Text("Ustaw") }

                        OutlinedButton(onClick = {
                            dueAt = null
                            onUpdate(task.copy(description = desc, priority = priority, dueAtMillis = dueAt))
                        }) { Text("Usuń") }
                    }
                }
            }

            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Usuń zadanie")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
