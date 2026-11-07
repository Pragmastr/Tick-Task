package todo

import (
        "database/sql"
        "path/filepath"
        "sync"
        "testing"

        _ "modernc.org/sqlite"
)

func newTestService(t *testing.T) *TodoService {
        t.Helper()
        dir := t.TempDir()
        s, err := NewTodoService(filepath.Join(dir, "test.db"))
        if err != nil {
                t.Fatalf("NewTodoService: %v", err)
        }
        t.Cleanup(func() { s.Close() })
        return s
}

func TestAddAndGetSorted(t *testing.T) {
        s := newTestService(t)

        id1, err := s.AddTaskAndGetID("Задача A", "05.01.2026", "10:00")
        if err != nil || id1 != 1 {
                t.Fatalf("add A: id=%d err=%v", id1, err)
        }
        if _, err := s.AddTaskAndGetID("Задача B", "2026-01-03", "09:00"); err != nil {
                t.Fatalf("add B: %v", err)
        }
        if _, err := s.AddTaskAndGetID("Задача C", "05.01.2026", "08:00"); err != nil {
                t.Fatalf("add C: %v", err)
        }

        json, err := s.GetAllTasksSortedJSON()
        if err != nil {
                t.Fatalf("all: %v", err)
        }
        want := `[{"id":2,"title":"Задача B","date":"03.01.2026","time":"09:00","status":0,"category":""},` +
                `{"id":3,"title":"Задача C","date":"05.01.2026","time":"08:00","status":0,"category":""},` +
                `{"id":1,"title":"Задача A","date":"05.01.2026","time":"10:00","status":0,"category":""}]`
        if json != want {
                t.Fatalf("sort mismatch:\n got: %s\nwant: %s", json, want)
        }
}

func TestAddWithCategory(t *testing.T) {
        s := newTestService(t)

        _, err := s.AddTaskWithCategoryAndGetID("Отчёт", "05.01.2026", "10:00", "  Работа ")
        if err != nil {
                t.Fatalf("add: %v", err)
        }
        json, err := s.GetAllTasksSortedJSON()
        if err != nil {
                t.Fatalf("all: %v", err)
        }
        // Категория нормализуется TrimSpace, дата yyyy-MM-dd -> дд.ММ.гггг
        want := `[{"id":1,"title":"Отчёт","date":"05.01.2026","time":"10:00","status":0,"category":"Работа"}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }
}

func TestUpdateTask(t *testing.T) {
        s := newTestService(t)
        id, _ := s.AddTaskWithCategoryAndGetID("Старое", "01.01.2026", "12:00", "Дом")

        // Полное редактирование: текст, дата (в обратном формате), время, категория.
        if err := s.UpdateTask(id, "Новое название", "2026-02-15", "09:30", "Работа"); err != nil {
                t.Fatalf("update: %v", err)
        }
        json, _ := s.GetAllTasksSortedJSON()
        want := `[{"id":1,"title":"Новое название","date":"15.02.2026","time":"09:30","status":0,"category":"Работа"}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }

        // Редактирование не трогает статус.
        if err := s.UpdateTaskStatus(id, 1); err != nil {
                t.Fatalf("status: %v", err)
        }
        if err := s.UpdateTask(id, "Новое 2", "01.03.2026", "08:00", ""); err != nil {
                t.Fatalf("update2: %v", err)
        }
        json, _ = s.GetAllTasksSortedJSON()
        want = `[{"id":1,"title":"Новое 2","date":"01.03.2026","time":"08:00","status":1,"category":""}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }
}

func TestUpdateTaskCategory(t *testing.T) {
        s := newTestService(t)
        id, _ := s.AddTaskAndGetID("Просто задача", "01.01.2026", "12:00")

        if err := s.UpdateTaskCategory(id, "Здоровье"); err != nil {
                t.Fatalf("cat: %v", err)
        }
        json, _ := s.GetAllTasksSortedJSON()
        want := `[{"id":1,"title":"Просто задача","date":"01.01.2026","time":"12:00","status":0,"category":"Здоровье"}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }
}

func TestUpdateAndDelete(t *testing.T) {
        s := newTestService(t)
        id, _ := s.AddTaskAndGetID("X", "01.01.2026", "12:00")

        if err := s.UpdateTaskStatus(id, 1); err != nil {
                t.Fatalf("update: %v", err)
        }
        if err := s.DeleteTask(id); err != nil {
                t.Fatalf("delete: %v", err)
        }
        if json, _ := s.GetAllTasksSortedJSON(); json != "[]" {
                t.Fatalf("expected empty, got %s", json)
        }
}

