# Mobile & API Test Automation Framework

Фреймворк для автоматизации тестирования: мобильные UI-тесты (Appium) + API-тесты (собственный HTTP-клиент на `java.net.http`) + работа с базами данных (JDBC).

> **Тестовые классы в `src/test/` служат наглядными примерами возможностей фреймворка.** Каждый тест демонстрирует конкретную функцию: отправку запросов, работу с SSL-сертификатами, управление токенами, retry при падениях и т.д.

## Ключевые особенности

- **API-клиент без внешних зависимостей** — построен на стандартной библиотеке `java.net.http`, без RestAssured. Все HTTP-методы, JSON/XML тела, fluent assertions
- **SSL/mTLS** — загрузка сертификатов из resources, объединение с системным truststore, логирование цепочки сертификатов
- **Управление токенами** — автоматическая авторизация, проверка expiration, refresh при 401, потокобезопасный refresh (synchronized)
- **Многопоточность** — параллельный запуск через TestNG, singleton-клиент с `ConcurrentHashMap`, поддержка нескольких хостов одновременно
- **Retry-механика** — автоматический перезапуск тестов при сетевых ошибках, фильтрация по типу ошибки (сетевая → retry, assertion → fail)
- **DB-клиент** — fluent SQL query builder поверх JDBC, работает с любой БД (PostgreSQL, MySQL, SQLite, H2)
- **Мобильные UI-тесты** — Appium + Page Object, переключение local/cloud через конфиг
- **Отчёты** — Allure Reports с логированием запросов, ответов и скриншотами при падении

## Структура проекта

```
app/src/main/java/org/ugina/
├── ApiClient/
│   ├── Client/
│   │   ├── ApiRequestClient.java       # HTTP-клиент (java.net.http)
│   │   └── ApiClientProvider.java      # Singleton + multi-host (ConcurrentHashMap)
│   ├── Config/
│   │   ├── ApiClientConfigReader.java  # Чтение конфигурации
│   │   └── SslConfig.java             # SSL/mTLS сертификаты
│   ├── Data/
│   │   ├── IRequestBody.java          # Интерфейс тела запроса
│   │   ├── JsonRequestBody.java       # JSON-реализация
│   │   ├── XmlRequestBody.java        # XML-реализация
│   │   ├── RequestInfo.java           # Полная модель HTTP-запроса
│   │   └── ApiResponse.java           # Обёртка ответа с fluent assertions
│   ├── Token/
│   │   ├── ApiTokenManager.java       # Авторизация, refresh, auto-retry на 401
│   │   ├── ApiTokenProvider.java      # Хранилище токенов (ConcurrentHashMap)
│   │   └── ApiTokenData.java          # Модель: access + refresh + expiration
│   ├── Db/
│   │   ├── IDbClient.java             # Интерфейс для любой БД
│   │   ├── JdbcDbClient.java          # Универсальная JDBC-реализация
│   │   ├── QueryBuilder.java          # Fluent SELECT builder
│   │   ├── InsertBuilder.java         # Fluent INSERT builder
│   │   ├── UpdateBuilder.java         # Fluent UPDATE builder
│   │   └── DeleteBuilder.java         # Fluent DELETE builder
│   └── utils/
│       └── Log.java                   # Логгер (SLF4J обёртка)
│
├── App.java
└── Data/
    └── PageDriverSetupData.java

app/src/test/java/org/ugina/                    # Примеры использования фреймворка
├── apiTests/
│   ├── SimpleApiTest.java                      # API: GET, POST, PUT, PATCH, DELETE, query params, headers
│   ├── SimpleSslConfigTest.java                # SSL: загрузка сертификатов, mTLS, HTTPS-запросы
│   └── SimpleTokenTest.java                    # Токены: логин, refresh, auto-retry на 401
├── MOCK/
│   └── MockAuthServer.java                     # Встроенный мок-сервер авторизации (JDK HttpServer)
├── pages/
│   └── MainPage.java                           # Page Object для Android
├── tests/
│   ├── BaseTest.java                           # Базовый класс UI-тестов
│   └── MainPageTest.java                       # UI: checkbox, input, radio
└── utils/
    ├── ConfigReader.java                        # Чтение app.properties / cloud.properties
    ├── ContextLogger.java                       # Логгер UI-тестов со скриншотами
    ├── DriverFactory.java                       # Создание драйвера (local / cloud)
    ├── DriverManager.java                       # ThreadLocal хранение драйвера
    ├── RetryAnalyzer.java                       # Retry: перезапуск при сетевых ошибках
    └── RetryListener.java                       # Автоподстановка retry на все @Test
```

