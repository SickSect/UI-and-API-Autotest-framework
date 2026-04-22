package org.ugina.ApiClient.Db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent-билдер для UPDATE-запросов.
 *
 * Пример:
 *   dbClient.update("users")
 *           .set("status", "inactive")
 *           .set("updated_at", "2026-04-19")
 *           .where("last_login", "<", "2025-01-01")
 *           .execute();
 *
 * Генерирует:
 *   UPDATE users SET status = ?, updated_at = ? WHERE last_login < ?
 *   params: ["inactive", "2026-04-19", "2025-01-01"]
 */
public class UpdateBuilder {

    private final IDbClient dbClient;
    private final String table;
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> conditionParams = new ArrayList<>();

    public UpdateBuilder(IDbClient dbClient, String table) {
        this.dbClient = dbClient;
        this.table = table;
    }

    /**
     * Добавляет пару колонка-значение для обновления.
     */
    public UpdateBuilder set(String column, Object value) {
        setValues.put(column, value);
        return this;
    }

    /**
     * Условие WHERE с оператором "=".
     */
    public UpdateBuilder where(String column, Object value) {
        conditions.add(column + " = ?");
        conditionParams.add(value);
        return this;
    }

    /**
     * Условие WHERE с произвольным оператором.
     */
    public UpdateBuilder where(String column, String operator, Object value) {
        conditions.add(column + " " + operator + " ?");
        conditionParams.add(value);
        return this;
    }

    /**
     * Выполняет UPDATE. Возвращает количество обновлённых строк.
     */
    public int execute() {
        // Собираем все параметры: сначала SET-значения, потом WHERE-значения
        List<Object> allParams = new ArrayList<>(setValues.values());
        allParams.addAll(conditionParams);
        return dbClient.execute(buildSql(), allParams.toArray());
    }

    /**
     * Возвращает собранный SQL.
     */
    public String buildSql() {
        // SET часть: column1 = ?, column2 = ?
        List<String> setClauses = new ArrayList<>();
        for (String column : setValues.keySet()) {
            setClauses.add(column + " = ?");
        }

        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(table)
                .append(" SET ")
                .append(String.join(", ", setClauses));

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        return sql.toString();
    }
}