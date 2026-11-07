package com.example.elitetodo

import android.Manifest
import android.app.NotificationManager
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

                var showAddDialog by remember { mutableStateOf(false) }
                var taskToDeleteId by remember { mutableStateOf<Int?>(null) }
                var taskToEditId by remember { mutableStateOf<Int?>(null) }
                var selectedCategory by remember { mutableStateOf<String?>(null) }

                // Категории и фильтрация: список категорий берётся из ядра,
                // выбранная категория оставляет на экране только её задачи.
                val allCategories = tasksState.map { it.category }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                val visibleTasks = if (selectedCategory == null) tasksState.toList()
                else tasksState.filter { it.category == selectedCategory }

                TodoScreen(
                    tasks = visibleTasks,
                    categories = allCategories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    onAddTaskClick = { showAddDialog = true },
                    onTaskCheckedChange = { taskEntity, isChecked ->
                        val status = if (isChecked) 1 else 0
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                goTodoService?.updateTaskStatus(
                                    taskEntity.id.toLong(),
                                    status.toLong()
                                )
                                // Выполненная задача больше не должна будить уведомлением,
                                // а возврат в "активные" — заново взводит будильник.
                                if (isChecked) {
                                    AndroidAlarmManager.cancelAllForTask(this@MainActivity, taskEntity.id)
                                } else {
                                    AndroidAlarmManager.scheduleNotification(
                                        this@MainActivity,
                                        taskEntity.id,
                                        taskEntity.title,
                                        taskEntity.date,
                                        taskEntity.time
                                    )
                                }
                                refreshUi()
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка изменения статуса в Go: ${e.message}")
                            }
                        }
                    },
                    onTaskEditClick = { taskEntity -> taskToEditId = taskEntity.id },
                    onTaskMenuClick = { taskEntity -> taskToDeleteId = taskEntity.id }
                )

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
                                                goTodoService?.deleteTask(id.toLong())
                                                // Задача удалена — снимаем её будильник и уведомление,
                                                // иначе будильник сработает по несуществующей задаче.
                                                AndroidAlarmManager.cancelAllForTask(this@MainActivity, id)
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

                // Диалог добавления задачи: те же поля, что и при редактировании.
                if (showAddDialog) {
                    val currentCal = Calendar.getInstance()
                    TaskEditorDialog(
                        title = "Новая цель",
                        initialText = "",
                        initialDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(currentCal.time),
                        initialTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentCal.time),
                        initialCategory = selectedCategory ?: "",
                        knownCategories = allCategories,
                        confirmLabel = "СОХРАНИТЬ",
                        onSave = { text, date, time, category ->
                            addTask(text, date, time, category)
                            showAddDialog = false
                        },
                        onDismiss = { showAddDialog = false }
                    )
                }

                // Диалог редактирования: подставляется текущее содержимое задачи,
                // по кнопке «ПРИМЕНИТЬ» задача переписывается в ядре, а будильник
                // перевзводится на новое время.
                taskToEditId?.let { editId ->
                    tasksState.firstOrNull { it.id == editId }?.let { task ->
                        TaskEditorDialog(
                            title = "Редактирование",
                            initialText = task.title,
                            initialDate = task.date,
                            initialTime = task.time,
                            initialCategory = task.category,
                            knownCategories = (allCategories + task.category)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .sorted(),
                            confirmLabel = "ПРИМЕНИТЬ",
                            onSave = { text, date, time, category ->
                                editTask(task, text, date, time, category)
                                taskToEditId = null
                            },
                            onDismiss = { taskToEditId = null }
                        )
                    }
                }
            }
        }
    }

    private fun addTask(text: String, date: String, time: String, category: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Вся логика форматирования дат и инсерта ушла в Go-слой
                val insertedId = goTodoService
                    ?.addTaskWithCategoryAndGetID(text, date, time, category) ?: -1L

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

    private fun editTask(original: TaskEntity, text: String, date: String, time: String, category: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Редактирование целиком уходит в Go-ядро: title, date, time и category
                // переписываются одной операцией, ID и статус задачи сохраняются.
                goTodoService?.updateTask(original.id.toLong(), text, date, time, category)

                // Будильник обязан следовать за новым временем: снимаем старый
                // и взводим новый. Выполненную задачу не будим.
                AndroidAlarmManager.cancelAllForTask(this@MainActivity, original.id)
                if (original.status == 0) {
                    AndroidAlarmManager.scheduleNotification(this@MainActivity, original.id, text, date, time)
                }

                withContext(Dispatchers.Main) {
                    refreshUi()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка редактирования задачи через Go: ${e.message}")
            }
        }
    }

    private fun refreshUi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Запрашиваем JSON строку у Go
                val jsonTasks = goTodoService?.allTasksSortedJSON ?: "[]"

                // Десериализуем её в список объектов Kotlin
                val type = object : TypeToken<List<TaskEntity>>() {}.type
                val freshTasks: List<TaskEntity> = gson.fromJson(jsonTasks, type) ?: emptyList()

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

                // Глушим циклический сигнал и закрываем экран будильника,
                // если он был открыт (например, выключали кнопкой из шторки).
                AlarmSoundPlayer.stop()
                sendBroadcast(
                    Intent(AlarmActivity.ACTION_STOP_ALARM_UI).setPackage(packageName)
                )

                if (taskId != -1 && taskId != 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // Приводим taskId и статус к типу Long с помощью .toLong()
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
