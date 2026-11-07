package com.example.elitetodo

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import todo.Todo
import todo.TodoService
import com.example.elitetodo.ui.theme.EliteToDoTheme

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var tasksState = mutableStateListOf<TaskEntity>()
    private val gson = Gson()
    private var goTodoService: TodoService? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) Log.d(TAG, "Разрешение на уведомления получено")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            val dbPath = applicationContext.getDatabasePath("elite_final.db").absolutePath
            goTodoService = Todo.newTodoService(dbPath)
            Log.d(TAG, "Go-сервис успешно инициализирован с файлом: $dbPath")
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка запуска Go-сервиса базы: ${e.message}")
        }

        handleIntent(intent)

        setContent {
            EliteToDoTheme {
                LaunchedEffect(Unit) {
                    refreshUi()
                }
                LaunchedEffect(Unit) {
                    refreshUi()
                }

                var showAddDialog by remember { mutableStateOf(false) }
                var showMenuTaskId by remember { mutableStateOf<Int?>(null) }
                var taskToDeleteId by remember { mutableStateOf<Int?>(null) }

                TodoScreen(
                    tasks = tasksState,
                    onAddTaskClick = { showAddDialog = true },
                    onTaskCheckedChange = { taskEntity, isChecked ->
                        val status = if (isChecked) 1 else 0
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // ИСПРАВЛЕНО: приводим ID и статус к типу Long с помощью .toLong()
                                goTodoService?.updateTaskStatus(
                                    taskEntity.id.toLong(),
                                    status.toLong()
                                )
                                refreshUi()
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка изменения статуса в Go: ${e.message}")
                            }
                        }
                    },
                    onTaskMenuClick = { taskEntity -> taskToDeleteId = taskEntity.id }
                )


                // if (showMenuTaskId != null) {
                //   DropdownMenu(
                //     expanded = true,
                //      onDismissRequest = { showMenuTaskId = null }
                //    ) {
                //         DropdownMenuItem(
                //            text = { Text("Удалить") },
                //             onClick = {
                //                 taskToDeleteId = showMenuTaskId
                //                 showMenuTaskId = null
                //             }
                //         )
                //     }
                //  }

                if (taskToDeleteId != null) {
                    AlertDialog(
                        onDismissRequest = { taskToDeleteId = null },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text("Удалить цель?") },
                        text = { Text("Вы уверены, что хотите навсегда стереть эту задачу из базы данных?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    taskToDeleteId?.let { id ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                // ИСПРАВЛЕНО: приводим id к типу Long с помощью .toLong()
                                                goTodoService?.deleteTask(id.toLong())
                                                refreshUi()
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Ошибка удаления в Go: ${e.message}")
                                            }
                                        }
                                    }
                                    taskToDeleteId = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(
                                        0xFFFF0000
                                    )
                                )
                            ) {
                                Text("УДАЛИТЬ")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { taskToDeleteId = null }) {
                                Text("ОТМЕНА")
                            }
                        }
                    )
                }


                if (showAddDialog) {
                    var taskText by remember { mutableStateOf("") }
                    val currentCal = Calendar.getInstance()

                    var tempDate by remember {
                        mutableStateOf(
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(
                                currentCal.time
                            )
                        )
                    }
                    var tempTime by remember {
                        mutableStateOf(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                                currentCal.time
                            )
                        )
                    }

                    AlertDialog(
                        onDismissRequest = { showAddDialog = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text("Новая цель") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = taskText,
                                    onValueChange = { taskText = it },
                                    label = { Text("Что планируем?") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Запланировано на: $tempDate в $tempTime",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (taskText.trim().isNotEmpty()) {
                                    addTask(taskText.trim(), tempDate, tempTime)
                                    showAddDialog = false
                                }
                            }) {
                                Text("СОХРАНИТЬ")
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    DatePickerDialog(
                                        this@MainActivity,
                                        { _, year, month, dayOfMonth ->
                                            val cal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            }
                                            tempDate = SimpleDateFormat(
                                                "dd.MM.yyyy",
                                                Locale.getDefault()
                                            ).format(cal.time)
                                        },
                                        currentCal.get(Calendar.YEAR),
                                        currentCal.get(Calendar.MONTH),
                                        currentCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }) {
                                    Text("ДАТА")
                                }

                                TextButton(onClick = {
                                    TimePickerDialog(
                                        this@MainActivity,
                                        { _, hourOfDay, minute ->
                                            val cal = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                set(Calendar.MINUTE, minute)
                                            }
                                            tempTime = SimpleDateFormat(
                                                "HH:mm",
                                                Locale.getDefault()
                                            ).format(cal.time)
                                        },
                                        currentCal.get(Calendar.HOUR_OF_DAY),
                                        currentCal.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                }) {
                                    Text("ВРЕМЯ")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    private fun addTask(text: String, date: String, time: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Вся логика форматирования дат и инсерта ушла в Go-слой
                val insertedId = goTodoService?.addTaskAndGetID(text, date, time) ?: -1L

                if (insertedId != -1L) {
                    // Системный будильник по-прежнему взводим на уровне Kotlin
                    AndroidAlarmManager.scheduleNotification(this@MainActivity, insertedId.toInt(), text, date, time)
                }

                withContext(Dispatchers.Main) {
                    refreshUi()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка добавления задачи через Go: ${e.message}")
            }
        }
    }

    private fun refreshUi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(150)

                // Запрашиваем JSON строку у Go
                val jsonTasks = goTodoService?.allTasksSortedJSON ?: "[]"

                // Десериализуем её в список объектов Kotlin
                val type = object : TypeToken<List<TaskEntity>>() {}.type
                val freshTasks: List<TaskEntity> = gson.fromJson(jsonTasks, type)

                withContext(Dispatchers.Main) {
                    tasksState.clear()
                    tasksState.addAll(freshTasks)
                    Log.d(TAG, "Интерфейс обновлен через Go-слой. Задач: ${freshTasks.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления UI через Go: ${e.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            if (intent == null) return
            val action = intent.action

            if (action == "ACTION_LAUNCH_ALARM") {
                val taskIdStr = intent.getStringExtra("alarm_task_id") ?: ""
                val taskId = taskIdStr.toIntOrNull() ?: -1
                val title = intent.getStringExtra("alarm_task_title") ?: "Задача"

                if (taskId != -1) {
                    AndroidAlarmManager.showAlarmNotification(this, taskId, title)
                }
            } else if (action == "ACTION_DISMISS_ALARM") {
                val taskIdStr = intent.getStringExtra("task_id") ?: ""
                val taskId = taskIdStr.toIntOrNull() ?: -1
                Log.d("EliteTodoLOG", "Свайп / Выключение! ID: $taskId")

                if (taskId != -1 && taskId != 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // ИСПРАВЛЕНО: ПриводимtaskId и статус к типу Long с помощью .toLong()
                            goTodoService?.updateTaskStatus(taskId.toLong(), 1L)
                            Log.d("EliteTodoLOG", "БД Go: Записан статус 'Выполнено' для ID = $taskId")

                            refreshUi()
                        } catch (e: Exception) {
                            Log.e("EliteTodoLOG", "Ошибка записи статуса по свайпу через Go: ${e.message}")
                        }
                    }

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(taskId + 1000)
                }

                intent.action = ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки интента: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            goTodoService?.close() // Закрываем бд при уничтожении Активити
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при закрытии базы в Go: ${e.message}")
        }
    }
}
