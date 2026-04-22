package org.ugina.ApiClient.Db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Fluent-билдер для SELECT-запросов.
 *
 * Вместо ручного написания SQL:
 *   "SELECT id, name FROM users WHERE status = 'active' AND age > 25 ORDER BY name LIMIT 10"
 *
 * Пишем:
 *   dbClient.select("users")
 *           .columns("id", "name")
 *           .where("status", "active")
 *           .where("age", ">", 25)
 *           .orderBy("name")
 *           .limit(10)
 *           .execute();
 *
 * Билдер не выполняет запрос сам — он собирает SQL-строку и параметры,
 * а потом передаёт их в DbClient.query().
 *
 * Зачем билдер, если можно просто написать SQL?
 *   1. Защита от SQL-инъекций — значения подставляются через PreparedStatement (?),
 *      а не конкатенацией строк.
 *   2. Читаемость — видно структуру запроса без парсинга SQL глазами.
 *   3. Динамические условия — можно добавлять where по условию:
 *      if (filterByAge) builder.where("age", ">", 25);
 */
public class QueryBuilder {

    private final IDbClient dbClient;
    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();
    private String orderByClause;
    private Integer limitValue;
    private Integer offsetValue;

    public QueryBuilder(IDbClient dbClient, String table) {
        this.dbClient = dbClient;
        this.table = table;
    }

    /**
     * Указывает колонки для выборки.
     * Если не вызван — SELECT * (все колонки).
     *
     * .columns("id", "name", "email")  → SELECT id, name, email
     */
    public QueryBuilder columns(String... cols) {
        for (String col : cols) {
            columns.add(col);
        }
        return this;
    }

    /**
     * Добавляет условие WHERE с оператором "=".
     *
     * .where("status", "active")  → WHERE status = ?  (param: "active")
     */
    public QueryBuilder where(String column, Object value) {
        conditions.add(column + " = ?");
        params.add(value);
        return this;
    }

    /**
     * Добавляет условие WHERE с произвольным оператором.
     *
     * .where("age", ">", 25)      → WHERE age > ?    (param: 25)
     * .where("name", "LIKE", "%John%") → WHERE name LIKE ?
     * .where("status", "!=", "deleted")
     */
    public QueryBuilder where(String column, String operator, Object value) {
        conditions.add(column + " " + operator + " ?");
        params.add(value);
        return this;
    }

    /**
     * Добавляет условие WHERE ... IS NULL.
     *
     * .whereNull("deleted_at")  → WHERE deleted_at IS NULL
     */
    public QueryBuilder whereNull(String column) {
        conditions.add(column + " IS NULL");
        return this;
    }

    /**
     * Добавляет условие WHERE ... IS NOT NULL.
     */
    public QueryBuilder whereNotNull(String column) {
        conditions.add(column + " IS NOT NULL");
        return this;
    }

    /**
     * Добавляет условие WHERE ... IN (...).
     *
     * .whereIn("status", "active", "pending")  → WHERE status IN (?, ?)
     */
    public QueryBuilder whereIn(String column, Object... values) {
        StringJoiner placeholders = new StringJoiner(", ");
        for (Object value : values) {
            placeholders.add("?");
            params.add(value);
        }
        conditions.add(column + " IN (" + placeholders + ")");
        return this;
    }

    /**
     * Сортировка результатов.
     *
     * .orderBy("name")          → ORDER BY name
     * .orderBy("created_at DESC") → ORDER BY created_at DESC
     */
    public QueryBuilder orderBy(String clause) {
        this.orderByClause = clause;
        return this;
    }

    /**
     * Ограничение количества строк.
     */
    public QueryBuilder limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    /**
     * Смещение (для пагинации).
     *
     * .limit(10).offset(20)  → LIMIT 10 OFFSET 20  (третья страница по 10)
     */
    public QueryBuilder offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    /**
     * Собирает SQL и выполняет запрос. Возвращает список строк.
     */
    public List<Map<String, Object>> execute() {
        return dbClient.query(buildSql(), params.toArray());
    }

    /**
     * Собирает SQL и возвращает первую строку.
     */
    public Map<String, Object> executeOne() {
        return dbClient.queryOne(buildSql(), params.toArray());
    }

    /**
     * Собирает SQL и возвращает одно значение (первая колонка первой строки).
     */
    public Object executeScalar() {
        return dbClient.queryScalar(buildSql(), params.toArray());
    }

    /**
     * Возвращает собранный SQL (для отладки).
     */
    public String buildSql() {
        StringBuilder sql = new StringBuilder("SELECT ");

        // Колонки
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", columns));
        }

        // Таблица
        sql.append(" FROM ").append(table);

        // WHERE
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        // ORDER BY
        if (orderByClause != null) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        // LIMIT
        if (limitValue != null) {
            sql.append(" LIMIT ").append(limitValue);
        }

        // OFFSET
        if (offsetValue != null) {
            sql.append(" OFFSET ").append(offsetValue);
        }

        return sql.toString();
    }
}