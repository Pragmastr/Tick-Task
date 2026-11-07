package todo

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"strings"

	_ "modernc.org/sqlite" // Pure Go драйвер без CGO, идеален для Android
)

// TaskEntity полностью дублирует структуру данных для UI
type TaskEntity struct {
	ID     int    `json:"id"`
	Title  string `json:"title"`
	Date   string `json:"date"`
	Time   string `json:"time"`
	Status int    `json:"status"`
}

// TodoService — главный компонент бизнес-логики приложения
type TodoService struct {
	db *sql.DB
}

// NewTodoService открывает базу данных и готовит таблицы. Прогрев происходит атомарно.
func NewTodoService(dbPath string) (*TodoService, error) {
	// busy_timeout заставляет ждущие соединения дожидаться снятия блокировки
	// вместо мгновенного SQLITE_BUSY; WAL разрешает чтение во время записи.
	dsn := "file:" + dbPath + "?_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// Сериализуем доступ внутри процесса: один писатель — нет взаимных блокировок.
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	service := &TodoService{db: db}
	if err := service.createTables(); err != nil {
		db.Close()
		return nil, err
	}

	return service, nil
}

func (s *TodoService) createTables() error {
	query := `
	CREATE TABLE IF NOT EXISTS "tasks" (
		"id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
		"title" TEXT NOT NULL,
		"date" TEXT NOT NULL,
		"time" TEXT NOT NULL,
		"status" INTEGER NOT NULL
	);`
	_, err := s.db.Exec(query)
	return err
}

// GetAllTasksSortedJSON возвращает отсортированный JSON-список задач.
func (s *TodoService) GetAllTasksSortedJSON() (string, error) {
	query := `
		SELECT id, title, date, time, status FROM tasks
		ORDER BY status ASC,
		SUBSTR(date, 7, 4) || '-' || SUBSTR(date, 4, 2) || '-' || SUBSTR(date, 1, 2) ASC,
		time ASC
	`
	rows, err := s.db.Query(query)
	if err != nil {
		return "", err
	}
	defer rows.Close()

	tasks := []TaskEntity{}
	for rows.Next() {
		var t TaskEntity
		if err := rows.Scan(&t.ID, &t.Title, &t.Date, &t.Time, &t.Status); err != nil {
			return "", err
		}
		tasks = append(tasks, t)
	}

	if err := rows.Err(); err != nil {
		return "", err
	}

	if len(tasks) == 0 {
		return "[]", nil
	}

	jsonData, err := json.Marshal(tasks)
	if err != nil {
		return "", err
	}
	return string(jsonData), nil
}

// AddTaskAndGetID нормализует дату и сохраняет новую задачу, возвращая сгенерированный ID
func (s *TodoService) AddTaskAndGetID(title string, dateStr string, timeStr string) (int, error) {
	if strings.Contains(dateStr, "-") {
		parts := strings.Split(dateStr, "-")
		if len(parts) == 3 {
			dateStr = fmt.Sprintf("%s.%s.%s", parts[2], parts[1], parts[0])
		}
	}

	query := "INSERT INTO tasks (title, date, time, status) VALUES (?, ?, ?, 0)"
	res, err := s.db.Exec(query, title, dateStr, timeStr)
	if err != nil {
		return 0, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return 0, err
	}

	return int(id), nil
}

// UpdateTaskStatus обновляет статус выполнения задачи
func (s *TodoService) UpdateTaskStatus(id int, status int) error {
	query := "UPDATE tasks SET status = ? WHERE id = ?"
	_, err := s.db.Exec(query, status, id)
	return err
}

// DeleteTask полностью удаляет задачу из базы
func (s *TodoService) DeleteTask(id int) error {
	query := "DELETE FROM tasks WHERE id = ?"
	_, err := s.db.Exec(query, id)
	return err
}

// Close безопасно закрывает дескриптор базы данных
func (s *TodoService) Close() error {
	return s.db.Close()
}
