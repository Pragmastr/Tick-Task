package com.example.elitetodo


import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Locale

object AndroidAlarmManager {
    private const val TAG = "AndroidAlarmManager"

    // v6: канал полностью ТИХИЙ (без звука и вибрации канала). Прежний канал
    // v5 звонил собственным сигналом системы и перескакивался с MediaPlayer
    // будильника — мелодии накладывались. Новый id заставляет систему
    // пересоздать канал со свежими настройками у уже установленных сборок.
    private const val CHANNEL_ID = "todo_alarm_channel_v6"

    /**
     * Канал «Мелодия “По умолчанию”»: ЕДИНСТВЕННОЕ место, где пользователь
     * задаёт звук для варианта мелодии «По умолчанию» — прямо из настроек
     * Android (Приложения → Tick-Task → Уведомления → этот канал → Звук).
     * Сам канал НИКОГДА не используется для показа уведомлений: приложение
     * только читает из него выбранный звук (NotificationChannel.sound) и
     * играет его через AlarmSoundPlayer. Поэтому переключение звука в
     * настройках Android не может наложиться на зацикленный сигнал плеера.
     */
    const val DEFAULT_SOUND_CHANNEL_ID = "todo_default_sound_channel"

    /** Команда «отложить будильник» (кнопка в уведомлении → SnoozeReceiver). */
    const val ACTION_SNOOZE_ALARM = "ACTION_SNOOZE_ALARM"

    // Слоты PendingIntent: основные будильники taskId+2000, отложенные
    // (snooze) taskId+4000, кнопка «Отложить» в уведомлении taskId+5000,
    // уведомления taskId+1000, полноэкранный интент taskId+3000.

    /**
     * Гарантирует наличие обоих каналов уведомлений: тихого будильного
     * (через него публикуются уведомления) и звукового «Мелодия “По
     * умолчанию”» (только хранит звук, выбранный в настройках Android).
     * Идемпотентно: создание существующего канала — no-op.
     */
    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Будильник Tick-Task",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Напоминания-будильники; сигнал играет в приложении громкостью будильника"
                    // Тихий канал: звук и вибрация — целиком на AlarmSoundPlayer,
                    // чтобы два сигнала не звонили одновременно.
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (notificationManager.getNotificationChannel(DEFAULT_SOUND_CHANNEL_ID) == null) {
                val soundChannel = NotificationChannel(
                    DEFAULT_SOUND_CHANNEL_ID,
                    "Мелодия «По умолчанию»",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Задайте здесь звук для варианта «По умолчанию» в настройках " +
                            "мелодии приложения. Уведомления этот канал не показывает."
                    // Звук изначально не задан: resolveUri тогда сыграет
                    // системный сигнал уведомлений. Пользователь может в любой
                    // момент выбрать свою мелодию в настройках Android.
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(soundChannel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка создания каналов]: ${e.message}", e)
        }
    }

    /**
     * Звук варианта «По умолчанию»: то, что пользователь выбрал в настройках
     * Android для ЭТОГО приложения (канал [DEFAULT_SOUND_CHANNEL_ID]).
     * Если пользователь ничего не задавал (канал без звука) либо на устройстве
     * нет каналов (Android 7.x) — системный сигнал УВЕДОМЛЕНИЙ по умолчанию.
     * Системный сигнал БУДИЛЬНИКА здесь намеренно не используется.
     */
    fun defaultMelodyUri(context: Context): Uri? {
        ensureNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.getNotificationChannel(DEFAULT_SOUND_CHANNEL_ID)
                    ?.sound
                    ?.let { return it }
            } catch (e: Exception) {
                Log.e(TAG, "[Ошибка чтения звука канала]: ${e.message}", e)
            }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

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

