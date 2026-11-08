package com.example.elitetodo

import android.content.Context
import android.content.SharedPreferences

/**
 * Хранилище правил повтора задач (SharedPreferences).
 *
 * Правило живёт в Kotlin-слое, ключ — «recurrence_<id задачи>». Go-ядро
 * и его AAR не меняются, пересборка gomobile не требуется. Правило NONE
 * вообще не записывается: хранилище остаётся компактным.
 */
object RecurrenceStore {

    private const val PREFS = "task_recurrence"
    private const val KEY_PREFIX = "recurrence_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(taskId: Int) = "$KEY_PREFIX$taskId"

    /** Правило повтора задачи; у задач без повтора — TaskRecurrence с type = NONE. */
    fun get(context: Context, taskId: Int): TaskRecurrence =
        TaskRecurrence.decode(prefs(context).getString(key(taskId), null))

    /** Сохраняет правило; NONE удаляет запись, а не хранит пустышку. */
    fun set(context: Context, taskId: Int, recurrence: TaskRecurrence) {
        val editor = prefs(context).edit()
        if (recurrence.isRepeating) {
            editor.putString(key(taskId), recurrence.normalized().encode())
        } else {
            editor.remove(key(taskId))
        }
        editor.apply()
    }

    /** Полное удаление правила (вместе с удалением задачи). */
    fun remove(context: Context, taskId: Int) {
        prefs(context).edit().remove(key(taskId)).apply()
    }
}
