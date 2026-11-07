package com.example.elitetodo

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import todo.Todo

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val taskIdStrFromIntent = intent.getStringExtra("alarm_task_id") ?: ""
        val taskIdInt = taskIdStrFromIntent.toIntOrNull() ?: 0
        val taskIdString = taskIdInt.toString()
        val taskTitle = intent.getStringExtra("alarm_task_title") ?: "Задача"

        android.util.Log.d("EliteTodoLOG", "AlarmActivity зажглась! ID = $taskIdInt, Заголовок = '$taskTitle'")

        setContent {
            AlarmActivityContent(taskTitle = taskTitle) {
                if (taskIdInt != 0) {
                    // Используем фоновый поток через CoroutineScope для вызова Go-библиотеки
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dbPath = applicationContext.getDatabasePath("elite_final.db").absolutePath
                            val goTodoService = Todo.newTodoService(dbPath)

                            // ИСПРАВЛЕНО: Применяем .toLong() к первому аргументу и суффикс L ко второму
                            goTodoService.updateTaskStatus(taskIdInt.toLong(), 1L)
                            goTodoService.close()
                            android.util.Log.d("EliteTodoLOG", "AlarmActivity: Статус задачи $taskIdInt сохранен через Go!")
                        } catch (e: Exception) {
                            android.util.Log.e("EliteTodoLOG", "Ошибка записи в Go из AlarmActivity: ${e.message}")
                        }
                    }
                }

                val dismissIntent = Intent(this@AlarmActivity, MainActivity::class.java).apply {
                    action = "ACTION_DISMISS_ALARM"
                    putExtra("task_id", taskIdString)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(dismissIntent)
                finish()
            }
        }
    }

}

@Composable
fun AlarmActivityContent(taskTitle: String, onDismiss: () -> Unit) {
    AlarmScreen(taskTitle = taskTitle, onDismiss = onDismiss)
}
