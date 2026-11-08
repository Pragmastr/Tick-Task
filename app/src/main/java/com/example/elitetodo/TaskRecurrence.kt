package com.example.elitetodo

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Правило повтора задачи.
 *
 * Хранится на стороне Kotlin (RecurrenceStore, SharedPreferences по id задачи),
 * поэтому Go-ядро и его AAR не требуют пересборки. В базе задача всегда
 * указывает на БЛИЖАЙШИЙ слот: когда будильник отзвенел и пользователь его
 * выключил, MainActivity вычисляет следующий слот через [nextOccurrence],
 * переписывает дату/время в ядре и перевзводит системный будильник — задача
 * остаётся активной (status = 0) и бесконечно возрождается по расписанию.
 *
 * Поддерживаемые периоды:
 *  - DAILY    — каждый день в то же время;
 *  - WEEKLY   — по выбранным дням недели (Calendar.DAY_OF_WEEK);
 *  - MONTHLY  — ежемесячно в указанное число (31-е в коротком месяце
 *               переносится на последний день);
 *  - INTERVAL — каждые N минут / часов / дней от исходного времени.
 */
data class TaskRecurrence(
    val type: Type = Type.NONE,
    val intervalCount: Int = 1,
    val intervalUnit: IntervalUnit = IntervalUnit.MINUTES,
    /** Дни недели для WEEKLY: константы Calendar.DAY_OF_WEEK (1=вс ... 7=сб). */
    val weekdays: Set<Int> = emptySet(),
    /** Число месяца для MONTHLY: 1..31. */
    val monthDay: Int = 1
) {
    enum class Type { NONE, DAILY, WEEKLY, MONTHLY, INTERVAL }

    enum class IntervalUnit { MINUTES, HOURS, DAYS }

    /** Есть ли у задачи повтор вообще. */
    val isRepeating: Boolean get() = type != Type.NONE

    /** Сериализация в строку для RecurrenceStore («WEEKLY:2,4,6» и т.п.). */
    fun encode(): String = when (type) {
        Type.NONE -> "NONE"
        Type.DAILY -> "DAILY"
        Type.WEEKLY -> "WEEKLY:" + weekdays.sorted().joinToString(",")
        Type.MONTHLY -> "MONTHLY:$monthDay"
        Type.INTERVAL -> "INTERVAL:$intervalCount:${intervalUnit.name}"
    }

    /** Человекочитаемое описание правила для карточки задачи и диалога. */
    fun summary(): String = when (type) {
        Type.NONE -> ""
        Type.DAILY -> "Каждый день"
        Type.WEEKLY -> {
            val labels = WEEKDAY_ORDER.filter { it in weekdays }.map { weekdayLabel(it) }
            "По дням: " + labels.joinToString(", ")
        }
        Type.MONTHLY -> "Ежемесячно: $monthDay-го числа"
        Type.INTERVAL -> when {
            intervalCount == 1 && intervalUnit == IntervalUnit.MINUTES -> "Каждую минуту"
            intervalCount == 1 && intervalUnit == IntervalUnit.HOURS -> "Каждый час"
            intervalCount == 1 && intervalUnit == IntervalUnit.DAYS -> "Каждый день"
            else -> "Каждые $intervalCount " + when (intervalUnit) {
                IntervalUnit.MINUTES -> plural(intervalCount, "минуту", "минуты", "минут")
                IntervalUnit.HOURS -> plural(intervalCount, "час", "часа", "часов")
                IntervalUnit.DAYS -> plural(intervalCount, "день", "дня", "дней")
            }
        }
    }

    /**
     * Чинит «полуфабрикаты» из редактора: пустые дни недели, нулевые интервалы,
     * число месяца вне 1..31. Невалидное правило схлопывается в NONE.
     */
    fun normalized(): TaskRecurrence = when (type) {
        Type.WEEKLY ->
            if (weekdays.isEmpty()) TaskRecurrence()
            else copy(weekdays = weekdays.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet())
        Type.MONTHLY -> copy(monthDay = monthDay.coerceIn(1, 31))
        Type.INTERVAL ->
            if (intervalCount < 1) TaskRecurrence()
            else copy(intervalCount = intervalCount.coerceAtMost(365))
        else -> this
    }

    /**
     * Следующее срабатывание строго в будущем (с запасом 30 секунд, чтобы
     * AlarmManager не выстрелил мгновенно по уже отзвеневшему слоту).
     * Возвращает пару (дата «dd.MM.yyyy», время «HH:mm») или null, если
     * правило пустое/некорректное/не удалось разобрать дату.
     */
    fun nextOccurrence(
        dateStr: String,
        timeStr: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<String, String>? {
        val base = parseDateTime(dateStr, timeStr) ?: return null
        val threshold = nowMillis + 30_000L

        val cal = Calendar.getInstance()
        cal.timeInMillis = base
        // Опорные час и минута исходного слота: день/месяц едем, время — нет.
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        when (type) {
            Type.NONE -> return null

            Type.DAILY -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    setTimeOfDay(cal, hour, minute)
                } while (cal.timeInMillis <= threshold)
            }

            Type.WEEKLY -> {
                if (weekdays.isEmpty()) return null
                var guard = 0
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    setTimeOfDay(cal, hour, minute)
                    guard++
                    if (guard > 3660) return null // страховка: ~10 лет назад — не наш слот
                } while (!(cal.timeInMillis > threshold && cal.get(Calendar.DAY_OF_WEEK) in weekdays))
            }

            Type.MONTHLY -> {
                var guard = 0
                do {
                    cal.add(Calendar.MONTH, 1)
                    // 31-е в коротком месяце ужимается до последнего дня.
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(monthDay.coerceIn(1, 31), maxDay))
                    setTimeOfDay(cal, hour, minute)
                    guard++
                    if (guard > 600) return null // страховка: 50 лет вперёд
                } while (cal.timeInMillis <= threshold)
            }

            Type.INTERVAL -> {
                val stepMillis = intervalCount.coerceAtLeast(1) * when (intervalUnit) {
                    IntervalUnit.MINUTES -> 60_000L
                    IntervalUnit.HOURS -> 3_600_000L
                    IntervalUnit.DAYS -> 86_400_000L
                }
                // Шаги считаем умножением, а не циклом: интервал в 5 минут от
                // задачи годичной давности не должен крутить миллион итераций.
                val steps = if (threshold <= base) 1 else (threshold - base) / stepMillis + 1
                var next = base + steps * stepMillis
                if (next <= threshold) next += stepMillis
                cal.timeInMillis = next
            }
        }

        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return Pair(dateFmt.format(cal.time), timeFmt.format(cal.time))
    }

    private fun setTimeOfDay(cal: Calendar, hour: Int, minute: Int) {
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    companion object {
        /** Порядок дней недели для отображения по-русски: Пн..Вс. */
        val WEEKDAY_ORDER = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )

        fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.MONDAY -> "Пн"
            Calendar.TUESDAY -> "Вт"
            Calendar.WEDNESDAY -> "Ср"
            Calendar.THURSDAY -> "Чт"
            Calendar.FRIDAY -> "Пт"
            Calendar.SATURDAY -> "Сб"
            else -> "Вс"
        }

        fun unitLabel(unit: IntervalUnit): String = when (unit) {
            IntervalUnit.MINUTES -> "минут"
            IntervalUnit.HOURS -> "часов"
            IntervalUnit.DAYS -> "дней"
        }

        /** Обратная операция к [encode]; мусор на входе даёт правило NONE. */
        fun decode(encoded: String?): TaskRecurrence {
            if (encoded.isNullOrBlank()) return TaskRecurrence()
            return try {
                when {
                    encoded == "DAILY" -> TaskRecurrence(type = Type.DAILY)

                    encoded.startsWith("WEEKLY:") -> TaskRecurrence(
                        type = Type.WEEKLY,
                        weekdays = encoded.removePrefix("WEEKLY:")
                            .split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .toSet()
                    )

                    encoded.startsWith("MONTHLY:") -> TaskRecurrence(
                        type = Type.MONTHLY,
                        monthDay = encoded.removePrefix("MONTHLY:").trim().toIntOrNull() ?: 1
                    )

                    encoded.startsWith("INTERVAL:") -> {
                        val parts = encoded.removePrefix("INTERVAL:").split(":")
                        val count = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                        val unit = parts.getOrNull(1)?.trim()
                            ?.let { name -> IntervalUnit.values().firstOrNull { it.name == name } }
                            ?: IntervalUnit.MINUTES
                        TaskRecurrence(
                            type = Type.INTERVAL,
                            intervalCount = count,
                            intervalUnit = unit
                        )
                    }

                    else -> TaskRecurrence()
                }
            } catch (_: Exception) {
                TaskRecurrence()
            }
        }

        private fun parseDateTime(dateStr: String, timeStr: String): Long? = try {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse("$dateStr $timeStr")?.time
        } catch (_: Exception) {
            null
        }

        /** Русское склонение: plural(5, "минуту", "минуты", "минут") = «минут». */
        private fun plural(count: Int, one: String, few: String, many: String): String {
            val n = count % 100
            if (n in 11..14) return many
            return when (count % 10) {
                1 -> one
                in 2..4 -> few
                else -> many
            }
        }
    }
}
