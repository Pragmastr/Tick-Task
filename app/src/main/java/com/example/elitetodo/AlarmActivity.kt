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

        val taskIdInt = intent.getStringExtra("alarm_task_id")?.toIntOrNull() ?: -1
        val taskTitle = intent.getStringExtra("alarm_task_title") ?: "Задача"

        android.util.Log.d("EliteTodoLOG", "AlarmActivity зажглась! ID = $taskIdInt, Заголовок = '$taskTitle'")

        setContent {
            AlarmActivityContent(taskTitle = taskTitle) {
                // Запись статуса "Выполнено", снятие уведомления и обновление UI целиком
                // делегируем MainActivity (ACTION_DISMISS_ALARM): у него уже открыт
                // единственный Go-сервис БД. Второе подключение отсюда ловило
                // SQLITE_BUSY ("database is locked") и дублировало запись статуса.
                val dismissIntent = Intent(this@AlarmActivity, MainActivity::class.java).apply {
                    action = "ACTION_DISMISS_ALARM"
                    putExtra("task_id", taskIdInt.toString())
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
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
