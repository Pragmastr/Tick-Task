package com.example.elitetodo

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Полная высота свайпа вверх (в пикселях), после которого будильник выключается. */
private const val SWIPE_DISMISS_THRESHOLD = 160f

@Composable
fun AlarmScreen(
    taskTitle: String,
    snoozeMinutes: Int,
    onSnooze: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // Бесконечная плавная анимация стрелки вверх-вниз
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    // Накопитель вертикального смещения: свайп состоит из множества мелких
    // событий, порог по одному событию (dragAmount.y < -50) почти недостижим.
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = {
                        if (accumulatedDrag < -SWIPE_DISMISS_THRESHOLD) onDismiss()
                    },
                    onDragCancel = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount.y
                    if (accumulatedDrag < -SWIPE_DISMISS_THRESHOLD) onDismiss()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. ВЕРХНИЙ БЛОК: Иконка приложения (Через системный ImageView без крашей)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { context ->
                    android.widget.ImageView(context).apply {
                        setImageResource(R.mipmap.ic_launcher_round)
                        clipToOutline = true
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                        }
                    }
                },
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "EliteTodo",
                color = Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 2. ЦЕНТРАЛЬНЫЙ БЛОК: Название задачи
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⏰ НАСТАЛО ВРЕМЯ ЗАДАЧИ:",
                color = Color(0xFFFFB74D),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = taskTitle,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
        }

        // 3. КНОПКА «ОТЛОЖИТЬ»: переносит сигнал на snoozeMinutes минут.
        // Задача остаётся активной; свайп вверх по-прежнему выключает будильник.
        if (onSnooze != null) {
            OutlinedButton(
                onClick = onSnooze,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 150.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = "ОТЛОЖИТЬ НА $snoozeMinutes МИН",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // 4. НИЖНИЙ БЛОК: Анимированная стрелка и текст свайпа
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.offset(y = arrowOffset.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Swipe Up",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Смахните вверх, чтобы выключить",
                color = Color.LightGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp
            )
        }
    }
}
