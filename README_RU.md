# Mobile & API Test Automation Framework

Фреймворк для автоматизации тестирования: мобильные UI-тесты (Appium) + API-тесты (собственный HTTP-клиент на `java.net.http`) + обёртка для работы с базами данных (JDBC).

## Ключевые особенности

- **API-клиент без внешних зависимостей** — построен на стандартной библиотеке `java.net.http`, без RestAssured. Поддерживает все HTTP-методы, JSON/XML тела, SSL/mTLS сертификаты, fluent assertions
- **Мобильные UI-тесты** — Appium + Page Object, переключение local/cloud через конфиг
- **DB-клиент** — fluent SQL query builder поверх JDBC, работает с любой БД (PostgreSQL, MySQL, SQLite, H2)
- **Многопоточность** — параллельный запуск через TestNG, потокобезопасная архитектура
- **Отчёты** — Allure Reports с логированием запросов, ответов и скриншотами при падении

## Структура проекта

```
app/src/main/java/org/ugina/
├── ApiClient/                          # Библиотека API-клиента
│   ├── Client/
│   │   └── ApiRequestClient.java       # HTTP-клиент (java.net.http)
│   ├── Config/
│   │   ├── ApiClientConfigReader.java   # Чтение конфигурации
│   │   └── SslConfig.java              # SSL/mTLS сертификаты
│   ├── Data/
│   │   ├── IRequestBody.java           # Интерфейс тела запроса
│   │   ├── JsonRequestBody.java        # JSON-реализация
│   │   ├── XmlRequestBody.java         # XML-реализация
│   │   ├── RequestInfo.java            # Полная модель HTTP-запроса
│   │   └── ApiResponse.java            # Обёртка ответа с fluent assertions
│   ├── Db/
│   │   ├── IDbClient.java              # Интерфейс для любой БД
│   │   ├── JdbcDbClient.java           # Универсальная JDBC-реализация
│   │   ├── QueryBuilder.java           # Fluent SELECT builder
│   │   ├── InsertBuilder.java          # Fluent INSERT builder
│   │   ├── UpdateBuilder.java          # Fluent UPDATE builder
│   │   └── DeleteBuilder.java          # Fluent DELETE builder
│   └── utils/
│       └── Log.java                    # Логгер (SLF4J обёртка)
│
├── App.java
└── Data/
    └── PageDriverSetupData.java

app/src/test/java/org/ugina/
├── apiTests/
│   ├── SimpleApiTest.java              # API-тесты (GET, POST, PUT, PATCH, DELETE)
│   └── SimpleSslConfigTest.java        # Тесты SSL-сертификатов
├── pages/
│   └── MainPage.java                   # Page Object для Android
├── tests/
│   ├── BaseTest.java                   # Базовый класс UI-тестов
│   └── MainPageTest.java              # UI-тесты (checkbox, input, radio)
└── utils/
    ├── ConfigReader.java               # Чтение app.properties / cloud.properties
    ├── ContextLogger.java              # Логгер UI-тестов со скриншотами
    ├── DriverFactory.java              # Создание драйвера (local / cloud)
    └── DriverManager.java              # ThreadLocal хранение драйвера
```

## Быстрый старт

### Требования

- Java 17+
- Gradle 9.x (wrapper включён в проект)

### Запуск API-тестов

```bash
./gradlew apiTest
```

Никаких дополнительных настроек не нужно — тесты работают с публичным API `jsonplaceholder.typicode.com`.

### Запуск UI-тестов (локально)

```bash
# 1. Запустить Appium server
appium

# 2. Запустить Android-эмулятор

# 3. Запустить тесты
./gradlew uiTest
```

### Запуск UI-тестов (облако — LambdaTest)

```bash
export LT_USERNAME=your_username
export LT_ACCESS_KEY=your_access_key
./gradlew uiTest -Dtest.mode=cloud
```

### Запуск SSL-тестов

```bash
./gradlew sslTest
```

### Запуск всех тестов

```bash
./gradlew test
```

## Примеры использования

### API-клиент

