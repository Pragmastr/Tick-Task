package com.example.elitetodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Кнопка «ОТЛОЖИТЬ» в уведомлении будильника.
 *
 * Зарегистрирован в манифесте, поэтому срабатывает даже при убитом процессе.
 * Получив команду, глушит играющий сигнал, прячет уведомление будильника и
 * перевзводит системный будильник на интервал отложки из настроек
 * (SettingsStore.snoozeMinutes). Сама задача НЕ отмечается выполненной —
 * после отложки она по-прежнему активна и зазвонит заново.
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmManager.ACTION_SNOOZE_ALARM) return

        val taskId = intent.getStringExtra("alarm_task_id")?.toIntOrNull() ?: -1
        val title = intent.getStringExtra("alarm_task_title") ?: "Задача"
        if (taskId == -1) return

        Log.d("SnoozeReceiver", "Откладываем будильник ID=$taskId «$title»")
        AndroidAlarmManager.snoozeAlarm(context, taskId, title)
    }
}
