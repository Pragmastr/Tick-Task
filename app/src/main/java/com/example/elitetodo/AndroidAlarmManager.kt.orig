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

    fun scheduleNotification(context: Context, taskId: Int, title: String, dateStr: String, timeStr: String) {
        try {
            val dateTimeStr = "$dateStr $timeStr"
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val date = sdf.parse(dateTimeStr) ?: return
            val epochTime = date.time

            // ИСПРАВЛЕНО 1: Направляем интент в невидимый системный ресивер, а не на экран Активити
            //val receiverClass = Class.forName("${context.packageName}.AlarmReceiver")
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
            }

            // ИСПРАВЛЕНО 2: Для ресиверов в Android 13+ используем FLAG_IMMUTABLE (это безопаснее и железно работает)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Создаем PendingIntent для БРОДКАСТА (getBroadcast вместо getActivity)
            val alarmPendingIntent = PendingIntent.getBroadcast(context, taskId + 2000, alarmIntent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // ИСПРАВЛЕНО 3: Переводим на ультимативный метод, который зажигает спящий экран и пробивает Doze Mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, // RTC_WAKEUP силой включает процессор из сна
                    epochTime,
                    alarmPendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochTime, alarmPendingIntent)
            }

            Log.d(TAG, "[Android] Ультимативный будильник взведён на $epochTime мс (пробьет сон и смахнутый кэш)")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка Будильника]: ${e.message}", e)
        }
    }


    fun showAlarmNotification(context: Context, taskId: Int, title: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alarmSound = Uri.parse("content://settings/system/alarm_alert")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {7
                if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                    val importance = NotificationManager.IMPORTANCE_MAX
                    val channel = NotificationChannel(CHANNEL_ID, "Будильник Списка Дел", importance).apply {
                        description = "Уведомления точных будильников с кнопками управления"
                        enableVibration(true)

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
            // ИСПРАВЛЕНО: Направляем интент СТРОГО на AlarmActivity вместо MainActivity
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val immutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(context, Class.forName("${context.packageName}.MainActivity")).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(context, taskId + 3000, alarmIntent, immutableFlags)
            val dismissIntent = Intent(context, Class.forName("${context.packageName}.MainActivity")).apply {
                action = "ACTION_DISMISS_ALARM"
                putExtra("task_id", taskId.toString())
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }



            val dismissPending = PendingIntent.getActivity(context, taskId + 1000, dismissIntent, mutableFlags)

            val appIconBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("⏰ СРАБОТАЛ БУДИЛЬНИК!")
                .setContentText(title)
//                .setSmallIcon(appIconId)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM) //если что поменять на alarm
                .setSound(alarmSound)
                .setLargeIcon(appIconBitmap)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(R.mipmap.ic_launcher, "ВЫКЛЮЧИТЬ", dismissPending)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)


            notificationManager.notify(taskId + 1000, builder.build())
            Log.d(TAG, "[showAlarmNotification] Уведомление отправлено для ID: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка при показе пуша]: ${e.message}", e)
        }
    }
}
