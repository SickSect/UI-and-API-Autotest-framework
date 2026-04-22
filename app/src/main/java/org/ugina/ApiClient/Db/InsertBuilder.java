package org.ugina.ApiClient.Db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent-билдер для INSERT-запросов.
 *
 * Пример:
 *   dbClient.insert("users")
 *           .set("name", "John")
 *           .set("email", "john@example.com")
 *           .set("age", 30)
 *           .execute();
 *
 * Генерирует:
 *   INSERT INTO users (name, email, age) VALUES (?, ?, ?)
 *   params: ["John", "john@example.com", 30]
 */
public class InsertBuilder {

    private final IDbClient dbClient;
    private final String table;
    // LinkedHashMap сохраняет порядок вставки — колонки будут в том порядке,
    // в котором вызывались .set()
    private final Map<String, Object> values = new LinkedHashMap<>();

    public InsertBuilder(IDbClient dbClient, String table) {
        this.dbClient = dbClient;
        this.table = table;
    }

    /**
     * Добавляет пару колонка-значение.
     *
     * .set("name", "John")  → колонка name, значение "John"
     * .set("age", 30)       → колонка age, значение 30
     * .set("active", true)  → колонка active, значение true
     */
    public InsertBuilder set(String column, Object value) {
        values.put(column, value);
        return this;
    }

    /**
     * Выполняет INSERT. Возвращает количество вставленных строк (обычно 1).
     */
    public int execute() {
        return dbClient.execute(buildSql(), values.values().toArray());
    }

    /**
     * Возвращает собранный SQL (для отладки).
     */
    public String buildSql() {
        List<String> columns = new ArrayList<>(values.keySet());
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            placeholders.add("?");
        }

        return "INSERT INTO " + table
                + " (" + String.join(", ", columns) + ")"
                + " VALUES (" + String.join(", ", placeholders) + ")";
    }
}