## Быстрый старт

### Требования

- Java 17+
- Gradle 9.x (wrapper включён)

### Запуск

```bash
./gradlew apiTest       # API-тесты (параллельно, 4 потока)
./gradlew uiTest        # UI/Appium тесты
./gradlew sslTest       # SSL-тесты
./gradlew test          # Все тесты
```

Для UI-тестов необходима настройка окружения: [SETUP.md](SETUP.md)

## Примеры использования

### API-клиент

```java
// Регистрируем хост (один раз)
ApiClientProvider.register("main", "https://api.example.com");

// Отправляем запрос
RequestInfo request = new RequestInfo();
request.setMethod("GET");
request.setPath("/users");
request.addQueryParam("page", "1");
request.addHeader("Accept", "application/json");

ApiClientProvider.get("main").sendRequest(request)
        .assertStatus(200)
        .assertBodyContains("\"email\"")
        .assertDurationLessThan(5000);
```

### Несколько хостов (параллельно)

```java
// Каждый хост — свой клиент, свой пул соединений
ApiClientProvider.register("api", "https://api.example.com");
ApiClientProvider.register("auth", "https://auth.example.com");
ApiClientProvider.register("payment", "https://pay.example.com");

// В разных потоках одновременно
ApiClientProvider.get("api").sendRequest(usersRequest);
ApiClientProvider.get("auth").sendRequest(loginRequest);
ApiClientProvider.get("payment").sendRequest(paymentRequest);
```

### Управление токенами

```java
// Авторизация
ApiTokenManager tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
tokenManager.authenticate("main", "admin", "password123");

// Запрос с автоматическим токеном
// Если 401 → refresh → retry — всё под капотом
RequestInfo request = new RequestInfo();
request.setMethod("GET");
request.setPath("/users/me");

tokenManager.sendWithAuth("main", request)
        .assertStatus(200)
        .assertBodyContains("\"email\"");
```

### SSL/mTLS

```java
SslConfig ssl = SslConfig.builder()
        .keyStore("certs/client.p12", "keypass")
        .trustStore("certs/truststore.p12", "trustpass")
        .build();

ApiClientProvider.register("secure", "https://secure-api.example.com", ssl);
```

### Database

```java
DbClient db = new JdbcDbClient("jdbc:postgresql://localhost:5432/testdb", "user", "pass");
db.connect();

// Fluent SELECT
List<Map<String, Object>> users = db.select("users")
        .columns("id", "name", "email")
        .where("status", "active")
        .where("age", ">", 25)
        .orderBy("name")
        .limit(10)
        .execute();

// INSERT / UPDATE / DELETE
db.insert("users").set("name", "John").set("email", "john@example.com").execute();
db.update("users").set("status", "inactive").where("id", 1).execute();
db.delete("users").where("status", "deleted").execute();

db.close();
```

### UI Page Object

```java
public class MainPageTest extends BaseTest {

    @Test
    public void testCheckbox() {
        MainPage page = new MainPage(driver, wait);
        assertFalse(page.isSelectedCheckBox1());
        page.clickCheckBox1();
        assertTrue(page.isSelectedCheckBox1());
    }
}
```

## Архитектура

### API-клиент

