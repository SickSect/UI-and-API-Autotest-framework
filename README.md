# Mobile & API Test Automation Framework

A test automation framework combining mobile UI tests (Appium) + API tests (custom HTTP client built on `java.net.http`) + database layer (JDBC).

> **Test classes in `src/test/` serve as practical examples of the framework's capabilities.** Each test demonstrates a specific feature: sending requests, SSL certificates, token management, retry on failures, etc.

## Key Features

- **API client with zero external dependencies** — built on the standard `java.net.http` library, no RestAssured. All HTTP methods, JSON/XML bodies, fluent assertions
- **SSL/mTLS** — certificate loading from resources, merge with system truststore, certificate chain logging
- **Token management** — automatic authentication, expiration tracking, refresh on 401, thread-safe refresh (synchronized)
- **Multithreading** — parallel execution via TestNG, singleton client with `ConcurrentHashMap`, multiple simultaneous hosts
- **Retry mechanism** — automatic test rerun on network errors, error type filtering (network → retry, assertion → fail)
- **DB client** — fluent SQL query builder on top of JDBC, works with any database (PostgreSQL, MySQL, SQLite, H2)
- **Mobile UI tests** — Appium + Page Object pattern, local/cloud switching via config
- **Reporting** — Allure Reports with request/response logging and screenshots on failure

## Project Structure

```
app/src/main/java/org/ugina/
├── ApiClient/
│   ├── Client/
│   │   ├── ApiRequestClient.java       # HTTP client (java.net.http)
│   │   └── ApiClientProvider.java      # Singleton + multi-host (ConcurrentHashMap)
│   ├── Config/
│   │   ├── ApiClientConfigReader.java  # Configuration reader
│   │   └── SslConfig.java             # SSL/mTLS certificates
│   ├── Data/
│   │   ├── IRequestBody.java          # Request body interface
│   │   ├── JsonRequestBody.java       # JSON implementation
│   │   ├── XmlRequestBody.java        # XML implementation
│   │   ├── RequestInfo.java           # Complete HTTP request model
│   │   └── ApiResponse.java           # Response wrapper with fluent assertions
│   ├── Token/
│   │   ├── ApiTokenManager.java       # Auth, refresh, auto-retry on 401
│   │   ├── ApiTokenProvider.java      # Token storage (ConcurrentHashMap)
│   │   └── ApiTokenData.java          # Model: access + refresh + expiration
│   ├── Db/
│   │   ├── IDbClient.java             # Database interface (any DB)
│   │   ├── JdbcDbClient.java          # Universal JDBC implementation
│   │   ├── QueryBuilder.java          # Fluent SELECT builder
│   │   ├── InsertBuilder.java         # Fluent INSERT builder
│   │   ├── UpdateBuilder.java         # Fluent UPDATE builder
│   │   └── DeleteBuilder.java         # Fluent DELETE builder
│   └── utils/
│       └── Log.java                   # Logger (SLF4J wrapper)
│
├── App.java
└── Data/
    └── PageDriverSetupData.java

app/src/test/java/org/ugina/                    # Framework usage examples
├── apiTests/
│   ├── SimpleApiTest.java                      # API: GET, POST, PUT, PATCH, DELETE, query params, headers
│   ├── SimpleSslConfigTest.java                # SSL: certificate loading, mTLS, HTTPS requests
│   └── SimpleTokenTest.java                    # Tokens: login, refresh, auto-retry on 401
├── MOCK/
│   └── MockAuthServer.java                     # Embedded mock auth server (JDK HttpServer)
├── pages/
│   └── MainPage.java                           # Page Object for Android
├── tests/
│   ├── BaseTest.java                           # Base class for UI tests
│   └── MainPageTest.java                       # UI: checkbox, input, radio
└── utils/
    ├── ConfigReader.java                        # Reads app.properties / cloud.properties
    ├── ContextLogger.java                       # UI test logger with screenshots
    ├── DriverFactory.java                       # Driver creation (local / cloud)
    ├── DriverManager.java                       # ThreadLocal driver storage
    ├── RetryAnalyzer.java                       # Retry: rerun on network errors
    └── RetryListener.java                       # Auto-attaches retry to all @Test
```

## Quick Start

### Requirements

- Java 17+
- Gradle 9.x (wrapper included)

### Run

```bash
./gradlew apiTest       # API tests (parallel, 4 threads)
./gradlew uiTest        # UI/Appium tests
./gradlew sslTest       # SSL tests
./gradlew test          # All tests
```

For UI test environment setup see [SETUP_EN.md](SETUP_EN.md).

## Usage Examples

### API Client

```java
// Register a host (once)
ApiClientProvider.register("main", "https://api.example.com");

// Send a request
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

### Multiple Hosts (parallel)

```java
// Each host = separate client, separate connection pool
ApiClientProvider.register("api", "https://api.example.com");
ApiClientProvider.register("auth", "https://auth.example.com");
ApiClientProvider.register("payment", "https://pay.example.com");

