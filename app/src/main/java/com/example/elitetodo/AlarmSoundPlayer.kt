package com.example.elitetodo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Циклический сигнал будильника.
 *
 * Мелодия запускается с isLooping = true — она повторяется БЕСКОНЕЧНО,
 * пока пользователь не выключит будильник (свайп вверх по экрану будильника
 * или кнопка «ВЫКЛЮЧИТЬ» в шторке). Вибрация тоже идёт повторяющимся
 * паттерном без остановки. Раньше сигнал уведомления проигрывался один раз
 * и замолкал — теперь будильник звонит до тех пор, пока его не выключат.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start(context: Context) {
        // Защита от двойного запуска: сперва глушим всё, что могло остаться играть.
        stop()

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (alarmUri != null) {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // играем в громкости будильника, а не медиа
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(context, alarmUri)
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
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
