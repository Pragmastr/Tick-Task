package com.example.elitetodo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Единый диалог создания и редактирования задачи.
 *
 * Для редактирования в него просто передаётся текущее содержимое задачи:
 * текст, дата, время, категория и правило повтора подставляются в поля
 * автоматически. Категория — произвольный текст; чипсы ниже поля подсказывают
 * уже использованные категории, чтобы не плодить дубли («Работа» / «работа»).
 *
 * Блок «Повтор»: Нет / Каждый день / Дни недели / Ежемесячно / Интервал
 * (каждые N минут, часов или дней). Повторяющаяся задача при выключении
 * будильника не выполняется, а сама переносится на следующий слот —
 * правило хранится в RecurrenceStore по id задачи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    title: String,
    initialText: String,
    initialDate: String,
    initialTime: String,
    initialCategory: String,
    initialRecurrence: TaskRecurrence,
    knownCategories: List<String>,
    confirmLabel: String,
    onSave: (text: String, date: String, time: String, category: String, recurrence: TaskRecurrence) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var taskText by remember { mutableStateOf(initialText) }
    var tempDate by remember { mutableStateOf(initialDate) }
    var tempTime by remember { mutableStateOf(initialTime) }
    var category by remember { mutableStateOf(initialCategory) }
    var recurrence by remember { mutableStateOf(initialRecurrence.normalized()) }
    // Текстовые поля чисел: число месяца и длина интервала.
    var monthDayText by remember { mutableStateOf(initialRecurrence.monthDay.toString()) }
    var intervalText by remember { mutableStateOf(initialRecurrence.intervalCount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    label = { Text("Что планируем?") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Категория (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (knownCategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        knownCategories.forEach { cat ->
                            AssistChip(
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 13.sp) }
                            )
                        }
                    }
                }

                // ───────────────────────── ПОВТОР ─────────────────────────
                Text(
                    text = "ПОВТОР",
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = recurrence.type == TaskRecurrence.Type.NONE,
                        onClick = { recurrence = TaskRecurrence() },
                        label = { Text("Нет", fontSize = 13.sp) }
                    )
                    FilterChip(
                        selected = recurrence.type == TaskRecurrence.Type.DAILY,
                        onClick = { recurrence = TaskRecurrence(type = TaskRecurrence.Type.DAILY) },
                        label = { Text("Каждый день", fontSize = 13.sp) }
                    )
                    FilterChip(
                        selected = recurrence.type == TaskRecurrence.Type.WEEKLY,
                        onClick = {
                            recurrence = TaskRecurrence(
                                type = TaskRecurrence.Type.WEEKLY,
                                weekdays = recurrence.weekdays.ifEmpty { setOf(Calendar.MONDAY) }
                            )
                        },
                        label = { Text("Дни недели", fontSize = 13.sp) }
                    )
                    FilterChip(
                        selected = recurrence.type == TaskRecurrence.Type.MONTHLY,
                        onClick = {
                            recurrence = TaskRecurrence(
                                type = TaskRecurrence.Type.MONTHLY,
                                monthDay = recurrence.monthDay
                            )
                        },
                        label = { Text("Ежемесячно", fontSize = 13.sp) }
                    )
                    FilterChip(
                        selected = recurrence.type == TaskRecurrence.Type.INTERVAL,
                        onClick = {
                            recurrence = TaskRecurrence(
                                type = TaskRecurrence.Type.INTERVAL,
                                intervalCount = recurrence.intervalCount,
                                intervalUnit = recurrence.intervalUnit
                            )
                        },
                        label = { Text("Интервал", fontSize = 13.sp) }
                    )
                }

                when (recurrence.type) {
                    TaskRecurrence.Type.WEEKLY -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TaskRecurrence.WEEKDAY_ORDER.forEach { dayOfWeek ->
                                val selected = dayOfWeek in recurrence.weekdays
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val updated = recurrence.weekdays.toMutableSet()
                                        if (selected) updated.remove(dayOfWeek) else updated.add(dayOfWeek)
                                        recurrence = recurrence.copy(weekdays = updated)
                                    },
                                    label = { Text(TaskRecurrence.weekdayLabel(dayOfWeek), fontSize = 13.sp) }
                                )
                            }
                        }
                    }

                    TaskRecurrence.Type.MONTHLY -> {
                        OutlinedTextField(
                            value = monthDayText,
                            onValueChange = { monthDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Число месяца (1–31)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Если в месяце нет такого числа, сигнал придёт в последний день месяца.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TaskRecurrence.Type.INTERVAL -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { intervalText = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text("Каждые") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TaskRecurrence.IntervalUnit.values().forEach { unit ->
                                FilterChip(
                                    selected = recurrence.intervalUnit == unit,
                                    onClick = { recurrence = recurrence.copy(intervalUnit = unit) },
                                    label = { Text(TaskRecurrence.unitLabel(unit), fontSize = 13.sp) }
                                )
                            }
                        }
                    }

                    TaskRecurrence.Type.NONE -> {}
                }

                val repeatHint = if (recurrence.isRepeating) {
                    " • " + recurrence.normalized().summary()
                } else ""
                Text(
                    text = "Запланировано на: $tempDate в $tempTime$repeatHint",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (taskText.trim().isNotEmpty()) {
                    // Чиним «полуфабрикаты»: пустые дни недели и нулевые
                    // интервалы схлопываются в «Без повтора».
                    var toSave = recurrence
                    if (toSave.type == TaskRecurrence.Type.MONTHLY) {
                        toSave = toSave.copy(monthDay = monthDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                    }
                    if (toSave.type == TaskRecurrence.Type.INTERVAL) {
                        val count = intervalText.toIntOrNull() ?: 0
                        toSave = if (count >= 1) toSave.copy(intervalCount = count) else TaskRecurrence()
                    }
                    onSave(taskText.trim(), tempDate, tempTime, category.trim(), toSave.normalized())
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val current = try {
                        Calendar.getInstance().apply {
                            time = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(tempDate)!!
                        }
                    } catch (_: Exception) {
                        Calendar.getInstance()
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }
                            tempDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(cal.time)
                        },
                        current.get(Calendar.YEAR),
                        current.get(Calendar.MONTH),
                        current.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text("ДАТА")
                }

                TextButton(onClick = {
                    val current = try {
                        Calendar.getInstance().apply {
                            time = SimpleDateFormat("HH:mm", Locale.getDefault()).parse(tempTime)!!
                        }
                    } catch (_: Exception) {
                        Calendar.getInstance()
                    }
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                set(Calendar.MINUTE, minute)
                            }
                            tempTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                        },
                        current.get(Calendar.HOUR_OF_DAY),
                        current.get(Calendar.MINUTE),
                        true
                    ).show()
                }) {
                    Text("ВРЕМЯ")
                }

                TextButton(onClick = onDismiss) {
                    Text("ОТМЕНА")
                }
            }
        }
    )
}
