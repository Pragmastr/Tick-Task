package com.example.elitetodo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.elitetodo.SettingsStore

private val DarkColorScheme = darkColorScheme(
    primary = MainBlue,
    secondary = LightBlue,
    background = Color(0xFF121212),   // Глубокий темный фон
    surface = Color(0xFF1E1E1E),      // Темные карточки задач
    surfaceVariant = Color(0xFF2A2A2A),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB8B8B8)
)

private val LightColorScheme = lightColorScheme(
    primary = MainBlue,
    secondary = LightBlue,
    background = AppBackground,
    surface = CardSurface,
    surfaceVariant = Color(0xFFF0F0F0),
    onPrimary = Color.White,       // Текст внутри голубых кнопок будет белым
    onBackground = TextBlack,      // Текст задач будет черным
    onSurface = TextBlack,         // Текст в шапке будет черным
    onSurfaceVariant = TextGray    // Второстепенный текст: даты, подписи
)

/**
 * Тема приложения с выбором режима из настроек:
 * THEME_SYSTEM — как в системе, THEME_LIGHT — всегда светлая,
 * THEME_DARK — всегда тёмная.
 */
@Composable
fun EliteToDoTheme(
    themeMode: Int = SettingsStore.THEME_SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
