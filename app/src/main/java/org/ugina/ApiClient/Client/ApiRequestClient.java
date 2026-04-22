package org.ugina.ApiClient.Client;

import org.ugina.ApiClient.Config.ApiClientConfigReader;
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

public class ApiRequestClient  {

    private static final Log log = Log.forClass(ApiRequestClient.class);

    private HttpClient httpClient;
    private String baseUrl;

    public ApiRequestClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Integer.parseInt(ApiClientConfigReader.get("timeout.connection"))))
                .followRedirects(HttpClient.Redirect.NORMAL) // CONFIG IN NEXT STEP
                .build();
    }

    /**
     * Логирует детали HTTP-запроса.
     *
     * Мы работаем с java.net.http.HttpRequest — поэтому не нужно гадать через reflection,
     * а можем напрямую вызывать его методы: .method(), .uri(), .headers().
     *
     * Формат вывода:
     *   ══════ REQUEST ══════
     *   → POST https://api.example.com/users?page=1
     *   Headers:
     *     Content-Type: application/json
     *     Authorization: ****
     *   Body: {"name": "John"}
     *   ═════════════════════
     *
     * @param request собранный запрос из java.net.http
     */
    private void logRequest(HttpRequest request) {
        if (request == null) {
            log.warn("Request is null — nothing to log");
            return;
        }

        log.info("══════ REQUEST ══════");
        log.info("→ {} {}", request.method(), request.uri());

        // Заголовки
        Map<String, List<String>> headers = request.headers().map();
        if (!headers.isEmpty()) {
            log.info("Headers:");
            headers.forEach((name, values) -> {
                // Маскируем чувствительные заголовки
                String displayValue = isSensitiveHeader(name)
                        ? "****"
                        : String.join(", ", values);
                log.info("  {}: {}", name, displayValue);
            });
        }

        // Тело — если есть, логируем на уровне debug (может быть большим)
        request.bodyPublisher().ifPresent(publisher ->
                log.debug("Body: (present, see request content)")
        );

        log.info("═════════════════════");
    }

    /**
     * Логирует детали HTTP-ответа.
     *
     * Формат вывода:
     *   ══════ RESPONSE ══════
     *   ← 201 Created | 245ms
     *   Headers:
     *     content-type: application/json; charset=utf-8
     *     cache-control: no-cache
     *   Body (preview): {"id":101,"name":"John"...
     *   ══════════════════════
     *
     * @param response ответ от сервера
     * @param durationMs время выполнения запроса в миллисекундах
     */
    private void logResponse(HttpResponse<String> response, long durationMs) {
        if (response == null) {
            log.warn("Response is null — nothing to log");
            return;
        }

        log.info("══════ RESPONSE ══════");
        log.info("← {} | {}ms", response.statusCode(), durationMs);

        // Заголовки ответа
        Map<String, List<String>> headers = response.headers().map();
        if (!headers.isEmpty()) {
            log.info("Headers:");
            headers.forEach((name, values) ->
                    log.info("  {}: {}", name, String.join(", ", values))
            );
        }

        // Тело ответа — превью на info, полное на debug
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
     * Проверяет, является ли заголовок чувствительным.
     * Такие заголовки маскируются в логах как "****".
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

    /**
     * Отправляет HTTP-запрос на основе собранных данных в RequestInfo.
     *
     * Порядок сборки запроса:
     *   1. Определяем наличие тела (body) → BodyPublisher
     *   2. Собираем query-параметры в строку → приклеиваем к URL
     *   3. Создаём URI (baseUrl + path + query)
     *   4. Устанавливаем метод и тело через .method(String, BodyPublisher)
     *   5. Добавляем заголовки из Map
     *   6. Отправляем и логируем результат
     *
     * @param requestInfo объект с данными запроса (метод, путь, заголовки, тело, query-параметры)
     * @return HttpResponse с телом ответа в виде строки
     * @throws IOException если произошла ошибка сети (таймаут, недоступный хост, разрыв соединения)
     * @throws InterruptedException если поток был прерван во время ожидания ответа
     */
    public ApiResponse sendRequest(RequestInfo requestInfo) throws IOException, InterruptedException {
        // Have body or not
        HttpRequest.BodyPublisher bodyPublisher;
        if (requestInfo.getBody() != null) {
            bodyPublisher = HttpRequest.BodyPublishers.ofString(requestInfo.getBody().content());
        } else {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }

        // Query parameters → string
        String query = "";
        if (requestInfo.getQueryParams() != null && !requestInfo.getQueryParams().isEmpty()) {
            StringBuilder sb = new StringBuilder("?");
            requestInfo.getQueryParams().forEach((key, value) -> {
                if (sb.length() > 1) sb.append("&");
                sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                sb.append("=");
                sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
            query = sb.toString();
        }

        // URI creation (baseUrl + path + query)
        URI customUri = URI.create(this.baseUrl + requestInfo.getPath() + query);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(customUri)
                .method(requestInfo.getMethod(), bodyPublisher);

        // Headers adding
        requestInfo.getHeaders().forEach(builder::header);

        HttpRequest request = builder.build();
        logRequest(request);

        try {
            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            logResponse(response, duration);
            ApiResponse customResponse = new ApiResponse(response, duration);
            return customResponse;

        } catch (IOException e) {
            // Сетевые ошибки: таймаут, DNS не найден, сервер не отвечает, разрыв соединения
            log.error("Network error: {} {} | reason: {}",
                    requestInfo.getMethod(), customUri, e.getMessage());
            throw e;  // пробрасываем дальше — пусть тест решает что делать

        } catch (InterruptedException e) {
            // Поток был прерван (например, TestNG остановил тест по таймауту)
            log.error("Request interrupted: {} {} | reason: {}",
                    requestInfo.getMethod(), customUri, e.getMessage());
            Thread.currentThread().interrupt();  // восстанавливаем флаг прерывания
            throw e;
        }
    }



}
