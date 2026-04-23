package org.ugina.MOCK;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Встроенный мок-сервер авторизации для тестов.
 * Embedded mock auth server for tests.
 *
 * Поднимается прямо в тестовом процессе — никаких внешних зависимостей.
 * Runs inside the test process — no external dependencies.
 *
 * Uses com.sun.net.httpserver.HttpServer — built into JDK since Java 6.
 * Not a third-party library — it's part of the JDK itself.
 *
 * Endpoints:
 *
 *   POST /auth/login
 *     Body: {"username": "admin", "password": "password123"}
 *     Returns: {"access_token": "...", "refresh_token": "...", "expires_in": 3600}
 *     Wrong credentials → 401
 *
 *   POST /auth/refresh
 *     Body: {"refresh_token": "..."}
 *     Returns: {"access_token": "NEW...", "refresh_token": "NEW...", "expires_in": 3600}
 *     Invalid refresh token → 401
 *
 *   GET /api/protected
 *     Header: Authorization: Bearer <token>
 *     Valid token → 200 {"message": "success", "user": "admin"}
 *     Missing/invalid token → 401
 *
 *   GET /api/public
 *     No auth required → 200 {"message": "public data"}
 *
 * Usage in tests:
 *
 *   MockAuthServer mock = new MockAuthServer(0);  // 0 = random free port
 *   mock.start();
 *   String baseUrl = mock.getBaseUrl();            // http://localhost:54321
 *   // ... run tests ...
 *   mock.stop();
 */
public class MockAuthServer {

    private HttpServer server;
    private int port;

    // Current valid tokens — updated on each login/refresh
    // Текущие валидные токены — обновляются при каждом логине/рефреше
    private volatile String currentAccessToken;
    private volatile String currentRefreshToken;

    // Counter to track how many times refresh was called (for assertions in tests)
    // Счётчик вызовов refresh (для проверок в тестах)
    private final AtomicInteger refreshCount = new AtomicInteger(0);

    // Configurable token lifetime in seconds (default 3600)
    // Настраиваемое время жизни токена в секундах (по умолчанию 3600)
    private int tokenLifetimeSeconds = 3600;

    // Test credentials
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "password123";

    /**
     * @param port port to listen on, 0 = random free port
     *             порт для прослушивания, 0 = случайный свободный порт
     */
    public MockAuthServer(int port) {
        this.port = port;
    }

    /**
     * Starts the mock server.
     * Запускает мок-сервер.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register endpoints
        server.createContext("/auth/login", new LoginHandler());
        server.createContext("/auth/refresh", new RefreshHandler());
        server.createContext("/api/protected", new ProtectedHandler());
        server.createContext("/api/public", new PublicHandler());

        server.setExecutor(null);  // default executor
        server.start();

        // If port was 0, get the actual assigned port
        // Если порт был 0, получаем реально назначенный порт
        this.port = server.getAddress().getPort();
    }

    /**
     * Stops the mock server.
     * Останавливает мок-сервер.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Returns base URL: http://localhost:{port}
     */
    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Returns how many times /auth/refresh was called.
     * Полезно для проверки: "refresh вызвался ровно 1 раз, а не 4".
     */
    public int getRefreshCount() {
        return refreshCount.get();
    }

    /**
     * Sets token lifetime for next login/refresh response.
     * Set to 1 to simulate near-instant expiration.
     */
    public void setTokenLifetimeSeconds(int seconds) {
        this.tokenLifetimeSeconds = seconds;
    }

    /**
     * Invalidates current access token — next request with it will get 401.
     * Simulates token expiration on the server side.
     */
    public void invalidateAccessToken() {
        this.currentAccessToken = null;
    }

    // ──── Handlers ────

    /**
     * POST /auth/login — accepts username/password, returns tokens.
     */
    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // Simple credential check
            if (body.contains(VALID_USERNAME) && body.contains(VALID_PASSWORD)) {
                currentAccessToken = "access_" + UUID.randomUUID();
                currentRefreshToken = "refresh_" + UUID.randomUUID();

                String response = String.format(
                        "{\"access_token\": \"%s\", \"refresh_token\": \"%s\", \"expires_in\": %d}",
                        currentAccessToken, currentRefreshToken, tokenLifetimeSeconds);

                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 401, "{\"error\": \"invalid_credentials\"}");
            }
        }
    }

    /**
     * POST /auth/refresh — accepts refresh_token, returns new token pair.
     */
    private class RefreshHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            refreshCount.incrementAndGet();

            // Check if refresh token matches current valid one
            if (currentRefreshToken != null && body.contains(currentRefreshToken)) {
                currentAccessToken = "access_" + UUID.randomUUID();
                currentRefreshToken = "refresh_" + UUID.randomUUID();

                String response = String.format(
                        "{\"access_token\": \"%s\", \"refresh_token\": \"%s\", \"expires_in\": %d}",
                        currentAccessToken, currentRefreshToken, tokenLifetimeSeconds);

                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 401, "{\"error\": \"invalid_refresh_token\"}");
            }
        }
    }

    /**
     * GET /api/protected — requires valid Bearer token.
     */
    private class ProtectedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendResponse(exchange, 401, "{\"error\": \"missing_token\"}");
                return;
            }

            String token = authHeader.substring("Bearer ".length());

            if (currentAccessToken != null && currentAccessToken.equals(token)) {
                sendResponse(exchange, 200,
                        "{\"message\": \"success\", \"user\": \"admin\"}");
            } else {
                sendResponse(exchange, 401, "{\"error\": \"invalid_or_expired_token\"}");
            }
        }
    }

    /**
     * GET /api/public — no auth required.
     */
    private class PublicHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, "{\"message\": \"public data\"}");
        }
    }

    // ──── Utils ────

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}