```
ApiClientProvider (ConcurrentHashMap)
    ├── "main"    → ApiRequestClient(baseUrl="https://api.example.com")
    ├── "auth"    → ApiRequestClient(baseUrl="https://auth.example.com")
    └── "payment" → ApiRequestClient(baseUrl="https://pay.example.com")

RequestInfo (что отправить)         → ApiRequestClient (как отправить)
    ├── method: "POST"                   ├── HttpClient (транспорт)
    ├── path: "/users"                   ├── SslConfig → SSLContext
    ├── headers: {Accept: ...}           └── Log (логирование)
    ├── queryParams: {page: 1}
    └── body: IRequestBody                    ↓
              ├── JsonRequestBody        ApiResponse (что получили)
              └── XmlRequestBody             ├── assertStatus(200)
                                             ├── assertBodyContains("email")
                                             └── assertDurationLessThan(5000)
```

### Управление токенами

```
authenticate("main", user, pass)
    → POST /auth/login → access_token + refresh_token
    → сохраняется в ApiTokenProvider

sendWithAuth("main", request)
    ├── токен expired? → refreshTokens() → новый access_token
    ├── добавляет Authorization: Bearer <token>
    ├── отправляет запрос
    ├── ответ 401? → forceExpire() → refreshTokens() → retry
    └── возвращает ApiResponse
```

### Retry-механика

```
Тест падает → RetryAnalyzer.retry()
    ├── IOException / HttpTimeoutException → retry (до 2 раз)
    ├── AssertionError → НЕ retry (это баг, не сеть)
    └── всё остальное → НЕ retry

RetryListener → автоматически подставляет RetryAnalyzer на все @Test
```

### Многопоточность

| Тип тестов | parallel | Почему |
|---|---|---|
| API | `methods` | HttpClient потокобезопасен, каждый тест независим |
| Токены | `false` | Тесты зависят друг от друга (логин → доступ → refresh) |
| UI | `tests` | AndroidDriver НЕ потокобезопасен, один драйвер на поток |

## Конфигурация

### API (src/main/resources/apiclient.properties)

```properties
api.baseUrl=https://jsonplaceholder.typicode.com
timeout.connection=30

# Именованные хосты (опционально)
# hosts.auth=https://auth.example.com
# hosts.payment=https://pay.example.com

# SSL (опционально)
# ssl.keyStore=certs/client.p12
# ssl.keyStorePassword=keypass
# ssl.trustStore=certs/truststore.p12
# ssl.trustStorePassword=trustpass
```

### UI — локально (src/test/resources/app.properties)

```properties
appium.serverUrl=http://127.0.0.1:4723
appium.platformName=Android
appium.automationName=UiAutomator2
appium.deviceName=Android Emulator
```

### UI — облако (src/test/resources/cloud.properties)

```properties
cloud.provider=lambdatest
cloud.hub=https://hub.lambdatest.com/wd/hub
```

## Логирование

Все запросы и ответы логируются с номером потока:

```
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ══════ REQUEST ══════
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → → POST https://api.example.com/auth/login
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → Headers:
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] →   Content-Type: application/json
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] →   Authorization: ****
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ═════════════════════
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ← 200 | 45ms
```

SSL-сертификаты при загрузке:

```
══════ SSL CONFIG START ══════
KeyStore contains 2 entries:
  [client] type=PrivateKeyEntry (CLIENT CERT)
    Subject: CN=Test Client, O=Ugina Framework
    Issuer:  CN=Test CA, O=Ugina Framework
TrustStore: merged 137 system + 1 custom certificates
══════ SSL CONFIG END ══════
```

Retry при падении:

```
[WARN] ↻ RETRYING [testGetUsers] — attempt 1/2 — reason: connection timed out
[INFO] → GET /users
[INFO] ← 200 | 180ms
```

## Стек технологий

- **Java 17** — text blocks, pattern matching, switch expressions
- **Gradle 9** — сборка, кастомные задачи (apiTest, uiTest, sslTest)
- **TestNG 7** — тестовый фреймворк, параллелизм, data providers, retry
- **java.net.http** — HTTP-клиент (без внешних зависимостей)
- **JDBC** — работа с БД (без ORM)
- **SLF4J + Logback** — логирование
- **Appium 9** — мобильная автоматизация
- **Selenium 4** — WebDriver API
- **Allure 2** — отчётность
- **GitHub Actions** — CI