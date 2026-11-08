package com.example.elitetodo

import android.app.KeyguardManager
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
                // Тихое уведомление с full-screen intent: на заблокированном
                // экране система поднимет по нему полноэкранный будильник,
                // на разблокированном покажет шторкой (heads-up).
                AndroidAlarmManager.showAlarmNotification(context, taskId, title)

                val keyguardManager =
                    context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isLocked = keyguardManager.isKeyguardLocked

                if (isLocked) {
                    // Экран заблокирован: звук заведёт AlarmActivity, который
                    // система сейчас поднимет поверх локскрина. Здесь стартовать
                    // его рано — активити лишь продолжит уже играющую мелодию.
                    Log.d("AlarmReceiver", "Экран заблокирован: звук заведёт AlarmActivity")
                } else {
                    // Экран разблокирован: полноэкранная активити НЕ поднимется,
                    // уведомление показывается шторкой. Заводим сигнал сами —
                    // тот же AlarmSoundPlayer и та же мелодия из настроек,
                    // что звонит и на заблокированном экране.
                    Log.d("AlarmReceiver", "Экран разблокирован: заводим сигнал для шторки")
                    AlarmSoundPlayer.start(context)
                }
            }
        }
    }
}
