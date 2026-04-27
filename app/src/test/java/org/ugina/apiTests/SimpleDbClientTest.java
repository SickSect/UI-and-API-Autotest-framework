package org.ugina.apiTests;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Db.IDbClient;
import org.ugina.ApiClient.Db.JdbcDbClient;

import java.util.List;
import java.util.Map;

/**
 * Tests for DB module using H2 in-memory database.
 * Тесты DB-модуля с использованием H2 in-memory базы данных.
 *
 * H2 in-memory — база данных, которая живёт только в оперативной памяти.
 * Не нужен внешний сервер, не нужна установка.
 * Создаётся при connect(), уничтожается при close().
 * Идеально для тестов.
 *
 * jdbc:h2:mem:testdb — URL подключения:
 *   h2    — тип БД
 *   mem   — in-memory (не на диске)
 *   testdb — имя базы (произвольное)
 *
 * Credentials: "sa" / "" — стандартные для H2 (sa = system admin).
 */
public class SimpleDbClientTest {

    private IDbClient db;

    @BeforeClass
    public void setUp() {
        // H2 in-memory: создаётся при connect, умирает при close
        db = new JdbcDbClient("jdbc:h2:mem:  ;DB_CLOSE_DELAY=-1", "sa", "");
        db.connect();

        // Создаём тестовую таблицу
        db.execute("""
                CREATE TABLE users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100),
                    age INT,
                    status VARCHAR(20) DEFAULT 'active'
                )
                """);

        // Наполняем тестовыми данными
        db.execute("INSERT INTO users (name, email, age, status) VALUES ('Alice', 'alice@test.com', 30, 'active')");
        db.execute("INSERT INTO users (name, email, age, status) VALUES ('Bob', 'bob@test.com', 25, 'active')");
        db.execute("INSERT INTO users (name, email, age, status) VALUES ('Charlie', 'charlie@test.com', 35, 'inactive')");
        db.execute("INSERT INTO users (name, email, age, status) VALUES ('Diana', 'diana@test.com', 28, 'active')");
        db.execute("INSERT INTO users (name, email, age, status) VALUES ('Eve', 'eve@test.com', 40, 'deleted')");
    }

    @AfterClass
    public void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    // ════════════════════════════════════════════
    //  CONNECTION
    // ════════════════════════════════════════════

    @Test(priority = 1)
    public void testIsConnected() {
        Assert.assertTrue(db.isConnected(), "Should be connected after connect()");
    }

    // ════════════════════════════════════════════
    //  RAW QUERY
    // ════════════════════════════════════════════

    @Test(priority = 2)
    public void testRawQuery() {
        List<Map<String, Object>> results = db.query("SELECT * FROM users");
        Assert.assertEquals(results.size(), 5, "Should have 5 users");
    }

