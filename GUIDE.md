# Framework Usage Guide / Инструкция по использованию фреймворка

Эта инструкция написана для тех, кто впервые работает с автотестами. Каждый пример — рабочий код, который можно скопировать и запустить.

## Оглавление

1. [Как создать тестовый класс](#1-как-создать-тестовый-класс)
2. [Как отправить запрос](#2-как-отправить-запрос)
3. [Как проверить код ответа](#3-как-проверить-код-ответа)
4. [Как проверить содержимое ответа](#4-как-проверить-содержимое-ответа)
5. [Как отправить запрос с телом (POST)](#5-как-отправить-запрос-с-телом-post)
6. [Как работать с базой данных](#6-как-работать-с-базой-данных)
7. [Как отправлять запросы на разные хосты](#7-как-отправлять-запросы-на-разные-хосты)
8. [Как работать с разными базами данных](#8-как-работать-с-разными-базами-данных)
9. [Как работать с токенами (авторизация)](#9-как-работать-с-токенами-авторизация)
10. [Как именовать тесты для Allure-отчёта](#10-как-именовать-тесты-для-allure-отчёта)
11. [Как запускать тесты](#11-как-запускать-тесты)
12. [Шпаргалка](#12-шпаргалка)

---

## 1. Как создать тестовый класс

Каждый тестовый файл — это Java-класс с методами, помеченными `@Test`. Фреймворк TestNG находит эти методы и запускает их.

```java
package org.ugina.apiTests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Client.ApiRequestClient;
import org.ugina.ApiClient.Data.RequestInfo;

public class MyFirstTest {

    private ApiRequestClient client;

    // @BeforeClass — выполняется ОДИН РАЗ перед всеми тестами в этом классе.
    // Здесь мы настраиваем клиент: указываем на какой сервер отправлять запросы.
    @BeforeClass
    public void setUp() {
        // has() проверяет: клиент с таким именем уже создан?
        // Если да — не создаём заново, берём существующий.
        // Без этой проверки каждый тестовый класс будет пересоздавать клиент.
        if (!ApiClientProvider.has("myapi")) {
            ApiClientProvider.register("myapi", "https://jsonplaceholder.typicode.com");
        }
        client = ApiClientProvider.get("myapi");
    }

    // @Test — это один тест. Каждый @Test-метод — отдельная проверка.
    @Test
    public void myFirstTest() throws Exception {
        // Здесь будет код теста
    }
}
```

**Что здесь происходит:**
- `@BeforeClass` — подготовка. Выполняется один раз перед всеми тестами.
- `ApiClientProvider.register("myapi", "...")` — регистрируем хост под именем "myapi".
- `ApiClientProvider.get("myapi")` — получаем клиент для этого хоста.
- `@Test` — метод-тест. Пиши столько `@Test`-методов, сколько нужно проверок.

---

## 2. Как отправить запрос

Для отправки запроса нужны три шага: создать `RequestInfo`, заполнить его, отправить через `client`.

```java
@Test
public void testGetUsers() throws Exception {
    // Шаг 1: Создаём объект запроса
    RequestInfo request = new RequestInfo();

    // Шаг 2: Заполняем — ЧТО отправляем
    request.setMethod("GET");          // HTTP-метод: GET, POST, PUT, DELETE, PATCH
    request.setPath("/users");         // Путь (добавляется к базовому URL)

    // Шаг 3: Отправляем
    client.sendRequest(request);
}
```

**Что такое HTTP-методы:**
- `GET` — получить данные (список пользователей, информацию о товаре)
- `POST` — создать что-то новое (нового пользователя, заказ)
- `PUT` — полностью заменить существующее (обновить все поля пользователя)
- `PATCH` — частично обновить (поменять только email)
- `DELETE` — удалить

---

## 3. Как проверить код ответа

Каждый HTTP-ответ содержит числовой код. Основные:
- `200` — всё хорошо
- `201` — создано успешно
- `400` — ошибка в запросе (неправильные данные)
- `401` — не авторизован (нет токена или токен невалиден)
- `404` — не найдено
- `500` — ошибка на сервере

```java
@Test
public void testStatusCode() throws Exception {
    RequestInfo request = new RequestInfo();
    request.setMethod("GET");
    request.setPath("/users");

    // .assertStatus(200) — проверяет что сервер вернул код 200.
    // Если код другой — тест упадёт с понятным сообщением:
    // "Expected status 200, got 404"
    client.sendRequest(request)
            .assertStatus(200);
}
```

---

## 4. Как проверить содержимое ответа

Сервер возвращает данные в теле ответа (обычно JSON). Можно проверить что в нём есть нужные слова или значения.

```java
@Test
public void testResponseContent() throws Exception {
    RequestInfo request = new RequestInfo();
    request.setMethod("GET");
    request.setPath("/users/1");

    client.sendRequest(request)
            .assertStatus(200)                          // код 200
            .assertBodyContains("Leanne Graham")        // в ответе есть это имя
            .assertBodyContains("\"email\"")            // в ответе есть поле email
            .assertBodyNotContains("error")             // в ответе НЕТ слова error
            .assertBodyNotEmpty()                       // ответ не пустой
            .assertDurationLessThan(5000);              // ответ пришёл быстрее 5 секунд
}
```

**Все проверки можно цеплять в цепочку.** Каждая проверка возвращает тот же объект ответа, поэтому можно писать `.assertStatus(200).assertBodyContains("...").assertDurationLessThan(5000)` в одну цепочку.

**Если нужно сохранить ответ и использовать позже:**

```java
@Test
public void testSaveAndCompare() throws Exception {
    // Отправляем первый запрос и сохраняем ответ
    RequestInfo request1 = new RequestInfo();
    request1.setMethod("GET");
    request1.setPath("/users/1");
    ApiResponse response1 = client.sendRequest(request1);

    // ... отправляем ещё 3 запроса, делаем что-то ...

    // Отправляем пятый запрос
    RequestInfo request5 = new RequestInfo();
    request5.setMethod("GET");
    request5.setPath("/users/1");
    ApiResponse response5 = client.sendRequest(request5);

    // Сравниваем данные из первого и пятого запросов
    Assert.assertEquals(response1.body(), response5.body(),
            "Данные не должны измениться между запросами");
}
```

**Сохранение данных между запросами — главное преимущество фреймворка.** Ты можешь сохранить ответ первого запроса и сравнить его с ответом пятого. Всё в одном методе, без пересылки данных между классами.

---

## 5. Как отправить запрос с телом (POST)

При создании ресурса (POST, PUT, PATCH) нужно отправить данные — тело запроса.

```java
import org.ugina.ApiClient.Data.JsonRequestBody;

@Test
public void testCreateUser() throws Exception {
    RequestInfo request = new RequestInfo();
    request.setMethod("POST");
    request.setPath("/users");

    // Тело запроса — JSON-строка с данными нового пользователя
    request.setBody(new JsonRequestBody("""
            {
                "name": "John",
                "email": "john@example.com",
                "age": 30
            }
            """));

    client.sendRequest(request)
            .assertStatus(201)              // 201 = создано
            .assertBodyContains("\"id\"");  // сервер вернул ID нового ресурса
}
```

**С заголовками и query-параметрами:**

```java
@Test
public void testFullRequest() throws Exception {
    RequestInfo request = new RequestInfo();
    request.setMethod("POST");
    request.setPath("/users");

    // Заголовки — метаинформация о запросе
    request.addHeader("Accept", "application/json");
    request.addHeader("X-Request-Id", "test-123");

    // Query-параметры — добавляются к URL: /users?source=test&version=2
    request.addQueryParam("source", "test");
    request.addQueryParam("version", "2");

    // Тело
    request.setBody(new JsonRequestBody("""
            {"name": "John", "email": "john@example.com"}
            """));

    client.sendRequest(request)
            .assertStatus(201);
}
```

**Если тело в формате XML:**

```java
import org.ugina.ApiClient.Data.XmlRequestBody;

request.setBody(new XmlRequestBody("""
        <?xml version="1.0" encoding="UTF-8"?>
        <user>
            <name>John</name>
            <email>john@example.com</email>
        </user>
        """));
```

---

## 6. Как работать с базой данных

### Подключение

```java
import org.ugina.ApiClient.Db.JdbcDbClient;
import org.ugina.ApiClient.Db.IDbClient;

// PostgreSQL
IDbClient db = new JdbcDbClient(
        "jdbc:postgresql://localhost:5432/mydb", "user", "password");
db.connect();

// MySQL
IDbClient db = new JdbcDbClient(
        "jdbc:mysql://localhost:3306/mydb", "root", "password");
db.connect();
```

### SELECT — посмотреть что в базе

```java
// Получить всех активных пользователей старше 25
List<Map<String, Object>> users = db.select("users")
        .columns("id", "name", "email")       // какие колонки нужны
        .where("status", "active")             // WHERE status = 'active'
        .where("age", ">", 25)                 // AND age > 25
        .orderBy("name")                       // сортировка
        .limit(10)                             // не больше 10 строк
        .execute();

// Результат — список. Каждый элемент — одна строка из базы:
// users.get(0).get("name") → "Alice"

// Получить одну строку
Map<String, Object> user = db.select("users")
        .where("id", 42)
        .executeOne();

// Получить одно значение (COUNT, MAX и т.д.)
Object count = db.select("users")
        .columns("COUNT(*)")
        .where("status", "active")
        .executeScalar();
```

### INSERT — добавить запись

```java
db.insert("users")
        .set("name", "John")
        .set("email", "john@example.com")
        .set("age", 30)
        .execute();
```

### UPDATE — обновить запись

```java
db.update("users")
        .set("status", "inactive")
        .where("last_login", "<", "2025-01-01")
        .execute();
```

### DELETE — удалить запись

```java
db.delete("users")
        .where("status", "deleted")
        .execute();
```

### Полный пример: API-запрос + проверка в базе

```java
@Test
public void testCreateUserAndCheckDb() throws Exception {
    // 1. Создаём пользователя через API
    RequestInfo request = new RequestInfo();
    request.setMethod("POST");
    request.setPath("/users");
    request.setBody(new JsonRequestBody("""
            {"name": "John", "email": "john@example.com"}
            """));

    ApiResponse response = client.sendRequest(request);
    response.assertStatus(201);

    // 2. Проверяем что пользователь появился в базе
    Map<String, Object> user = db.select("users")
            .where("email", "john@example.com")
            .executeOne();

    Assert.assertNotNull(user, "Пользователь должен быть в базе");
    Assert.assertEquals(user.get("name"), "John");

    // 3. Чистим за собой
    db.delete("users").where("email", "john@example.com").execute();
}
```

---

## 7. Как отправлять запросы на разные хосты

В одном тесте можно работать с несколькими серверами одновременно.

```java
@BeforeClass
public void setUp() {
    // has() предотвращает пересоздание клиента.
    // Если 10 тестовых классов используют "main-api" — клиент создастся один раз,
    // остальные 9 получат тот же экземпляр. Один HttpClient, один пул соединений.
    if (!ApiClientProvider.has("main-api")) {
        ApiClientProvider.register("main-api", "https://api.example.com");
    }
    if (!ApiClientProvider.has("auth")) {
        ApiClientProvider.register("auth", "https://auth.example.com");
    }
    if (!ApiClientProvider.has("payment")) {
        ApiClientProvider.register("payment", "https://pay.example.com");
    }
}

@Test
public void testCrossServiceFlow() throws Exception {
    // Запрос на auth-сервис
    RequestInfo login = new RequestInfo();
    login.setMethod("POST");
    login.setPath("/login");
    login.setBody(new JsonRequestBody("""
            {"username": "admin", "password": "secret"}
            """));
    ApiResponse loginResponse = ApiClientProvider.get("auth").sendRequest(login);

    // Запрос на основной API
    RequestInfo order = new RequestInfo();
    order.setMethod("POST");
    order.setPath("/orders");
    order.setBody(new JsonRequestBody("""
            {"product": "laptop", "quantity": 1}
            """));
    ApiResponse orderResponse = ApiClientProvider.get("main-api").sendRequest(order);

    // Проверка на платёжном сервисе
    RequestInfo payment = new RequestInfo();
    payment.setMethod("GET");
    payment.setPath("/payments/latest");
    ApiClientProvider.get("payment").sendRequest(payment)
            .assertStatus(200);
}
```

> **Важно:** всегда используй `has()` перед `register()`. Без проверки каждый тестовый класс будет пересоздавать клиент — это не ошибка, но лишняя работа. С проверкой — один клиент на весь прогон, независимо от количества тестовых классов.

---

## 8. Как работать с разными базами данных

Аналогично хостам — создаёшь несколько клиентов.

```java
private IDbClient mainDb;
private IDbClient analyticsDb;

@BeforeClass
public void setUp() {
    mainDb = new JdbcDbClient(
            "jdbc:postgresql://localhost:5432/main", "user", "pass");
    mainDb.connect();

    analyticsDb = new JdbcDbClient(
            "jdbc:postgresql://localhost:5432/analytics", "user", "pass");
    analyticsDb.connect();
}

@Test
public void testDataSync() throws Exception {
    // Данные из основной базы
    Map<String, Object> user = mainDb.select("users")
            .where("id", 1)
            .executeOne();

    // Те же данные в аналитической базе
    Map<String, Object> event = analyticsDb.select("user_events")
            .where("user_id", 1)
            .executeOne();

    Assert.assertNotNull(event, "Данные должны попасть в аналитику");
}

@AfterClass
public void tearDown() {
    mainDb.close();
    analyticsDb.close();
}
```

---

## 9. Как работать с токенами (авторизация)

Если API требует авторизацию:

```java
import org.ugina.ApiClient.Token.ApiTokenManager;

private ApiTokenManager tokenManager;

@BeforeClass
public void setUp() {
    ApiClientProvider.register("api", "https://api.example.com");
    tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
    tokenManager.authenticate("api", "admin", "password123");
}

@Test
public void testProtectedEndpoint() {
    RequestInfo request = new RequestInfo();
    request.setMethod("GET");
    request.setPath("/users/me");

    // sendWithAuth — автоматически:
    //   1. Добавляет токен в заголовок
    //   2. Если токен истёк — обновляет
    //   3. Если 401 — обновляет и повторяет
    tokenManager.sendWithAuth("api", request)
            .assertStatus(200)
            .assertBodyContains("\"email\"");
}
```

---

## 10. Как именовать тесты для Allure-отчёта

Аннотации Allure группируют тесты в красивый отчёт:

```java
import io.qameta.allure.*;

@Epic("User Management")                     // верхний уровень — модуль
public class UserApiTest {

    @Test
    @Feature("User CRUD")                    // фича внутри модуля
    @Story("Create user")                    // конкретный сценарий
    @Description("POST /users creates a new user and returns 201")  // описание
    @Severity(SeverityLevel.CRITICAL)        // критичность
    public void testCreateUser() throws Exception {
        // ...
    }
}
```

**Как это выглядит в отчёте:**

```
📁 User Management                          ← @Epic
  📁 User CRUD                              ← @Feature
    ✅ Create user                           ← @Story
       POST /users creates a new user...     ← @Description
       Severity: CRITICAL                    ← @Severity
```

**Минимальный набор — хотя бы `@Description`:**

```java
@Test
@Description("Проверка что GET /users возвращает список пользователей")
public void testGetUsers() throws Exception {
    // ...
}
```

---

## 11. Как запускать тесты

```bash
./gradlew apiTest       # API-тесты (параллельно)
./gradlew uiTest        # UI-тесты (Appium)
./gradlew sslTest       # SSL-тесты
./gradlew e2eTest       # DB + End-to-End тесты
./gradlew test          # Все тесты
```

---

## 12. Шпаргалка

### Запрос

```java
RequestInfo r = new RequestInfo();
r.setMethod("GET");                              // метод
r.setPath("/users");                             // путь
r.addHeader("Accept", "application/json");       // заголовок
r.addQueryParam("page", "1");                    // ?page=1
r.setBody(new JsonRequestBody("{...}"));         // тело
```

### Проверки

```java
response.assertStatus(200);                      // код ответа
response.assertBodyContains("email");            // содержит
response.assertBodyNotContains("error");         // не содержит
response.assertBodyNotEmpty();                   // не пустой
response.assertDurationLessThan(5000);           // быстрее 5с
response.assertHeader("content-type", "json");   // заголовок
response.assertSuccess();                        // код 2xx
```

### Данные ответа

```java
response.statusCode();    // 200
response.body();          // {"name": "John"}
response.durationMs();    // 145
response.header("...");   // значение заголовка
response.isSuccess();     // true/false
```

### База данных

```java
db.select("t").where("c", v).execute();                    // SELECT
db.insert("t").set("c", v).execute();                      // INSERT
db.update("t").set("c", v).where("id", 1).execute();      // UPDATE
db.delete("t").where("id", 1).execute();                   // DELETE
```

### Allure

```java
@Epic("Модуль")           @Feature("Фича")
@Story("Сценарий")        @Description("Описание")
@Severity(SeverityLevel.CRITICAL)
```