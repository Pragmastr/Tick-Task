package com.example.elitetodo

import android.content.Context
import android.content.SharedPreferences

/**
 * Учёт отложенных (snooze) будильников: id задачи → время следующего звонка.
 *
 * Нужен потому, что AlarmManager стирает ВСЕ системные будильники при
 * выключении телефона (тот же мотив, что у BootReceiver). Если пользователь
 * отложил сигнал и перезагрузил телефон, по этой таблице отложка
 * перевзводится на сохранённое время, а не теряется до следующего запуска
 * приложения. Запись снимается в момент, когда отложенный сигнал зазвонил,
 * либо когда задача удалена/выполнена/отменена.
 */
object SnoozeStore {

    private const val PREFS = "alarm_snooze"
    private const val KEY_PREFIX = "snooze_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(taskId: Int) = "$KEY_PREFIX$taskId"

    /** Время отложенного звонка (epoch мс) или 0, если отложки нет. */
    fun get(context: Context, taskId: Int): Long =
        prefs(context).getLong(key(taskId), 0L)

    fun set(context: Context, taskId: Int, fireAtMillis: Long) {
        prefs(context).edit().putLong(key(taskId), fireAtMillis).apply()
    }

    fun clear(context: Context, taskId: Int) {
        prefs(context).edit().remove(key(taskId)).apply()
    }
}
