package com.example.elitetodo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * текст, дата, время и категория подставляются в поля автоматически.
 * Категория — произвольный текст; чипсы ниже поля подсказывают уже
 * использованные категории, чтобы не плодить дубли («Работа» / «работа»).
 */
@Composable
fun TaskEditorDialog(
    title: String,
    initialText: String,
    initialDate: String,
    initialTime: String,
    initialCategory: String,
    knownCategories: List<String>,
    confirmLabel: String,
    onSave: (text: String, date: String, time: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var taskText by remember { mutableStateOf(initialText) }
    var tempDate by remember { mutableStateOf(initialDate) }
    var tempTime by remember { mutableStateOf(initialTime) }
    var category by remember { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text(
                    text = "Запланировано на: $tempDate в $tempTime",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (taskText.trim().isNotEmpty()) {
                    onSave(taskText.trim(), tempDate, tempTime, category.trim())
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