    @Test(priority = 2)
    public void testRawQueryWithParam() {
        List<Map<String, Object>> results = db.query(
                "SELECT * FROM users WHERE name = ?", "Alice");
        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).get("EMAIL"), "alice@test.com");
    }

    @Test(priority = 2)
    public void testQueryOne() {
        Map<String, Object> user = db.queryOne(
                "SELECT * FROM users WHERE name = ?", "Bob");
        Assert.assertNotNull(user);
        Assert.assertEquals(user.get("NAME"), "Bob");
        Assert.assertEquals(user.get("AGE"), 25);
    }

    @Test(priority = 2)
    public void testQueryOneNotFound() {
        Map<String, Object> user = db.queryOne(
                "SELECT * FROM users WHERE name = ?", "NonExistent");
        Assert.assertNull(user, "Should return null for non-existent user");
    }

    @Test(priority = 2)
    public void testQueryScalar() {
        Object count = db.queryScalar("SELECT COUNT(*) FROM users");
        Assert.assertNotNull(count);
        Assert.assertEquals(((Number) count).intValue(), 5);
    }

    // ════════════════════════════════════════════
    //  SELECT BUILDER
    // ════════════════════════════════════════════

    @Test(priority = 3)
    public void testSelectAll() {
        List<Map<String, Object>> results = db.select("users").execute();
        Assert.assertEquals(results.size(), 5);
    }

    @Test(priority = 3)
    public void testSelectWithColumns() {
        List<Map<String, Object>> results = db.select("users")
                .columns("name", "email")
                .execute();
        Assert.assertEquals(results.size(), 5);
        // Should only have name and email columns
        Assert.assertTrue(results.get(0).containsKey("NAME"));
        Assert.assertTrue(results.get(0).containsKey("EMAIL"));
        Assert.assertFalse(results.get(0).containsKey("AGE"));
    }

    @Test(priority = 3)
    public void testSelectWhereEquals() {
        List<Map<String, Object>> results = db.select("users")
                .where("status", "active")
                .execute();
        Assert.assertEquals(results.size(), 3, "Should have 3 active users");
    }

    @Test(priority = 3)
    public void testSelectWhereOperator() {
        List<Map<String, Object>> results = db.select("users")
                .where("age", ">", 30)
                .execute();
        Assert.assertEquals(results.size(), 2, "Charlie(35) and Eve(40)");
    }

    @Test(priority = 3)
    public void testSelectMultipleWhere() {
        List<Map<String, Object>> results = db.select("users")
                .where("status", "active")
                .where("age", ">", 27)
                .execute();
        Assert.assertEquals(results.size(), 2, "Alice(30) and Diana(28)");
    }

    @Test(priority = 3)
    public void testSelectWhereIn() {
        List<Map<String, Object>> results = db.select("users")
                .whereIn("name", "Alice", "Bob", "Eve")
                .execute();
        Assert.assertEquals(results.size(), 3);
    }

    @Test(priority = 3)
    public void testSelectOrderBy() {
        List<Map<String, Object>> results = db.select("users")
                .columns("name")
                .orderBy("name")
                .execute();
        Assert.assertEquals(results.get(0).get("NAME"), "Alice");
        Assert.assertEquals(results.get(4).get("NAME"), "Eve");
    }

    @Test(priority = 3)
    public void testSelectLimit() {
        List<Map<String, Object>> results = db.select("users")
                .orderBy("name")
                .limit(2)
                .execute();
        Assert.assertEquals(results.size(), 2);
    }

    @Test(priority = 3)
    public void testSelectLimitOffset() {
        List<Map<String, Object>> results = db.select("users")
                .orderBy("name")
                .limit(2)
                .offset(2)
                .execute();
        Assert.assertEquals(results.size(), 2);
        Assert.assertEquals(results.get(0).get("NAME"), "Charlie");
    }

    @Test(priority = 3)
    public void testSelectExecuteOne() {
        Map<String, Object> user = db.select("users")
                .where("name", "Diana")
                .executeOne();
        Assert.assertNotNull(user);
        Assert.assertEquals(user.get("AGE"), 28);
    }

    @Test(priority = 3)
    public void testSelectExecuteScalar() {
        Object count = db.select("users")
                .columns("COUNT(*)")
                .where("status", "active")
                .executeScalar();
        Assert.assertEquals(((Number) count).intValue(), 3);
    }

    // ════════════════════════════════════════════
    //  INSERT BUILDER
    // ════════════════════════════════════════════

    @Test(priority = 4)
    public void testInsert() {
        int affected = db.insert("users")
                .set("name", "Frank")
                .set("email", "frank@test.com")
                .set("age", 33)
                .set("status", "active")
                .execute();

        Assert.assertEquals(affected, 1, "Should insert 1 row");

        // Verify inserted data
        Map<String, Object> frank = db.select("users")
                .where("name", "Frank")
                .executeOne();
        Assert.assertNotNull(frank);
        Assert.assertEquals(frank.get("EMAIL"), "frank@test.com");
        Assert.assertEquals(frank.get("AGE"), 33);
    }

    // ════════════════════════════════════════════
    //  UPDATE BUILDER
    // ════════════════════════════════════════════

    @Test(priority = 5)
    public void testUpdate() {
        int affected = db.update("users")
                .set("status", "suspended")
                .where("name", "Bob")
                .execute();

        Assert.assertEquals(affected, 1);

        // Verify updated data
        Map<String, Object> bob = db.select("users")
                .where("name", "Bob")
                .executeOne();
        Assert.assertEquals(bob.get("STATUS"), "suspended");
    }

    @Test(priority = 5)
    public void testUpdateMultipleRows() {
        int affected = db.update("users")
                .set("status", "archived")
                .where("status", "inactive")
                .execute();

        Assert.assertTrue(affected >= 1, "Should update at least 1 row");
    }

    // ════════════════════════════════════════════
    //  DELETE BUILDER
    // ════════════════════════════════════════════

    @Test(priority = 6)
    public void testDelete() {
        int affected = db.delete("users")
                .where("status", "deleted")
                .execute();

        Assert.assertEquals(affected, 1, "Eve was 'deleted'");

        // Verify deletion
        Map<String, Object> eve = db.select("users")
                .where("name", "Eve")
                .executeOne();
        Assert.assertNull(eve, "Eve should be gone");
    }

    // ════════════════════════════════════════════
    //  BUILT SQL VERIFICATION
    // ════════════════════════════════════════════

    @Test(priority = 7)
    public void testBuildSqlSelect() {
        String sql = db.select("users")
                .columns("name", "email")
                .where("status", "active")
                .where("age", ">", 25)
                .orderBy("name")
                .limit(10)
                .buildSql();

        Assert.assertEquals(sql,
                "SELECT name, email FROM users WHERE status = ? AND age > ? ORDER BY name LIMIT 10");
    }

    @Test(priority = 7)
    public void testBuildSqlInsert() {
        String sql = db.insert("users")
                .set("name", "Test")
                .set("email", "test@test.com")
                .buildSql();

        Assert.assertEquals(sql,
                "INSERT INTO users (name, email) VALUES (?, ?)");
    }

    @Test(priority = 7)
    public void testBuildSqlUpdate() {
        String sql = db.update("users")
                .set("status", "inactive")
                .where("age", "<", 18)
                .buildSql();

        Assert.assertEquals(sql,
                "UPDATE users SET status = ? WHERE age < ?");
    }

    @Test(priority = 7)
    public void testBuildSqlDelete() {
        String sql = db.delete("users")
                .where("status", "deleted")
                .buildSql();

        Assert.assertEquals(sql,
                "DELETE FROM users WHERE status = ?");
    }
}