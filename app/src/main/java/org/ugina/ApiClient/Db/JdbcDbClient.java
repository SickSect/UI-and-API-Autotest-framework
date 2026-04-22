package org.ugina.ApiClient.Db;

import org.ugina.ApiClient.utils.Log;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Универсальная реализация DbClient через JDBC.
 * Работает с любой БД, для которой есть JDBC-драйвер:
 *   PostgreSQL, MySQL, Oracle, SQLite, H2, MSSQL...
 *
 * Что меняется между БД:
 *   - URL подключения (jdbc:postgresql://... vs jdbc:mysql://...)
 *   - Драйвер (org.postgresql.Driver vs com.mysql.cj.jdbc.Driver)
 *   - Мелкие различия в SQL-синтаксисе (LIMIT vs TOP, и т.д.)
 *
 * Что НЕ меняется:
 *   - Connection, PreparedStatement, ResultSet — одинаковые для всех
 *   - Наш API (select/insert/update/delete) — одинаковый для всех
 *
 * Использование:
 *
 *   // PostgreSQL
 *   DbClient db = new JdbcDbClient(
 *       "jdbc:postgresql://localhost:5432/testdb", "postgres", "secret");
 *   db.connect();
 *
 *   // MySQL
 *   DbClient db = new JdbcDbClient(
 *       "jdbc:mysql://localhost:3306/testdb", "root", "secret");
 *   db.connect();
 *
 *   // SQLite (встроенная, без сервера — удобно для тестов)
 *   DbClient db = new JdbcDbClient("jdbc:sqlite:test.db", "", "");
 *   db.connect();
 *
 *   // H2 in-memory (для unit-тестов без внешних зависимостей)
 *   DbClient db = new JdbcDbClient("jdbc:h2:mem:testdb", "sa", "");
 *   db.connect();
 *
 *   // Работаем
 *   db.select("users").where("age", ">", 25).execute();
 *   db.insert("users").set("name", "John").set("age", 30).execute();
 *
 *   // Закрываем
 *   db.close();
 */
public class JdbcDbClient implements IDbClient {

    private static final Log log = Log.forClass(JdbcDbClient.class);

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public JdbcDbClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    // ──── Подключение ────

    @Override
    public void connect() {
        try {
            log.info("Connecting to DB: {}", url);
            connection = DriverManager.getConnection(url, username, password);
            log.info("Connected successfully | catalog={}", connection.getCatalog());
        } catch (SQLException e) {
            log.error("Failed to connect to DB: {}", e.getMessage());
            throw new DbException("Failed to connect to: " + url, e);
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("DB connection closed");
            } catch (SQLException e) {
                log.warn("Error closing DB connection: {}", e.getMessage());
            }
        }
    }

    // ──── Чтение ────

    @Override
    public List<Map<String, Object>> query(String sql, Object... params) {
        log.info("QUERY: {}", sql);
        log.debug("Params: {}", formatParams(params));

        try (PreparedStatement ps = prepareStatement(sql, params);
             ResultSet rs = ps.executeQuery()) {

            List<Map<String, Object>> results = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }

            log.info("QUERY returned {} rows", results.size());
            return results;

        } catch (SQLException e) {
            log.error("QUERY failed: {} | SQL: {}", e.getMessage(), sql);
            throw new DbException("Query failed: " + sql, e);
        }
    }

    @Override
    public Map<String, Object> queryOne(String sql, Object... params) {
        List<Map<String, Object>> results = query(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Object queryScalar(String sql, Object... params) {
        Map<String, Object> row = queryOne(sql, params);
        if (row == null || row.isEmpty()) return null;
        return row.values().iterator().next();
    }

    // ──── Запись ────

    @Override
    public int execute(String sql, Object... params) {
        log.info("EXECUTE: {}", sql);
        log.debug("Params: {}", formatParams(params));

        try (PreparedStatement ps = prepareStatement(sql, params)) {
            int affected = ps.executeUpdate();
            log.info("EXECUTE affected {} rows", affected);
            return affected;

        } catch (SQLException e) {
            log.error("EXECUTE failed: {} | SQL: {}", e.getMessage(), sql);
            throw new DbException("Execute failed: " + sql, e);
        }
    }

    // ──── Билдеры ────

    @Override
    public QueryBuilder select(String table) {
        return new QueryBuilder(this, table);
    }

    @Override
    public InsertBuilder insert(String table) {
        return new InsertBuilder(this, table);
    }

    @Override
    public UpdateBuilder update(String table) {
        return new UpdateBuilder(this, table);
    }

    @Override
    public DeleteBuilder delete(String table) {
        return new DeleteBuilder(this, table);
    }

    // ──── Внутренние методы ────

    /**
     * Создаёт PreparedStatement и подставляет параметры.
     *
     * PreparedStatement vs Statement:
     *   Statement:  "SELECT * FROM users WHERE id = " + id  ← SQL-инъекция!
     *   Prepared:   "SELECT * FROM users WHERE id = ?"      ← безопасно
     *
     * PreparedStatement отправляет запрос и данные ОТДЕЛЬНО.
     * Даже если в value будет "1; DROP TABLE users" — это будет просто строка,
     * а не исполняемый SQL-код.
     */
    private PreparedStatement prepareStatement(String sql, Object... params) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new DbException("Not connected to DB. Call connect() first.");
        }

        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);  // JDBC индексация с 1, не с 0
        }
        return ps;
    }

    /**
     * Форматирует параметры для логирования.
     */
    private String formatParams(Object[] params) {
        if (params == null || params.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ──── Exception ────

    public static class DbException extends RuntimeException {
        public DbException(String message) {
            super(message);
        }

        public DbException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}