// Воспроизводит сценарий AlarmActivity: второе подключение к той же БД
// параллельно с основным сервисом ( MainActivity ).
func TestConcurrentConnections(t *testing.T) {
        dir := t.TempDir()
        dbPath := filepath.Join(dir, "shared.db")

        main, err := NewTodoService(dbPath)
        if err != nil {
                t.Fatalf("main service: %v", err)
        }
        defer main.Close()

        id, err := main.AddTaskAndGetID("Параллельная запись", "01.01.2026", "12:00")
        if err != nil {
                t.Fatalf("seed: %v", err)
        }

        var wg sync.WaitGroup
        errs := make(chan error, 20)
        for i := 0; i < 10; i++ {
                wg.Add(1)
                go func() {
                        defer wg.Done()
                        second, err := NewTodoService(dbPath) // как в AlarmActivity
                        if err != nil {
                                errs <- err
                                return
                        }
                        defer second.Close()
                        if err := second.UpdateTaskStatus(id, 0); err != nil {
                                errs <- err
                        }
                }()
        }
        wg.Wait()
        close(errs)
        for err := range errs {
                t.Errorf("concurrent write: %v", err)
        }
}

// Старая база (схема до появления категорий) должна бесшовно мигрировать:
// колонка category достраивается, данные остаются нетронутыми.
func TestMigrationFromLegacySchema(t *testing.T) {
        dir := t.TempDir()
        dbPath := filepath.Join(dir, "legacy.db")

        legacy, err := sql.Open("sqlite", "file:"+dbPath)
        if err != nil {
                t.Fatalf("open legacy: %v", err)
        }
        _, err = legacy.Exec(`CREATE TABLE IF NOT EXISTS "tasks" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "title" TEXT NOT NULL,
                "date" TEXT NOT NULL,
                "time" TEXT NOT NULL,
                "status" INTEGER NOT NULL
        );`)
        if err != nil {
                t.Fatalf("create legacy: %v", err)
        }
        if _, err := legacy.Exec(
                `INSERT INTO tasks (title, date, time, status) VALUES ('Старая задача', '01.01.2026', '07:00', 0)`,
        ); err != nil {
                t.Fatalf("seed legacy: %v", err)
        }
        legacy.Close()

        s, err := NewTodoService(dbPath)
        if err != nil {
                t.Fatalf("migrated service: %v", err)
        }
        defer s.Close()

        json, err := s.GetAllTasksSortedJSON()
        if err != nil {
                t.Fatalf("all: %v", err)
        }
        want := `[{"id":1,"title":"Старая задача","date":"01.01.2026","time":"07:00","status":0,"category":""}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }

        // После миграции редактирование тоже работает.
        if err := s.UpdateTask(1, "Изменено", "02.01.2026", "07:30", "Покупки"); err != nil {
                t.Fatalf("update after migration: %v", err)
        }
}

// Задачи, запланированные на годы вперёд, обязаны переживать все операции
// и корректно сортироваться: ядро хранит напоминания бессрочно.
func TestFarFuturePersistence(t *testing.T) {
        s := newTestService(t)

        farFuture := "07.11.2036" // через 10 лет
        id, err := s.AddTaskWithCategoryAndGetID("Проверка через 10 лет", farFuture, "06:30", "Семья")
        if err != nil {
                t.Fatalf("add far future: %v", err)
        }

        // Перезапись статуса туда-обратно (как при свайпе будильника) не должна терять данные.
        if err := s.UpdateTaskStatus(id, 1); err != nil {
                t.Fatalf("status on: %v", err)
        }
        if err := s.UpdateTaskStatus(id, 0); err != nil {
                t.Fatalf("status off: %v", err)
        }
        if err := s.UpdateTask(id, "Проверка через 10 лет (правка)", farFuture, "06:45", "Семья"); err != nil {
                t.Fatalf("update: %v", err)
        }

        json, _ := s.GetAllTasksSortedJSON()
        want := `[{"id":1,"title":"Проверка через 10 лет (правка)","date":"07.11.2036","time":"06:45","status":0,"category":"Семья"}]`
        if json != want {
                t.Fatalf("mismatch:\n got: %s\nwant: %s", json, want)
        }
}
