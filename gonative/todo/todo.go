package todo

import (
        "database/sql"
        "encoding/json"
        "fmt"
        "strings"

        _ "modernc.org/sqlite" // Pure Go драйвер без CGO, идеален для Android
)

// TaskEntity полностью дублирует структуру данных для UI.
// Category — произвольная текстовая метка ("Работа", "Дом", ...).
// Пустая строка означает "Без категории". Хранится в ядре бессрочно,
// как и сама задача: ядро ничего не удаляет и не "забывает".
type TaskEntity struct {
        ID       int    `json:"id"`
        Title    string `json:"title"`
        Date     string `json:"date"`
        Time     string `json:"time"`
        Status   int    `json:"status"`
        Category string `json:"category"`
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
        // Свежая схема сразу содержит колонку category.
        query := `
        CREATE TABLE IF NOT EXISTS "tasks" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "title" TEXT NOT NULL,
                "date" TEXT NOT NULL,
                "time" TEXT NOT NULL,
                "status" INTEGER NOT NULL,
                "category" TEXT NOT NULL DEFAULT ''
        );`
        if _, err := s.db.Exec(query); err != nil {
                return err
        }
        // Базы, созданные прошлыми версиями приложения, продолжают жить дальше:
        // миграция достраивает недостающие колонки, данные не теряются никогда.
        return s.migrate()
}

// migrate идемпотентно достраивает схему до актуальной.
func (s *TodoService) migrate() error {
        rows, err := s.db.Query(`SELECT name FROM pragma_table_info('tasks')`)
        if err != nil {
                return err
        }
        existing := map[string]bool{}
        for rows.Next() {
                var name string
                if err := rows.Scan(&name); err != nil {
                        rows.Close()
                        return err
                }
                existing[name] = true
        }
        rows.Close()
        if err := rows.Err(); err != nil {
                return err
        }

        if !existing["category"] {
                if _, err := s.db.Exec(`ALTER TABLE "tasks" ADD COLUMN "category" TEXT NOT NULL DEFAULT ''`); err != nil {
                        return err
                }
        }
        return nil
}

// GetAllTasksSortedJSON возвращает отсортированный JSON-список задач.
func (s *TodoService) GetAllTasksSortedJSON() (string, error) {
        query := `
                SELECT id, title, date, time, status, category FROM tasks
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
                if err := rows.Scan(&t.ID, &t.Title, &t.Date, &t.Time, &t.Status, &t.Category); err != nil {
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

// normalizeDate приводит дату к каноничному формату дд.ММ.гггг.
func normalizeDate(dateStr string) string {
        if strings.Contains(dateStr, "-") {
                parts := strings.Split(dateStr, "-")
                if len(parts) == 3 {
                        return fmt.Sprintf("%s.%s.%s", parts[2], parts[1], parts[0])
                }
        }
        return dateStr
}

// AddTaskAndGetID сохраняет новую задачу без категории, возвращая сгенерированный ID.
// Оставлен для совместимости со старыми вызовами.
func (s *TodoService) AddTaskAndGetID(title string, dateStr string, timeStr string) (int, error) {
        return s.AddTaskWithCategoryAndGetID(title, dateStr, timeStr, "")
}

// AddTaskWithCategoryAndGetID нормализует дату и сохраняет новую задачу
// вместе с категорией, возвращая сгенерированный ID.
func (s *TodoService) AddTaskWithCategoryAndGetID(title string, dateStr string, timeStr string, category string) (int, error) {
        dateStr = normalizeDate(dateStr)

        query := "INSERT INTO tasks (title, date, time, status, category) VALUES (?, ?, ?, 0, ?)"
        res, err := s.db.Exec(query, title, dateStr, timeStr, strings.TrimSpace(category))
        if err != nil {
                return 0, err
        }

        id, err := res.LastInsertId()
        if err != nil {
                return 0, err
        }

        return int(id), nil
}

// UpdateTask полностью переписывает содержимое задачи (функция редактирования).
// Дата нормализуется так же, как при создании. Статус и ID не трогаются.
func (s *TodoService) UpdateTask(id int, title string, dateStr string, timeStr string, category string) error {
        dateStr = normalizeDate(dateStr)

        query := "UPDATE tasks SET title = ?, date = ?, time = ?, category = ? WHERE id = ?"
        _, err := s.db.Exec(query, title, dateStr, timeStr, strings.TrimSpace(category), id)
        return err
}

// UpdateTaskCategory меняет только категорию задачи.
func (s *TodoService) UpdateTaskCategory(id int, category string) error {
        query := "UPDATE tasks SET category = ? WHERE id = ?"
        _, err := s.db.Exec(query, strings.TrimSpace(category), id)
        return err
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
