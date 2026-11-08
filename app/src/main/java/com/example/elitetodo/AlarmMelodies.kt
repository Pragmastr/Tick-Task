package com.example.elitetodo

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/**
 * Реестр мелодий будильника.
 *
 * Выбранная мелодия ОДНА на все случаи: она играет и на полноэкранном
 * будильнике при заблокированном экране, и в шторке при разблокированном —
 * потому что единственный источник звука (AlarmSoundPlayer) берёт её отсюда.
 *
 * Помимо системного сигнала в приложение пакуются пять встроенных мелодий
 * (res/raw, зацикленные WAV): их можно слушать в настройках и выбирать
 * без похода в системные настройки Android.
 */
object AlarmMelodies {

    /**
     * Описание мелодии: id для настроек, человекочитаемое имя,
     * ресурс из res/raw (0 — значит «системный сигнал будильника»).
     */
    data class Melody(
        val id: String,
        val title: String,
        val rawResId: Int
    )

    /** Системный сигнал будильника, установленный в Android. */
    val SYSTEM = Melody(SettingsStore.DEFAULT_ALARM_SOUND, "Системный сигнал", 0)

    /** Встроенные мелодии приложения. */
    val BUILT_IN = listOf(
        Melody("alarm_classic", "Классика", R.raw.alarm_classic),
        Melody("alarm_pulse", "Пульс", R.raw.alarm_pulse),
        Melody("alarm_sunrise", "Рассвет", R.raw.alarm_sunrise),
        Melody("alarm_wave", "Волна", R.raw.alarm_wave),
        Melody("alarm_chime", "Перезвон", R.raw.alarm_chime)
    )

    /** Полный список для экрана настроек: системная первой. */
    val ALL: List<Melody> = listOf(SYSTEM) + BUILT_IN

    fun byId(id: String): Melody = ALL.firstOrNull { it.id == id } ?: SYSTEM

    /**
     * URI мелодии для MediaPlayer. Для встроенных — android.resource://,
     * для системной — текущий сигнал будильника Android с каскадом фолбэков
     * (будильник → рингтон → уведомление), как принято в будильных приложениях.
     */
    fun resolveUri(context: Context, id: String): Uri? {
        val melody = byId(id)
        if (melody.rawResId != 0) {
            return Uri.parse(
                "android.resource://${context.packageName}/${melody.rawResId}"
            )
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}
