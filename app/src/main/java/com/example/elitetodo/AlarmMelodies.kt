package com.example.elitetodo

import android.content.Context
import android.net.Uri

/**
 * Реестр мелодий будильника.
 *
 * Выбранная мелодия ОДНА на все случаи: она играет и на полноэкранном
 * будильнике при заблокированном экране, и в шторке при разблокированном —
 * потому что единственный источник звука (AlarmSoundPlayer) берёт её отсюда.
 *
 * Помимо варианта «По умолчанию» в приложение пакуются пять встроенных
 * мелодий (res/raw, зацикленные WAV): их можно слушать в настройках и
 * выбирать без похода в системные настройки Android.
 *
 * «По умолчанию» — это НЕ системный сигнал будильника Android, а звук,
 * выбранный пользователем в настройках САМОГО ПРИЛОЖЕНИЯ:
 * Настройки Android → Приложения → Tick-Task → Уведомления → канал
 * «Мелодия “По умолчанию”» → Звук. Там можно выбрать любую системную
 * мелодию или файл — приложение прочитает её из канала и будет звонить
 * именно ею. Если пользователь ничего не задавал, звучит системный сигнал
 * УВЕДОМЛЕНИЙ по умолчанию (не сигнал будильника системы).
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

    /**
     * «По умолчанию» — звук из настроек уведомлений Android для этого
     * приложения (см. описание класса). id сохранён прежний ("system"),
     * чтобы не ломать выбор пользователей прошлых версий.
     */
    val SYSTEM = Melody(SettingsStore.DEFAULT_ALARM_SOUND, "По умолчанию (настройки Android)", 0)

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
     * для «По умолчанию» — звук из канала уведомлений приложения, который
     * пользователь задал в системных настройках Android; если он не задан —
     * системный сигнал уведомлений по умолчанию (но НЕ системный будильник).
     */
    fun resolveUri(context: Context, id: String): Uri? {
        val melody = byId(id)
        if (melody.rawResId != 0) {
            return Uri.parse(
                "android.resource://${context.packageName}/${melody.rawResId}"
            )
        }
        // Канал создаётся/читается в AndroidAlarmManager — он отвечает за
        // все каналы уведомлений приложения.
        return AndroidAlarmManager.defaultMelodyUri(context)
    }
}
