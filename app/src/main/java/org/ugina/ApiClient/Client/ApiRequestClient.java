package org.ugina.ApiClient.Client;

import org.ugina.ApiClient.Config.ApiClientConfigReader;
import org.ugina.ApiClient.Config.SslConfig;
import org.ugina.ApiClient.Data.ApiResponse;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.utils.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client built on java.net.http — no external dependencies.
 * HTTP-клиент на java.net.http — без внешних зависимостей.
 *
 * Each instance holds one HttpClient (thread-safe) and one baseUrl.
 * Каждый экземпляр содержит один HttpClient (потокобезопасный) и один baseUrl.
 *
 * Created and managed by ApiClientProvider — not by tests directly.
 * Создаётся и управляется через ApiClientProvider — не напрямую из тестов.
 */
public class ApiRequestClient {

    private static final Log log = Log.forClass(ApiRequestClient.class);

    private final HttpClient httpClient;
    private final String baseUrl;

    /**
     * Creates client without SSL.
     * Создаёт клиент без SSL.
     *
     * @param baseUrl base URL for all requests / базовый URL для всех запросов
     */
    public ApiRequestClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        ApiClientConfigReader.getIntOrDefault("timeout.connection", 30)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        log.info("ApiRequestClient created | baseUrl={}", baseUrl);
    }

    /**
     * Creates client with SSL certificates.
     * Создаёт клиент с SSL-сертификатами.
     *
     * SslConfig merges custom truststore with system truststore,
     * so regular HTTPS requests (to public APIs) still work.
     *
     * SslConfig объединяет кастомный truststore с системным,
     * поэтому обычные HTTPS-запросы (к публичным API) продолжают работать.
     *
     * @param baseUrl   base URL / базовый URL
     * @param sslConfig SSL configuration with keystore and/or truststore
     */
    public ApiRequestClient(String baseUrl, SslConfig sslConfig) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(
                        ApiClientConfigReader.getIntOrDefault("timeout.connection", 30)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(sslConfig.createSslContext())
                .build();

        log.info("ApiRequestClient created | baseUrl={} | SSL=enabled", baseUrl);
    }

    /**
     * Returns the base URL of this client.
     * Возвращает базовый URL этого клиента.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sends an HTTP request based on RequestInfo data.
     * Отправляет HTTP-запрос на основе данных из RequestInfo.
     *
     * Assembly order / Порядок сборки:
     *   1. Body → BodyPublisher (or noBody)
     *   2. Query params → URL string
     *   3. URI = baseUrl + path + query
     *   4. Method + body via .method(String, BodyPublisher)
     *   5. Headers from Map
     *   6. Send + log result
     *
     * @param requestInfo request data / данные запроса
     * @return ApiResponse wrapper / обёртка ответа
     * @throws IOException on network errors / при сетевых ошибках
     * @throws InterruptedException if thread was interrupted / если поток прерван
     */
    public ApiResponse sendRequest(RequestInfo requestInfo) throws IOException, InterruptedException {
        // 1. Body
        HttpRequest.BodyPublisher bodyPublisher;
        if (requestInfo.getBody() != null) {
            bodyPublisher = HttpRequest.BodyPublishers.ofString(requestInfo.getBody().content());
        } else {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }

        // 2. Query parameters → string
        String query = buildQueryString(requestInfo.getQueryParams());

        // 3. URI
        URI uri = URI.create(this.baseUrl + requestInfo.getPath() + query);

        // 4. Method + body
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .method(requestInfo.getMethod(), bodyPublisher);

        // 5. Headers
        if (requestInfo.getHeaders() != null) {
            requestInfo.getHeaders().forEach(builder::header);
        }

        // Auto-set Content-Type from body if not already in headers
        // Автоматически ставим Content-Type из body, если не задан в headers
        if (requestInfo.getBody() != null && !requestInfo.getHeaders().containsKey("Content-Type")) {
            builder.header("Content-Type", requestInfo.getBody().contentType());
        }

        HttpRequest request = builder.build();
        logRequest(request);
// DEBUG: проверяем что body publisher содержит данные
        System.out.println("=== DEBUG: method=" + request.method());
        System.out.println("=== DEBUG: bodyPublisher present=" + request.bodyPublisher().isPresent());
        request.bodyPublisher().ifPresent(bp ->
                System.out.println("=== DEBUG: contentLength=" + bp.contentLength()));

        // 6. Send
        try {
            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            logResponse(response, duration);
            return new ApiResponse(response, duration);

        } catch (IOException e) {
            log.error("Network error: {} {} | reason: {}",
                    requestInfo.getMethod(), uri, e.getMessage());
            throw e;

        } catch (InterruptedException e) {
            log.error("Request interrupted: {} {} | reason: {}",
                    requestInfo.getMethod(), uri, e.getMessage());
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    // ──── Query string builder ────

    /**
     * Builds query string from Map: {page=1, limit=10} → "?page=1&limit=10"
     * Собирает query-строку из Map: {page=1, limit=10} → "?page=1&limit=10"
     *
     * URLEncoder.encode() escapes special characters: space → %20, & → %26
     * URLEncoder.encode() экранирует спецсимволы: пробел → %20, & → %26
     */
    private String buildQueryString(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("?");
        queryParams.forEach((key, value) -> {
            if (sb.length() > 1) sb.append("&");
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    // ──── Logging ────

    /**
     * Logs HTTP request details: method, URL, headers, body presence.
     * Логирует детали запроса: метод, URL, заголовки, наличие тела.
     */
    private void logRequest(HttpRequest request) {
        if (request == null) {
            log.warn("Request is null — nothing to log");
            return;
        }

        log.info("══════ REQUEST ══════");
        log.info("→ {} {}", request.method(), request.uri());

        Map<String, List<String>> headers = request.headers().map();
        if (!headers.isEmpty()) {
            log.info("Headers:");
            headers.forEach((name, values) -> {
                String displayValue = isSensitiveHeader(name)
                        ? "****"
                        : String.join(", ", values);
                log.info("  {}: {}", name, displayValue);
            });
        }

        request.bodyPublisher().ifPresent(publisher ->
                log.debug("Body: (present, see request content)")
        );

        log.info("═════════════════════");
    }

    /**
     * Logs HTTP response details: status, duration, headers, body preview.
     * Логирует детали ответа: статус, время, заголовки, превью тела.
     */
    private void logResponse(HttpResponse<String> response, long durationMs) {
        if (response == null) {
            log.warn("Response is null — nothing to log");
            return;
        }

        log.info("══════ RESPONSE ══════");
        log.info("← {} | {}ms", response.statusCode(), durationMs);

        Map<String, List<String>> headers = response.headers().map();
        if (!headers.isEmpty()) {
            log.info("Headers:");
            headers.forEach((name, values) ->
                    log.info("  {}: {}", name, String.join(", ", values))
            );
        }

        String body = response.body();
        if (body != null && !body.isEmpty()) {
            if (body.length() <= 300) {
                log.info("Body: {}", body);
            } else {
                log.info("Body (preview): {}...", body.substring(0, 300));
                log.debug("Body (full): {}", body);
            }
        } else {
            log.info("Body: (empty)");
        }

        log.info("══════════════════════");
    }

    /**
     * Checks if a header contains sensitive data (masked in logs as "****").
     * Проверяет, содержит ли заголовок чувствительные данные (маскируется в логах).
     */
    private boolean isSensitiveHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.contains("authorization")
                || lower.contains("cookie")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("api-key")
                || lower.contains("x-api-key");
    }
}