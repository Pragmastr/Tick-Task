package com.example.elitetodo

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран настроек.
 *
 *  - «Тема приложения»: как в системе / всегда светлая / всегда тёмная.
 *  - «Мелодия будильника»: «По умолчанию» (звук из настроек Android для
 *    приложения) или одна из пяти встроенных мелодий. Мелодия едина для всех
 *    точек звука приложения: и для полноэкранного будильника на
 *    заблокированном экране, и для уведомления-шторки при разблокированном —
 *    обе точки берут её через AlarmSoundPlayer.
 *  - Переход в системные настройки Android, где выбирается звук для варианта
 *    «По умолчанию» (канал «Мелодия “По умолчанию”»).
 *  - «Кнопка “Отложить”»: интервал переноса сигнала (5/10/15/30 минут).
 *
 * Тап по мелодии сразу сохраняет выбор и проигрывает короткое прослушивание;
 * повторный тап останавливает. Прослушивание — отдельный плеер, он не трогает
 * сигнал настоящего будильника.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Системная кнопка «Назад» тоже возвращает к списку задач.
    BackHandler(onBack = onBack)

    var selectedSoundId by remember { mutableStateOf(SettingsStore.alarmSoundId(context)) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var snoozeMinutes by remember { mutableStateOf(SettingsStore.snoozeMinutes(context)) }

    // Открывает настройки Android именно этого приложения: на Android 8+ —
    // настройки канала «Мелодия “По умолчанию”», там выбирается звук для
    // варианта «По умолчанию». На Android 7 — страницу приложения.
    fun openAndroidSoundSettings() {
        try {
            AndroidAlarmManager.ensureNotificationChannels(context)
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, AndroidAlarmManager.DEFAULT_SOUND_CHANNEL_ID)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // На отдельных прошивках экран канала может отсутствовать —
            // просто ничего не делаем.
        }
    }

    fun stopPreview() {
        previewPlayer?.let { player ->
            try {
                player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
        previewPlayer = null
        previewingId = null
    }

    fun startPreview(melody: AlarmMelodies.Melody) {
        stopPreview()
        try {
            val uri = AlarmMelodies.resolveUri(context, melody.id) ?: return
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.isLooping = false
            player.setOnCompletionListener {
                try {
                    it.release()
                } catch (_: Exception) {
                }
                if (previewPlayer == it) {
                    previewPlayer = null
                    previewingId = null
                }
            }
            player.prepare()
            player.start()
            previewPlayer = player
            previewingId = melody.id
        } catch (_: Exception) {
            // Не смогли проиграть пример — молча остаёмся на выборе.
            stopPreview()
        }
    }

    // Уход с экрана глушит прослушивание.
    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ───────────────────────── ТЕМА ─────────────────────────
            SectionTitle("ТЕМА ПРИЛОЖЕНИЯ")
            SettingsCard {
                ThemeOption(
                    title = "Как в системе",
                    subtitle = "Светлая днём, тёмная ночью — вслед за Android",
                    selected = themeMode == SettingsStore.THEME_SYSTEM,
                    onClick = { onThemeModeChanged(SettingsStore.THEME_SYSTEM) }
                )
                HorizontalDivider()
                ThemeOption(
                    title = "Светлая",
                    subtitle = "Белый фон, тёмный текст",
                    selected = themeMode == SettingsStore.THEME_LIGHT,
                    onClick = { onThemeModeChanged(SettingsStore.THEME_LIGHT) }
                )
                HorizontalDivider()
                ThemeOption(
                    title = "Тёмная",
                    subtitle = "Тёмный фон — комфортно для глаз ночью",
                    selected = themeMode == SettingsStore.THEME_DARK,
                    onClick = { onThemeModeChanged(SettingsStore.THEME_DARK) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ──────────────────── МЕЛОДИЯ БУДИЛЬНИКА ────────────────────
            SectionTitle("МЕЛОДИЯ БУДИЛЬНИКА")
            Text(
                text = "Едина для звонка на заблокированном экране и для шторки " +
                        "при разблокированном. Тап — выбрать и прослушать, " +
                        "повторный тап — остановить.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            SettingsCard {
                AlarmMelodies.ALL.forEachIndexed { index, melody ->
                    if (index > 0) HorizontalDivider()
                    val isSelected = selectedSoundId == melody.id
                    val isPreviewing = previewingId == melody.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPreviewing) {
                                    // Повторный тап по играющей мелодии — стоп.
                                    stopPreview()
                                } else {
                                    selectedSoundId = melody.id
                                    SettingsStore.setAlarmSoundId(context, melody.id)
                                    startPreview(melody)
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = if (isPreviewing) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = melody.title,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (melody.rawResId == 0) {
                                Text(
                                    text = "Звук из настроек Android: Приложения → Tick-Task → " +
                                            "Уведомления → «Мелодия “По умолчанию”» → Звук. " +
                                            "Если звук не задан — системный сигнал уведомлений.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Мелодия играет громкостью будильника (отдельный ползунок в " +
                        "настройках звука) и повторяется по кругу, пока будильник " +
                        "не выключен.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Быстрый переход к настройке звука «По умолчанию» в Android:
            // настройки приложения → уведомления → канал «Мелодия “По умолчанию”».
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openAndroidSoundSettings() }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Column {
                        Text(
                            text = "Выбрать звук «По умолчанию» в Android",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Открывает настройки уведомлений приложения " +
                                    "(канал «Мелодия “По умолчанию”»)",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ────────────────── КНОПКА «ОТЛОЖИТЬ» (SNOOZE) ──────────────────
            SectionTitle("КНОПКА «ОТЛОЖИТЬ»")
            Text(
                text = "На сколько минут переносится сигнал, когда вы нажимаете " +
                        "«Отложить» на экране будильника или в уведомлении. " +
                        "Задача при этом остаётся активной и зазвонит снова.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            SettingsCard {
                SettingsStore.SNOOZE_OPTIONS.forEachIndexed { index, minutes ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                snoozeMinutes = minutes
                                SettingsStore.setSnoozeMinutes(context, minutes)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = snoozeMinutes == minutes,
                            onClick = null
                        )
                        Text(
                            text = "$minutes минут",
                            fontSize = 16.sp,
                            fontWeight = if (snoozeMinutes == minutes) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
