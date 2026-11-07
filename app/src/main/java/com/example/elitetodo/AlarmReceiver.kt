package com.example.elitetodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "[Ресивер] Сигнал будильника получен системным ядром!")

        if (intent.action == "ACTION_LAUNCH_ALARM") {
            val taskIdStr = intent.getStringExtra("alarm_task_id") ?: ""
            val taskId = taskIdStr.toIntOrNull() ?: -1
            val title = intent.getStringExtra("alarm_task_title") ?: "Задача"

            if (taskId != -1) {
                // Вызываем метод показа уведомления, который у тебя уже написан
                AndroidAlarmManager.showAlarmNotification(context, taskId, title)
            }
        }
    }
}
