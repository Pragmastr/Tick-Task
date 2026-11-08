package com.example.elitetodo


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


private val categoryPalette = listOf(
    Color(0xFF5C6BC0), // индиго
    Color(0xFF26A69A), // бирюза
    Color(0xFFEF6C00), // оранжевый
    Color(0xFF8E24AA), // фиолетовый
    Color(0xFF43A047), // зелёный
    Color(0xFFD81B60), // розовый
    Color(0xFF1E88E5), // синий
    Color(0xFF6D4C41)  // коричневый
)

/** Стабильный цвет категории: одинаков при каждом запуске приложения. */
fun categoryColor(name: String): Color {
    val hash = name.hashCode().let { if (it < 0) it.inv() else it }
    return categoryPalette[hash % categoryPalette.size]
}

@Composable
fun TaskItem(
    task: TaskEntity,
    onCheckboxClick: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val isTaskDone = task.status == 1
    // Локальное состояние открытия меню для конкретной карточки
    var menuExpanded by remember { mutableStateOf(false) }

    // Цвета из темы: в светлой теме — белые карточки, в тёмной — тёмные.
    val itemSurface = MaterialTheme.colorScheme.surface
    val itemText = MaterialTheme.colorScheme.onSurface
    val itemSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(itemSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isTaskDone) Icons.Default.CheckCircle else Icons.Default.DateRange,
            contentDescription = "Status Icon",
            tint = if (isTaskDone) Color.Gray else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .size(24.dp)
        )

        Column(
            modifier = Modifier
                .weight(0.75f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = task.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textDecoration = if (isTaskDone) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isTaskDone) itemSecondary else itemText
            )

            // Бейдж категории: цветная метка, привязанная к категории из ядра
            if (task.category.isNotBlank()) {
                Surface(
                    color = categoryColor(task.category).copy(alpha = 0.12f),
                    contentColor = categoryColor(task.category),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = task.category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            val secondaryText = "${task.date} в ${task.time}"
            if (secondaryText.trim().isNotEmpty())  {
                Text(
                    text = secondaryText,
                    fontSize = 14.sp,
                    color = itemSecondary,
                    textDecoration = if (isTaskDone) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(0.25f)
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isTaskDone,
                onCheckedChange = { onCheckboxClick(it) },
                modifier = Modifier.size(32.dp),
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = itemSecondary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Delete",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMenuClick()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    tasks: List<TaskEntity>,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onAddTaskClick: () -> Unit,
    onTaskCheckedChange : (TaskEntity, Boolean) -> Unit,
    onTaskEditClick: (TaskEntity) -> Unit,
    onTaskMenuClick: (TaskEntity) -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tick-Task",
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        // Шестерёнка настроек: тема и мелодия будильника.
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Лента категорий-фильтров: «Все» + категории, встречающиеся в задачах.
                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("Все") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    // Повторный тап по выбранной категории возвращает «Все»
                                    onCategorySelected(if (selectedCategory == cat) null else cat)
                                },
                                label = {
                                    Text(
                                        text = cat,
                                        color = if (selectedCategory == cat)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            categoryColor(cat)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTaskClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = if (selectedCategory == null)
                            "Здесь пока пусто.\nНажмите «+», чтобы добавить цель."
                        else
                            "В категории «$selectedCategory» задач нет.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp)
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCheckboxClick = { isChecked -> onTaskCheckedChange(task, isChecked) },
                    onEditClick = { onTaskEditClick(task) },
                    onMenuClick = { onTaskMenuClick(task) }
                )
            }
        }
    }
}
