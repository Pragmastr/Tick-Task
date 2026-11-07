package com.example.elitetodo

data class TaskEntity(
    val id: Int = 0,
    val title: String,
    val date: String,
    val time: String,
    val status: Int = 0,
    // Категория хранится прямо в ядре (колонка category в SQLite).
    // Пустая строка = «Без категории».
    val category: String = ""
)
