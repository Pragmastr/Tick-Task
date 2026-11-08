package com.example.elitetodo

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

class AlarmActivity : ComponentActivity() {

    companion object {
        /** Внутренняя команда «выключить сигнал и закрыть экран будильника». */
        const val ACTION_STOP_ALARM_UI = "com.example.elitetodo.ACTION_STOP_ALARM_UI"
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Пользователь нажал «ВЫКЛЮЧИТЬ» в уведомлении, пока этот экран был открыт:
            // глушим бесконечный сигнал и закрываемся.
            AlarmSoundPlayer.stop()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Блокируем поворот экрана: будильник всегда отображается портретно,
        // даже на заблокированном экране и при включённом автоповороте системы.
        // Дублируем манифестный android:screenOrientation="portrait" на случай,
        // если активити пересоздастся системой в неожиданной ориентации.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

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
        // Экран будильника не гаснет, пока играет сигнал.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val taskIdInt = intent.getStringExtra("alarm_task_id")?.toIntOrNull() ?: -1
        val taskTitle = intent.getStringExtra("alarm_task_title") ?: "Задача"

        android.util.Log.d("EliteTodoLOG", "AlarmActivity зажглась! ID = $taskIdInt, Заголовок = '$taskTitle'")

        // Циклический бесконечный сигнал: мелодия и вибрация крутятся по кругу,
        // пока пользователь не смахнёт экран вверх или не нажмёт «ВЫКЛЮЧИТЬ».
        // Если сигнал уже завёл AlarmReceiver (случай разблокированного экрана),
        // start() ничего не перезапускает — мелодия продолжается бесшовно.
        AlarmSoundPlayer.start(this)

        // Подмена уведомления на тихое больше не нужна: будильный канал теперь
        // всегда тихий, единственный звук — AlarmSoundPlayer, наложений нет.
        // Уведомление из ресивера остаётся в шторке с кнопкой «ВЫКЛЮЧИТЬ».

        // Слушаем команду остановки, присланную из MainActivity (кнопка в шторке).
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(ACTION_STOP_ALARM_UI),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            AlarmActivityContent(taskTitle = taskTitle) {
                // Запись статуса "Выполнено", снятие уведомления и обновление UI целиком
                // делегируем MainActivity (ACTION_DISMISS_ALARM): у него уже открыт
                // единственный Go-сервис БД. Второе подключение отсюда ловило
                // SQLITE_BUSY ("database is locked") и дублировало запись статуса.
                AlarmSoundPlayer.stop()
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }
        // Страховка: сигнал не должен остаться играть после смерти активити.
        AlarmSoundPlayer.stop()
    }
}

@Composable
fun AlarmActivityContent(taskTitle: String, onDismiss: () -> Unit) {
    AlarmScreen(taskTitle = taskTitle, onDismiss = onDismiss)
}
