package org.ugina.ApiClient.Db;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для работы с любой базой данных через JDBC.
 *
 * JDBC (Java Database Connectivity) — стандартный API Java для работы с БД.
 * Он уже является абстракцией: один и тот же код работает с PostgreSQL, MySQL,
 * Oracle, SQLite — меняется только строка подключения и драйвер.
 *
 * Зачем обёртка поверх JDBC?
 *
 * JDBC сам по себе многословен:
 *   Connection conn = DriverManager.getConnection(url, user, pass);
 *   PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
 *   ps.setInt(1, 42);
 *   ResultSet rs = ps.executeQuery();
 *   while (rs.next()) { ... }
 *   rs.close(); ps.close(); conn.close();
 *
 * С нашей обёрткой:
 *   List<Map<String, Object>> users = dbClient.query("SELECT * FROM users WHERE id = ?", 42);
 *
 * Или через билдер:
 *   dbClient.select("users").where("id", 42).execute();
 *
 * Реализации:
 *   JdbcDbClient — универсальная, работает с любой БД через JDBC
 *
 * Подключение настраивается в конфиге:
 *   db.url=jdbc:postgresql://localhost:5432/testdb
 *   db.username=postgres
 *   db.password=secret
 *   db.driver=org.postgresql.Driver
 */

/**
 * HOW TO USE
 * DbClient db = new JdbcDbClient("jdbc:h2:mem:testdb", "sa", "");
 * db.connect();
 *
 * // INSERT
 * db.insert("users").set("name", "John").set("age", 30).execute();
 *
 * // SELECT
 * Map<String, Object> user = db.select("users")
 *         .where("name", "John")
 *         .executeOne();
 *
 * // UPDATE
 * db.update("users").set("age", 31).where("name", "John").execute();
 *
 * // DELETE
 * db.delete("users").where("name", "John").execute();
 *
 * db.close();
 */
public interface IDbClient extends AutoCloseable {

    // ──── Подключение ────

    /**
     * Устанавливает соединение с БД.
     * Вызывается один раз перед началом работы.
     */
    void connect();

    /**
     * Проверяет что соединение активно.
     */
    boolean isConnected();

    // ──── Чтение (SELECT) ────

    /**
     * Выполняет SELECT-запрос и возвращает результат как список строк.
     * Каждая строка — Map, где ключ = имя колонки, значение = данные.
     *
     * Пример:
     *   List<Map<String, Object>> users = dbClient.query(
     *       "SELECT id, name FROM users WHERE age > ?", 25);
     *
     *   // users = [
     *   //   {id=1, name="John"},
     *   //   {id=2, name="Jane"}
     *   // ]
     *
     * Почему Map, а не объект?
     *   Потому что мы не знаем структуру таблицы заранее. Map — универсален.
     *   В тестах обычно важно проверить 1-2 поля, а не маппить весь объект.
     *
     * @param sql  SQL-запрос с плейсхолдерами ? (PreparedStatement)
     * @param params значения для подстановки в ? по порядку
     * @return список строк результата
     */
    List<Map<String, Object>> query(String sql, Object... params);

    /**
     * Выполняет SELECT и возвращает первую строку.
     * Удобно когда ожидаем ровно один результат (SELECT ... WHERE id = ?).
     *
     * @return первая строка или null если результат пустой
     */
    Map<String, Object> queryOne(String sql, Object... params);

    /**
     * Выполняет SELECT и возвращает значение одной ячейки.
     * Удобно для COUNT(*), MAX(*), SELECT name FROM ... WHERE id = ?.
     *
     * Пример:
     *   int count = (int) dbClient.queryScalar("SELECT COUNT(*) FROM users");
     *   String name = (String) dbClient.queryScalar("SELECT name FROM users WHERE id = ?", 1);
     *
     * @return значение первой колонки первой строки, или null
     */
    Object queryScalar(String sql, Object... params);

    // ──── Запись (INSERT, UPDATE, DELETE) ────

    /**
     * Выполняет INSERT, UPDATE или DELETE.
     * Возвращает количество затронутых строк.
     *
     * Пример:
     *   int affected = dbClient.execute(
     *       "UPDATE users SET status = ? WHERE age < ?", "inactive", 18);
     *
     * @param sql    SQL-запрос с плейсхолдерами ?
     * @param params значения для подстановки
     * @return количество изменённых строк
     */
    int execute(String sql, Object... params);

    // ──── Билдер запросов ────

    /**
     * Создаёт SELECT-билдер для указанной таблицы.
     *
     * Пример:
     *   dbClient.select("users")
     *           .columns("id", "name", "email")
     *           .where("status", "active")
     *           .where("age", ">", 25)
     *           .orderBy("name")
     *           .limit(10)
     *           .execute();
     */
    QueryBuilder select(String table);

    /**
     * Создаёт INSERT-билдер.
     *
     * Пример:
     *   dbClient.insert("users")
     *           .set("name", "John")
     *           .set("email", "john@example.com")
     *           .set("age", 30)
     *           .execute();
     */
    org.ugina.ApiClient.Db.InsertBuilder insert(String table);

    /**
     * Создаёт UPDATE-билдер.
     *
     * Пример:
     *   dbClient.update("users")
     *           .set("status", "inactive")
     *           .where("last_login", "<", "2025-01-01")
     *           .execute();
     */
    UpdateBuilder update(String table);

    /**
     * Создаёт DELETE-билдер.
     *
     * Пример:
     *   dbClient.delete("users")
     *           .where("status", "deleted")
     *           .execute();
     */
    DeleteBuilder delete(String table);
}