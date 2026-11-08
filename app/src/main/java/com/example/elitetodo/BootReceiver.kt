package com.example.elitetodo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import todo.Todo

/**
 * Восстанавливает все активные будильники после перезагрузки телефона или
 * обновления приложения.
 *
 * Android стирает ВСЕ системные будильники (AlarmManager) при выключении —
 * без этого ресивера напоминания умирали бы до первого запуска приложения.
 * Теперь при каждой загрузке системы мы читаем бессрочный список задач
 * из Go-ядра (SQLite) и взводим будильники заново: напоминание, созданное
 * сегодня, сработает и через 10 лет — после любого числа перезагрузок.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "android.intent.action.HTC_QUICKBOOT_POWERON"
        val isUpdate = action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBoot && !isUpdate) return

        Log.d("BootReceiver", "Восстановление будильников после: $action")

        try {
            val dbPath = context.getDatabasePath("elite_final.db").absolutePath
            val service = Todo.newTodoService(dbPath)
            val json = try {
                service.getAllTasksSortedJSON()
            } finally {
                try {
                    service.close()
                } catch (_: Exception) {
                }
            }

            val type = object : TypeToken<List<TaskEntity>>() {}.type
            val tasks: List<TaskEntity> = Gson().fromJson(json, type) ?: emptyList()

            var armed = 0
            for (task in tasks) {
                if (task.status != 0) continue

                // Отложка тоже переживает перезагрузку: если пользователь
                // отложил будильник и выключил телефон, перевзводим её на
                // сохранённое время (SnoozeStore), а не теряем до перезвона.
                val snoozeAt = SnoozeStore.get(context, task.id)
                if (snoozeAt > System.currentTimeMillis()) {
                    AndroidAlarmManager.rearmSnooze(context, task.id, task.title, snoozeAt)
                } else {
                    // scheduleNotification сам пропустит задачи с прошедшей датой.
                    AndroidAlarmManager.scheduleNotification(
                        context,
                        task.id,
                        task.title,
                        task.date,
                        task.time
                    )
                }
                armed++
            }
            Log.d("BootReceiver", "Перевзведено будильников: $armed из ${tasks.size}")
        } catch (e: Exception) {
            Log.e("BootReceiver", "Ошибка восстановления будильников: ${e.message}", e)
        }
    }
}
