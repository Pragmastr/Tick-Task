package com.example.elitetodo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * ЕДИНСТВЕННЫЙ источник звука и вибрации будильника в приложении.
 *
 * Раньше мелодии накладывались: уведомительный канал звонил своим сигналом
 * (FLAG_INSISTENT повторял его бесконечно), и параллельно этот MediaPlayer
 * играл свою мелодию — на заблокированном экране слышались обе. Теперь канал
 * уведомлений полностью тихий (AndroidAlarmManager), и звук ведёт только этот
 * плеер: наложение физически невозможно.
 *
 * Плеер ведёт себя как настоящий будильник:
 *  - играет громкостью будильника (USAGE_ALARM), а не уведомлений;
 *  - мелодия зациклена (isLooping) и звонит, пока будильник не выключат
 *    свайпом по экрану или кнопкой «ВЫКЛЮЧИТЬ» в уведомлении;
 *  - вибрация идёт повторяющимся паттерном без остановки;
 *  - мелодия берётся из настроек (SettingsStore) — одна и та же для звонка
 *    на заблокированном экране и для шторки при разблокированном.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /** Играет ли сигнал прямо сейчас. */
    val isPlaying: Boolean
        get() = mediaPlayer != null

    fun start(context: Context) {
        // Защита от наложения и перезапуска: если сигнал уже играет (например,
        // его завёл AlarmReceiver на разблокированном экране, а затем поднялся
        // AlarmActivity), повторный start() ничего не делает — мелодия
        // продолжается бесшовно, а не наслаивается сама на себя.
        if (isPlaying) {
            Log.d(TAG, "Сигнал уже играет, повторный запуск пропущен")
            return
        }

        val appContext = context.applicationContext

        try {
            val alarmUri = AlarmMelodies.resolveUri(
                appContext,
                SettingsStore.alarmSoundId(appContext)
            )

            if (alarmUri != null) {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // громкость будильника, а не уведомлений
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(appContext, alarmUri)
                // Ключевая строка: циклическое воспроизведение до бесконечности.
                player.isLooping = true
                player.prepare()
                player.start()
                mediaPlayer = player
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить циклический звук будильника: ${e.message}", e)
        }

        try {
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // Паттерн: 0мс пауза, 600мс вибрация, 800мс тишина; repeat = 0 — цикл с начала.
            val pattern = longArrayOf(0, 600, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
            vibrator = vib
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить циклическую вибрацию: ${e.message}", e)
        }
    }

    /** Глушит и звук, и вибрацию. Безопасно вызывать сколько угодно раз. */
    fun stop() {
        try {
            mediaPlayer?.let { player ->
                try {
                    player.stop()
                } catch (_: Exception) {
                }
                try {
                    player.release()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }
}