            armExact(alarmManager, epochTime, alarmPendingIntent)
            Log.d(TAG, "[Android] Будильник взведён на $epochTime мс (ID=$taskId)")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка Будильника]: ${e.message}", e)
        }
    }

    private fun armExact(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            // Пользователь отозвал SCHEDULE_EXACT_ALARM — деградируем до неточного
            // будильника вместо SecurityException и полной потери напоминания.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Отложить (snooze) будильник задачи: глушит текущий сигнал, прячет
     * уведомление будильника и перевзводит системный будильник на
     * SettingsStore.snoozeMinutes минут от текущего момента. Задача остаётся
     * активной (status = 0) — после отложки она зазвонит снова, а уже при
     * выключении сработает обычная логика (выполнить или переехать на
     * следующий повтор).
     */
    fun snoozeAlarm(context: Context, taskId: Int, title: String) {
        try {
            val minutes = SettingsStore.snoozeMinutes(context).coerceAtLeast(1)
            val fireAt = System.currentTimeMillis() + minutes * 60_000L

            SnoozeStore.set(context, taskId, fireAt)
            rearmSnooze(context, taskId, title, fireAt)

            // Гасим зацикленный сигнал и убираем уведомление будильника:
            // до конца отложки всё тихо.
            AlarmSoundPlayer.stop()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId + 1000)

            // Если на момент отложки был открыт полноэкранный будильник
            // (кнопку нажали в шторке поверх него) — закрываем и его.
            context.sendBroadcast(
                Intent(AlarmActivity.ACTION_STOP_ALARM_UI).setPackage(context.packageName)
            )

            Log.d(TAG, "[Snooze] Будильник ID=$taskId отложен на $minutes мин (сработает в $fireAt)")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка отложки]: ${e.message}", e)
        }
    }

    /** Перевзвод отложенного будильника на сохранённое время (BootReceiver). */
    fun rearmSnooze(context: Context, taskId: Int, title: String, triggerAtMillis: Long) {
        try {
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // Отдельный слот (+4000), чтобы не пересекаться с основным будильником.
            val pendingIntent = PendingIntent.getBroadcast(context, taskId + 4000, alarmIntent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            armExact(alarmManager, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка перевзвода отложки]: ${e.message}", e)
        }
    }

    /**
     * Полная зачистка задачи: снимает системный будильник И отложку,
     * убирает зависшее уведомление. Вызывается при удалении задачи и при
     * отметке её выполненной.
     */
    fun cancelAllForTask(context: Context, taskId: Int) {
        try {
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_LAUNCH_ALARM"
                putExtra("alarm_task_id", taskId.toString())
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Основной будильник.
            val alarmPendingIntent = PendingIntent.getBroadcast(context, taskId + 2000, alarmIntent, flags)
            alarmManager.cancel(alarmPendingIntent)
            alarmPendingIntent.cancel()

            // Отложенный (snooze) будильник умирает вместе с задачей.
            val snoozePendingIntent = PendingIntent.getBroadcast(context, taskId + 4000, alarmIntent, flags)
            alarmManager.cancel(snoozePendingIntent)
            snoozePendingIntent.cancel()
            SnoozeStore.clear(context, taskId)
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

    /**
     * Показывает будильное уведомление.
     *
     * Канал полностью тихий: ни звука, ни вибрации. Сигнал играет
     * AlarmSoundPlayer (громкость будильника, зацикленная мелодия из
     * настроек) — поэтому мелодии больше не накладываются друг на друга.
     *
     * Поведение зависит от состояния экрана (решает система по
     * full-screen intent):
     *  - экран заблокирован → система поднимает полноэкранный будильник
     *    (AlarmActivity), и он заводит звук;
     *  - экран разблокирован → уведомление выезжает шторкой (heads-up)
     *    поверх текущего приложения, а звук из ресивера заводит
     *    AlarmReceiver — та же мелодия AlarmSoundPlayer.
     *
     * Кнопки уведомления: «ОТЛОЖИТЬ» (SnoozeReceiver, работает при убитом
     * процессе) и «ВЫКЛЮЧИТЬ» (MainActivity, задача выполняется либо, если
     * у неё есть повтор, переезжает на следующий слот).
     */
    fun showAlarmNotification(context: Context, taskId: Int, title: String) {
        try {
            // Тихий будильный канал + звуковой канал «Мелодия “По умолчанию”».
            ensureNotificationChannels(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

            // «ОТЛОЖИТЬ»: через манифестный SnoozeReceiver, поэтому кнопка
            // работает даже когда процесс приложения убит системой.
            val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
                putExtra("alarm_task_id", taskId.toString())
                putExtra("alarm_task_title", title)
            }
            val snoozePending = PendingIntent.getBroadcast(context, taskId + 5000, snoozeIntent, immutableFlags)

            val appIconBitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("⏰ СРАБОТАЛ БУДИЛЬНИК!")
                .setContentText(title)
                .setSmallIcon(R.drawable.ic_notification)
                // PRIORITY_MAX + CATEGORY_ALARM + тихий канал IMPORTANCE_HIGH:
                // на разблокированном экране уведомление выезжает шторкой (heads-up),
                // на заблокированном система поднимает полноэкранный будильник.
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setLargeIcon(appIconBitmap)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "ОТЛОЖИТЬ", snoozePending)
                .addAction(R.drawable.ic_notification, "ВЫКЛЮЧИТЬ", dismissPending)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                // Тап по шторке на разблокированном экране открывает экран будильника.
                .setContentIntent(fullScreenPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val notification = builder.build()

            notificationManager.notify(taskId + 1000, notification)
            Log.d(TAG, "[showAlarmNotification] Тихое уведомление отправлено для ID: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "[Ошибка при показе пуша]: ${e.message}", e)
        }
    }

}