// From different threads simultaneously
ApiClientProvider.get("api").sendRequest(usersRequest);
ApiClientProvider.get("auth").sendRequest(loginRequest);
ApiClientProvider.get("payment").sendRequest(paymentRequest);
```

### Token Management

```java
// Authenticate
ApiTokenManager tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
tokenManager.authenticate("main", "admin", "password123");

// Request with automatic token handling
// If 401 → refresh → retry — all under the hood
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

## Architecture

### API Client

```
ApiClientProvider (ConcurrentHashMap)
    ├── "main"    → ApiRequestClient(baseUrl="https://api.example.com")
    ├── "auth"    → ApiRequestClient(baseUrl="https://auth.example.com")
    └── "payment" → ApiRequestClient(baseUrl="https://pay.example.com")

RequestInfo (what to send)          → ApiRequestClient (how to send)
    ├── method: "POST"                   ├── HttpClient (transport)
    ├── path: "/users"                   ├── SslConfig → SSLContext
    ├── headers: {Accept: ...}           └── Log (logging)
    ├── queryParams: {page: 1}
    └── body: IRequestBody                    ↓
              ├── JsonRequestBody        ApiResponse (what we got)
              └── XmlRequestBody             ├── assertStatus(200)
                                             ├── assertBodyContains("email")
                                             └── assertDurationLessThan(5000)
```

### Token Management

```
authenticate("main", user, pass)
    → POST /auth/login → access_token + refresh_token
    → stored in ApiTokenProvider

sendWithAuth("main", request)
    ├── token expired? → refreshTokens() → new access_token
    ├── adds Authorization: Bearer <token>
    ├── sends request
    ├── response 401? → forceExpire() → refreshTokens() → retry
    └── returns ApiResponse
```

### Retry Mechanism

```
Test fails → RetryAnalyzer.retry()
    ├── IOException / HttpTimeoutException → retry (up to 2 times)
    ├── AssertionError → NO retry (it's a bug, not a network issue)
    └── everything else → NO retry

RetryListener → auto-attaches RetryAnalyzer to all @Test methods
```

### Multithreading

| Test type | parallel | Reason |
|---|---|---|
| API | `methods` | HttpClient is thread-safe, each test is independent |
| Tokens | `false` | Tests depend on each other (login → access → refresh) |
| UI | `tests` | AndroidDriver is NOT thread-safe, one driver per thread |

## Configuration

### API (src/main/resources/apiclient.properties)

```properties
api.baseUrl=https://jsonplaceholder.typicode.com
timeout.connection=30

# Named hosts (optional)
# hosts.auth=https://auth.example.com
# hosts.payment=https://pay.example.com

# SSL (optional)
# ssl.keyStore=certs/client.p12
# ssl.keyStorePassword=keypass
# ssl.trustStore=certs/truststore.p12
# ssl.trustStorePassword=trustpass
```

### UI — local (src/test/resources/app.properties)

```properties
appium.serverUrl=http://127.0.0.1:4723
appium.platformName=Android
appium.automationName=UiAutomator2
appium.deviceName=Android Emulator
```

### UI — cloud (src/test/resources/cloud.properties)

```properties
cloud.provider=lambdatest
cloud.hub=https://hub.lambdatest.com/wd/hub
```

## Logging

All requests and responses are logged with thread ID:

```
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ══════ REQUEST ══════
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → → POST https://api.example.com/auth/login
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → Headers:
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] →   Content-Type: application/json
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] →   Authorization: ****
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ═════════════════════
[2026-04-26 20:42:32] [INFO] [TestNG-method-1 #31] → ← 200 | 45ms
```

SSL certificates on load:

```
══════ SSL CONFIG START ══════
KeyStore contains 2 entries:
  [client] type=PrivateKeyEntry (CLIENT CERT)
    Subject: CN=Test Client, O=Ugina Framework
    Issuer:  CN=Test CA, O=Ugina Framework
TrustStore: merged 137 system + 1 custom certificates
══════ SSL CONFIG END ══════
```

Retry on failure:

```
[WARN] ↻ RETRYING [testGetUsers] — attempt 1/2 — reason: connection timed out
[INFO] → GET /users
[INFO] ← 200 | 180ms
```

## Tech Stack

- **Java 17** — text blocks, pattern matching, switch expressions
- **Gradle 9** — build system, custom tasks (apiTest, uiTest, sslTest)
- **TestNG 7** — test framework, parallelism, data providers, retry
- **java.net.http** — HTTP client (zero external dependencies)
- **JDBC** — database access (no ORM)
- **SLF4J + Logback** — logging
- **Appium 9** — mobile automation
- **Selenium 4** — WebDriver API
- **Allure 2** — reporting
- **GitHub Actions** — CI