```java
// Создаём клиент
ApiRequestClient client = new ApiRequestClient("https://api.example.com");

// GET-запрос
RequestInfo request = new RequestInfo();
request.setMethod("GET");
request.setPath("/users");
request.addQueryParam("page", "1");
request.addHeader("Accept", "application/json");

client.sendRequest(request)
      .assertStatus(200)
      .assertBodyContains("\"email\"")
      .assertDurationLessThan(5000);

// POST с JSON-телом
RequestInfo post = new RequestInfo();
post.setMethod("POST");
post.setPath("/users");
post.setBody(new JsonRequestBody("""
    {"name": "John", "email": "john@example.com"}
    """));

client.sendRequest(post)
      .assertStatus(201)
      .assertBodyContains("\"id\"");
```

### SSL/mTLS

```java
// Клиент с сертификатами
SslConfig ssl = SslConfig.builder()
    .keyStore("certs/client.p12", "keypass")
    .trustStore("certs/truststore.p12", "trustpass")
    .build();

ApiRequestClient client = new ApiRequestClient("https://secure-api.example.com", ssl);
```

### Database

```java
DbClient db = new JdbcDbClient("jdbc:postgresql://localhost:5432/testdb", "user", "pass");
db.connect();

// SELECT с fluent builder
List<Map<String, Object>> users = db.select("users")
    .columns("id", "name", "email")
    .where("status", "active")
    .where("age", ">", 25)
    .orderBy("name")
    .limit(10)
    .execute();

// INSERT
db.insert("users")
    .set("name", "John")
    .set("email", "john@example.com")
    .execute();

// UPDATE
db.update("users")
    .set("status", "inactive")
    .where("last_login", "<", "2025-01-01")
    .execute();

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
RequestInfo (что отправить)
    ├── method: "POST"
    ├── path: "/users"
    ├── headers: {Accept: application/json}
    ├── queryParams: {page: 1}
    └── body: IRequestBody
              ├── JsonRequestBody → Content-Type: application/json
              └── XmlRequestBody  → Content-Type: application/xml

         ↓ передаём в

ApiRequestClient (как отправить)
    ├── java.net.http.HttpClient (транспорт)
    ├── SslConfig → SSLContext (сертификаты)
    └── Log (логирование)

         ↓ возвращает

ApiResponse (что получили)
    ├── statusCode(), body(), headers()
    ├── assertStatus(200)
    ├── assertBodyContains("email")
    └── assertDurationLessThan(5000)
```

### Многопоточность

| Тип тестов | parallel | Почему |
|---|---|---|
| API | `methods` | HttpClient потокобезопасен, каждый тест независим |
| UI | `tests` | AndroidDriver НЕ потокобезопасен, один драйвер на поток |

## Конфигурация

### API (app/src/main/resources/apiClient.properties)

```properties
timeout.connection=30
```

### UI — локально (app/src/test/resources/app.properties)

```properties
appium.serverUrl=http://127.0.0.1:4723
appium.platformName=Android
appium.automationName=UiAutomator2
appium.deviceName=Android Emulator
```

### UI — облако (app/src/test/resources/cloud.properties)

```properties
cloud.provider=lambdatest
cloud.hub=https://hub.lambdatest.com/wd/hub
cloud.deviceName=Pixel 5
cloud.platformVersion=11
```

## Логирование

Все действия логируются через кастомный `Log` с форматом:

```
[2026-04-19 21:30:05.100] [INFO] [TestNG-method-1 #31] → GET /users
[2026-04-19 21:30:05.355] [INFO] [TestNG-method-1 #31] ← GET /users | status=200 | 255ms
```

SSL-сертификаты при загрузке:

```
══════ SSL CONFIG START ══════
KeyStore contains 2 entries:
  [client] type=PrivateKeyEntry (CLIENT CERT)
    Subject: CN=Test Client, O=Ugina Framework
    Issuer:  CN=Test CA, O=Ugina Framework
TrustStore contains 1 entries:
  [ca] type=trustedCertEntry (TRUSTED)
══════ SSL CONFIG END ══════
```

## Стек технологий

- **Java 17** — язык, text blocks, pattern matching
- **Gradle 9** — сборка, кастомные задачи
- **TestNG 7** — тестовый фреймворк, параллелизм, data providers
- **java.net.http** — HTTP-клиент (без внешних зависимостей)
- **JDBC** — работа с БД (без ORM)
- **SLF4J + Logback** — логирование
- **Appium 9** — мобильная автоматизация
- **Selenium 4** — WebDriver API
- **Allure 2** — отчётность
- **GitHub Actions** — CI