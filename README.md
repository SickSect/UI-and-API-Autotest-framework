# Mobile & API Test Automation Framework

A test automation framework combining mobile UI tests (Appium) + API tests (custom HTTP client built on `java.net.http`) + database wrapper (JDBC).

## Key Features

- **API client with zero external dependencies** — built on the standard `java.net.http` library, no RestAssured. Supports all HTTP methods, JSON/XML bodies, SSL/mTLS certificates, fluent assertions
- **Mobile UI tests** — Appium + Page Object pattern, local/cloud switching via config
- **DB client** — fluent SQL query builder on top of JDBC, works with any database (PostgreSQL, MySQL, SQLite, H2)
- **Multithreading** — parallel execution via TestNG, thread-safe architecture
- **Reporting** — Allure Reports with request/response logging and screenshots on failure

## Project Structure

```
app/src/main/java/org/ugina/
├── ApiClient/                          # API client library
│   ├── Client/
│   │   └── ApiRequestClient.java       # HTTP client (java.net.http)
│   ├── Config/
│   │   ├── ApiClientConfigReader.java   # Configuration reader
│   │   └── SslConfig.java              # SSL/mTLS certificates
│   ├── Data/
│   │   ├── IRequestBody.java           # Request body interface
│   │   ├── JsonRequestBody.java        # JSON implementation
│   │   ├── XmlRequestBody.java         # XML implementation
│   │   ├── RequestInfo.java            # Complete HTTP request model
│   │   └── ApiResponse.java            # Response wrapper with fluent assertions
│   ├── Db/
│   │   ├── IDbClient.java              # Database interface (any DB)
│   │   ├── JdbcDbClient.java           # Universal JDBC implementation
│   │   ├── QueryBuilder.java           # Fluent SELECT builder
│   │   ├── InsertBuilder.java          # Fluent INSERT builder
│   │   ├── UpdateBuilder.java          # Fluent UPDATE builder
│   │   └── DeleteBuilder.java          # Fluent DELETE builder
│   └── utils/
│       └── Log.java                    # Logger (SLF4J wrapper)
│
├── App.java
└── Data/
    └── PageDriverSetupData.java

app/src/test/java/org/ugina/
├── apiTests/
│   ├── SimpleApiTest.java              # API tests (GET, POST, PUT, PATCH, DELETE)
│   └── SimpleSslConfigTest.java        # SSL certificate tests
├── pages/
│   └── MainPage.java                   # Page Object for Android
├── tests/
│   ├── BaseTest.java                   # Base class for UI tests
│   └── MainPageTest.java              # UI tests (checkbox, input, radio)
└── utils/
    ├── ConfigReader.java               # Reads app.properties / cloud.properties
    ├── ContextLogger.java              # UI test logger with screenshots
    ├── DriverFactory.java              # Driver creation (local / cloud)
    └── DriverManager.java              # ThreadLocal driver storage
```

## Quick Start

### Requirements

- Java 17+
- Gradle 9.x (wrapper included)

### Run API Tests

```bash
./gradlew apiTest
```

No additional setup needed — tests use the public API at `jsonplaceholder.typicode.com`.

### Run UI Tests (locally)

```bash
# 1. Start Appium server
appium

# 2. Start Android emulator

# 3. Run tests
./gradlew uiTest
```

For detailed environment setup see [SETUP_EN.md](SETUP_EN.md).

### Run UI Tests (cloud — LambdaTest)

```bash
export LT_USERNAME=your_username
export LT_ACCESS_KEY=your_access_key
./gradlew uiTest -Dtest.mode=cloud
```

### Run SSL Tests

```bash
./gradlew sslTest
```

### Run All Tests

```bash
./gradlew test
```

## Usage Examples

### API Client

```java
// Create client
ApiRequestClient client = new ApiRequestClient("https://api.example.com");

// GET request
RequestInfo request = new RequestInfo();
request.setMethod("GET");
request.setPath("/users");
request.addQueryParam("page", "1");
request.addHeader("Accept", "application/json");

client.sendRequest(request)
      .assertStatus(200)
      .assertBodyContains("\"email\"")
      .assertDurationLessThan(5000);

// POST with JSON body
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
// Client with certificates
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

// SELECT with fluent builder
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

## Architecture

### API Client

```
RequestInfo (what to send)
    ├── method: "POST"
    ├── path: "/users"
    ├── headers: {Accept: application/json}
    ├── queryParams: {page: 1}
    └── body: IRequestBody
              ├── JsonRequestBody → Content-Type: application/json
              └── XmlRequestBody  → Content-Type: application/xml

         ↓ passed to

ApiRequestClient (how to send)
    ├── java.net.http.HttpClient (transport)
    ├── SslConfig → SSLContext (certificates)
    └── Log (logging)

         ↓ returns

ApiResponse (what we got)
    ├── statusCode(), body(), headers()
    ├── assertStatus(200)
    ├── assertBodyContains("email")
    └── assertDurationLessThan(5000)
```

### Multithreading

| Test type | parallel | Reason |
|---|---|---|
| API | `methods` | HttpClient is thread-safe, each test is independent |
| UI | `tests` | AndroidDriver is NOT thread-safe, one driver per thread |

## Configuration

### API (app/src/main/resources/apiClient.properties)

```properties
timeout.connection=30
```

### UI — local (app/src/test/resources/app.properties)

```properties
appium.serverUrl=http://127.0.0.1:4723
appium.platformName=Android
appium.automationName=UiAutomator2
appium.deviceName=Android Emulator
```

### UI — cloud (app/src/test/resources/cloud.properties)

```properties
cloud.provider=lambdatest
cloud.hub=https://hub.lambdatest.com/wd/hub
cloud.deviceName=Pixel 5
cloud.platformVersion=11
```

## Logging

All actions are logged via a custom `Log` wrapper:

```
[2026-04-19 21:30:05.100] [INFO] [TestNG-method-1 #31] → GET /users
[2026-04-19 21:30:05.355] [INFO] [TestNG-method-1 #31] ← GET /users | status=200 | 255ms
```

SSL certificates on load:

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

## Tech Stack

- **Java 17** — text blocks, pattern matching
- **Gradle 9** — build system, custom tasks
- **TestNG 7** — test framework, parallelism, data providers
- **java.net.http** — HTTP client (zero external dependencies)
- **JDBC** — database access (no ORM)
- **SLF4J + Logback** — logging
- **Appium 9** — mobile automation
- **Selenium 4** — WebDriver API
- **Allure 2** — reporting
- **GitHub Actions** — CI