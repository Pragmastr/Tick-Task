package com.example.elitetodo


import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Locale

object AndroidAlarmManager {
    private const val TAG = "AndroidAlarmManager"
    private const val CHANNEL_ID = "todo_alarm_channel_v5"
    private const val CHANNEL_UI_ID = "todo_alarm_ui_channel_v1"

    fun scheduleNotification(context: Context, taskId: Int, title: String, dateStr: String, timeStr: String) {
        try {
            val dateTimeStr = "$dateStr $timeStr"
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateTimeStr) ?: return
            val epochTime = date.time

            // Не взводим будильник в прошлое: в лучшем случае он сработал бы мгновенно
            // и разбудил пользователя по уже прошедшей задаче.
            if (epochTime <= System.currentTimeMillis()) {
                Log.d(TAG, "[Android] Будильник для ID=$taskId не взведён: время $dateTimeStr уже прошло")
                return
            }

            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Создаем PendingIntent для БРОДКАСТА (getBroadcast вместо getActivity)
            val alarmPendingIntent = PendingIntent.getBroadcast(context, taskId + 2000, alarmIntent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        epochTime,
                        alarmPendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochTime, alarmPendingIntent)
                }
            } else {
                // Пользователь отозвал SCHEDULE_EXACT_ALARM — деградируем до неточного
                // будильника вместо SecurityException и полной потери напоминания.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochTime, alarmPendingIntent)
                Log.w(TAG, "[Android] Точные будильники запрещены, взведён неточный для ID=$taskId")
            }

            Log.d(TAG, "[Android] Будильник взведён на $epochTime мс (ID=$taskId)")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка Будильника]: ${e.message}", e)
        }
    }

    /**
     * Полная зачистка задачи: снимает системный будильник и убирает зависшее
     * уведомление. Вызывается при удалении задачи и при отметке её выполненной.
     */
    fun cancelAllForTask(context: Context, taskId: Int) {
        try {
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val alarmPendingIntent = PendingIntent.getBroadcast(context, taskId + 2000, alarmIntent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(alarmPendingIntent)
            alarmPendingIntent.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка снятия будильника]: ${e.message}", e)
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId + 1000)
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка отмены уведомления]: ${e.message}", e)
        }
    }

    fun showAlarmNotification(context: Context, taskId: Int, title: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alarmSound = Uri.parse("content://settings/system/alarm_alert")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                    val importance = NotificationManager.IMPORTANCE_MAX
                    val channel = NotificationChannel(CHANNEL_ID, "Будильник Списка Дел", importance).apply {
                        description = "Уведомления точных будильников с кнопками управления"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 600, 800)

                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(alarmSound, audioAttributes)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }

            // Направляем интент на наш ресивер с фейковым экшеном, чтобы система разрешила зажечь экран, но ничего не открывала
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val fullScreenPendingIntent = PendingIntent.getActivity(context, taskId + 3000, alarmIntent, immutableFlags)
            val dismissIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_DISMISS_ALARM"
                putExtra("task_id", taskId.toString())
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val dismissPending = PendingIntent.getActivity(context, taskId + 1000, dismissIntent, immutableFlags)

            val appIconBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("⏰ СРАБОТАЛ БУДИЛЬНИК!")
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setLargeIcon(appIconBitmap)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "ВЫКЛЮЧИТЬ", dismissPending)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val notification = builder.build()
            // FLAG_INSISTENT: система повторяет сигнал уведомления ПО КРУГУ бесконечно,
            // пока уведомление не будет снято и пользователь не выключит будильник.
            // Если полноэкранный интент сработал — экран будильника подменит это
            // уведомление тихим и сам заведёт зацикленный MediaPlayer.
            notification.flags = notification.flags or Notification.FLAG_INSISTENT

            notificationManager.notify(taskId + 1000, notification)
            Log.d(TAG, "[showAlarmNotification] Уведомление отправлено для ID: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка при показе пуша]: ${e.message}", e)
        }
    }

    /**
     * Тихая замена будильного уведомления. Вызывается из AlarmActivity, когда
     * полноэкранный будильник уже играет собственный зацикленный сигнал:
     * без неё уведомление продолжало бы звонить поверх экрана будильника.
     * Уведомление остаётся в шторке с кнопкой «ВЫКЛЮЧИТЬ».
     */
    fun showSilentAlarmUiNotification(context: Context, taskId: Int, title: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(CHANNEL_UI_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_UI_ID,
                        "Экран будильника",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Показывается, пока экран будильника играет сигнал"
                        setSound(null, null)
                        enableVibration(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }

            val immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val dismissIntent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_DISMISS_ALARM"
                putExtra("task_id", taskId.toString())
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val dismissPending = PendingIntent.getActivity(context, taskId + 1000, dismissIntent, immutableFlags)

            val appIconBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            val builder = NotificationCompat.Builder(context, CHANNEL_UI_ID)
                .setContentTitle("⏰ НАСТАЛО ВРЕМЯ ЗАДАЧИ")
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setLargeIcon(appIconBitmap)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "ВЫКЛЮЧИТЬ", dismissPending)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(taskId + 1000, builder.build())
            Log.d(TAG, "[showSilentAlarmUiNotification] Тихое уведомление для ID: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка тихого уведомления]: ${e.message}", e)
        }
    }
}
