package org.ugina.ApiClient.Db;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent-билдер для DELETE-запросов.
 *
 * Пример:
 *   dbClient.delete("users")
 *           .where("status", "deleted")
 *           .where("last_login", "<", "2024-01-01")
 *           .execute();
 *
 * Генерирует:
 *   DELETE FROM users WHERE status = ? AND last_login < ?
 *   params: ["deleted", "2024-01-01"]
 *
 * ВАЖНО: .execute() без .where() удалит ВСЕ строки из таблицы.
 * Это сделано осознанно — в тестах иногда нужен TRUNCATE-like функционал.
 * Но в логах будет предупреждение.
 */
public class DeleteBuilder {

    private final IDbClient dbClient;
    private final String table;
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();

    public DeleteBuilder(IDbClient dbClient, String table) {
        this.dbClient = dbClient;
        this.table = table;
    }

    /**
     * Условие WHERE с оператором "=".
     */
    public DeleteBuilder where(String column, Object value) {
        conditions.add(column + " = ?");
        params.add(value);
        return this;
    }

    /**
     * Условие WHERE с произвольным оператором.
     */
    public DeleteBuilder where(String column, String operator, Object value) {
        conditions.add(column + " " + operator + " ?");
        params.add(value);
        return this;
    }

    /**
     * Выполняет DELETE. Возвращает количество удалённых строк.
     */
    public int execute() {
        return dbClient.execute(buildSql(), params.toArray());
    }

    /**
     * Возвращает собранный SQL.
     */
    public String buildSql() {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        return sql.toString();
    }
}
