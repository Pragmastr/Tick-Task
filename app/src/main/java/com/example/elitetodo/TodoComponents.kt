package com.example.elitetodo


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@Composable
fun TaskItem(
    task: TaskEntity,
    onCheckboxClick: (Boolean) -> Unit,
    onMenuClick: () -> Unit
) {
    val isTaskActive = task.status == 1
    // Локальное состояние открытия меню для конкретной карточки
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isTaskActive) Icons.Default.CheckCircle else Icons.Default.DateRange,
            contentDescription = "Status Icon",
            tint = if (isTaskActive) Color.Gray else MaterialTheme.colorScheme.primary,
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
                textDecoration = if (isTaskActive) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isTaskActive) Color.Gray else Color.Black
            )
            val secondaryText = "${task.date} в ${task.time}"
            if (secondaryText.trim().isNotEmpty())  {
                Text(
                    text = secondaryText,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textDecoration = if (isTaskActive) TextDecoration.LineThrough else TextDecoration.None
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
                checked = isTaskActive,
                onCheckedChange = { onCheckboxClick(it) },
                modifier = Modifier.size(32.dp),
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // ИСПРАВЛЕНО: Добавлен Box, чтобы меню знало координаты этой кнопки
            Box {
                IconButton(
                    onClick = { menuExpanded = true }, // При клике открываем локальное меню
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.Gray
                    )
                }

                // ИСПРАВЛЕНО: Выпадающее меню теперь привязано к этой кнопке
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(com.example.elitetodo.ui.theme.SoftLightBlue)
                ) {

                    //HorizontalDivider(
                      //  modifier = Modifier.padding(horizontal = 8.dp),
                       // thickness = 1.dp,
                      //  color = Color.LightGray.copy(alpha = 0.6f)
                    //)
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuExpanded = false
                            onMenuClick() // Пробрасываем событие удаления в MainActivity
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
    onAddTaskClick: () -> Unit,
    onTaskCheckedChange : (TaskEntity, Boolean) -> Unit,
    onTaskMenuClick: (TaskEntity) -> Unit
) {
    Scaffold(
        modifier = Modifier.background(Color.White),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tick-Task", // Добавили ваше новое название!
                        fontWeight = FontWeight.Normal,
                        color = Color.Black
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
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
            items(tasks, key = { it.id }) { task ->
                TaskItem(
                    task = task,
                    onCheckboxClick = { isChecked -> onTaskCheckedChange(task, isChecked) },
                    onMenuClick = { onTaskMenuClick(task) }
                )
            }
        }
    }
}