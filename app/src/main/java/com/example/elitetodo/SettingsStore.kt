package com.example.elitetodo

import android.content.Context
import android.content.SharedPreferences

/**
 * Хранилище настроек приложения на SharedPreferences.
 *
 * Тема и мелодия будильника переживают перезапуск процесса и перезагрузку
 * телефона. Мелодия читается из хранилища в момент КАЖДОГО запуска сигнала —
 * поэтому выбранная мелодия едина и для полноэкранного будильника на
 * заблокированном экране, и для уведомления-шторки при разблокированном:
 * обе точки звука спрашивают её здесь.
 */
object SettingsStore {

    private const val PREFS = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ALARM_SOUND = "alarm_sound_id"

    /** Режим темы: вслед за системной настройкой Android. */
    const val THEME_SYSTEM = 0

    /** Режим темы: всегда светлая. */
    const val THEME_LIGHT = 1

    /** Режим темы: всегда тёмная. */
    const val THEME_DARK = 2

    /** Мелодия по умолчанию — системный сигнал будильника Android. */
    const val DEFAULT_ALARM_SOUND = "system"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Текущий режим темы: THEME_SYSTEM / THEME_LIGHT / THEME_DARK. */
    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /** Id выбранной мелодии будильника (см. AlarmMelodies). */
    fun alarmSoundId(context: Context): String =
        prefs(context).getString(KEY_ALARM_SOUND, DEFAULT_ALARM_SOUND)
            ?: DEFAULT_ALARM_SOUND

    fun setAlarmSoundId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ALARM_SOUND, id).apply()
    }
}
