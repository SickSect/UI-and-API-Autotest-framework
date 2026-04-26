package org.ugina.ApiClient.Token;

import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Client.ApiRequestClient;
import org.ugina.ApiClient.Data.ApiResponse;
import org.ugina.ApiClient.Data.JsonRequestBody;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.utils.Log;

/**
 * Handles token lifecycle: authenticate, check expiration, refresh, retry.
 * Управляет жизненным циклом токенов: авторизация, проверка, обновление, повтор.
 *
 * Flow / Поток:
 *
 *   1. authenticate(host, login, password)
 *      → POST /auth/login → gets access + refresh tokens
 *      → stores in ApiTokenProvider under host name
 *
 *   2. sendWithAuth(host, requestInfo)
 *      → checks: is token expired?
 *        → YES: refresh first, then send
 *        → NO:  send directly
 *      → if server returns 401:
 *        → refresh tokens
 *        → retry original request
 *      → returns ApiResponse
 *
 * Thread safety / Потокобезопасность:
 *
 *   refreshTokens() is synchronized — only one thread refreshes at a time.
 *   refreshTokens() синхронизирован — только один поток обновляет за раз.
 *
 *   Without this: 4 threads get 401, all 4 try to refresh simultaneously.
 *   Server accepts first refresh, invalidates the token. Threads 2-4 fail.
 *
 *   Без этого: 4 потока получают 401, все 4 пытаются обновить одновременно.
 *   Сервер принимает первый refresh, инвалидирует токен. Потоки 2-4 падают.
 *
 *   With synchronized: thread 1 refreshes, threads 2-4 wait.
 *   When thread 2 enters — token is already fresh, no need to refresh again.
 *
 * Usage / Использование:
 *
 *   ApiTokenManager tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
 *
 *   // Authenticate once
 *   tokenManager.authenticate("main", "admin", "password123");
 *
 *   // Send requests — token management is automatic
 *   ApiResponse response = tokenManager.sendWithAuth("main", requestInfo);
 */
public class ApiTokenManager {

    private static final Log log = Log.forClass(ApiTokenManager.class);

    // API paths for auth endpoints
    // Пути API для эндпоинтов авторизации
    private final String loginPath;
    private final String refreshPath;

    /**
     * @param loginPath   path to login endpoint, e.g. "/auth/login"
     * @param refreshPath path to refresh endpoint, e.g. "/auth/refresh"
     */
    public ApiTokenManager(String loginPath, String refreshPath) {
        this.loginPath = loginPath;
        this.refreshPath = refreshPath;
    }

    /**
     * Authenticates against a host — sends credentials, stores tokens.
     * Авторизуется на хосте — отправляет credentials, сохраняет токены.
     *
     * @param hostName  name registered in ApiClientProvider / имя из ApiClientProvider
     * @param username  login / логин
     * @param password  password / пароль
     */
    public void authenticate(String hostName, String username, String password) {
        log.info("Authenticating on host '{}' as '{}'", hostName, username);

        ApiRequestClient client = ApiClientProvider.get(hostName);

        RequestInfo request = new RequestInfo();
        request.setMethod("POST");
        request.setPath(loginPath);
        request.setBody(new JsonRequestBody(String.format(
                "{\"username\": \"%s\", \"password\": \"%s\"}", username, password)));

        try {
            ApiResponse response = client.sendRequest(request);

            if (!response.isSuccess()) {
                throw new TokenException("Authentication failed: status " + response.statusCode()
                        + " | body: " + response.body());
            }

            // Parse tokens from response
            // Парсим токены из ответа
            String accessToken = extractJsonValue(response.body(), "access_token");
            String refreshToken = extractJsonValue(response.body(), "refresh_token");
            String expiresIn = extractJsonValue(response.body(), "expires_in");

            if (accessToken == null) {
                throw new TokenException("No access_token in response: " + response.body());
            }

            // Create token data with or without expiration
            // Создаём данные токена с или без времени жизни
            ApiTokenData tokenData;
            if (expiresIn != null) {
                tokenData = new ApiTokenData(accessToken, refreshToken, Long.parseLong(expiresIn));
            } else {
                tokenData = new ApiTokenData(accessToken, refreshToken);
            }

            // Store in provider
            // Сохраняем в провайдере
            ApiTokenProvider.register(tokenData, hostName);
            log.info("Authentication successful on '{}'", hostName);

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Authentication failed on '{}': {}", hostName, e.getMessage());
            throw new TokenException("Authentication failed on '" + hostName + "'", e);
        }
    }

    /**
     * Refreshes tokens for a host. Only one thread can refresh at a time.
     * Обновляет токены для хоста. Только один поток может обновлять за раз.
     *
     * synchronized prevents multiple threads from refreshing simultaneously:
     *   Thread 1: enters, refreshes, gets new tokens.
     *   Thread 2: was waiting, enters, checks — tokens are already fresh.
     *
     * @param hostName host name / имя хоста
     */
    public synchronized void refreshTokens(String hostName) {
        log.info("Refreshing tokens for '{}'...", hostName);

        ApiTokenData currentTokens = ApiTokenProvider.get(hostName);
        if (currentTokens == null) {
            throw new TokenException("No tokens found for '" + hostName + "'. Call authenticate() first.");
        }

        // Double-check: maybe another thread already refreshed while we waited
        // Перепроверка: возможно другой поток уже обновил пока мы ждали
        if (!currentTokens.isExpired()) {
            log.info("Tokens already refreshed by another thread for '{}'", hostName);
            return;
        }

        String refreshToken = currentTokens.getRefreshToken();
        if (refreshToken == null) {
            throw new TokenException("No refresh token for '" + hostName + "'. Re-authenticate.");
        }

        ApiRequestClient client = ApiClientProvider.get(hostName);

        RequestInfo request = new RequestInfo();
        request.setMethod("POST");
        request.setPath(refreshPath);
        request.setBody(new JsonRequestBody(String.format(
                "{\"refresh_token\": \"%s\"}", refreshToken)));

        try {
            ApiResponse response = client.sendRequest(request);

            if (!response.isSuccess()) {
                // Refresh token also expired — need full re-authentication
                // Refresh токен тоже истёк — нужна полная реавторизация
                log.error("Refresh failed for '{}': status {}", hostName, response.statusCode());
                ApiTokenProvider.reset(hostName);
                throw new TokenException("Refresh failed for '" + hostName
                        + "': status " + response.statusCode() + ". Re-authenticate.");
            }

            // Parse new tokens
            String newAccess = extractJsonValue(response.body(), "access_token");
            String newRefresh = extractJsonValue(response.body(), "refresh_token");
            String expiresIn = extractJsonValue(response.body(), "expires_in");

            // Update stored tokens
            // Обновляем сохранённые токены
            if (expiresIn != null) {
                currentTokens.update(newAccess, newRefresh, Long.parseLong(expiresIn));
            } else {
                currentTokens.update(newAccess, newRefresh);
            }

            log.info("Tokens refreshed successfully for '{}'", hostName);

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token refresh failed for '{}': {}", hostName, e.getMessage());
            throw new TokenException("Token refresh failed for '" + hostName + "'", e);
        }
    }

    /**
     * Sends request with automatic token management.
     * Отправляет запрос с автоматическим управлением токенами.
     *
     * Steps / Шаги:
     *   1. Check if token is expired → refresh proactively
     *   2. Add Authorization header
     *   3. Send request
     *   4. If 401 → refresh + retry once
     *   5. Return response
     *
     * @param hostName    host name from ApiClientProvider / имя хоста
     * @param requestInfo request data / данные запроса
     * @return ApiResponse
     */
    public ApiResponse sendWithAuth(String hostName, RequestInfo requestInfo) {
        ApiRequestClient client = ApiClientProvider.get(hostName);
        ApiTokenData tokenData = ApiTokenProvider.get(hostName);

        if (tokenData == null) {
            throw new TokenException("No tokens for '" + hostName + "'. Call authenticate() first.");
        }

        try {
            // Step 1: Proactive refresh if expired
            // Шаг 1: Упреждающее обновление если истёк
            if (tokenData.isExpired()) {
                log.info("Token expired for '{}' — refreshing proactively", hostName);
                refreshTokens(hostName);
                tokenData = ApiTokenProvider.get(hostName);
            }

            // Step 2: Add auth header
            // Шаг 2: Добавляем заголовок авторизации
            requestInfo.addHeader("Authorization", "Bearer " + tokenData.getAccessToken());

            // Step 3: Send
            // Шаг 3: Отправляем
            ApiResponse response = client.sendRequest(requestInfo);

            // Step 4: Handle 401 — token might have expired between check and send
            // Шаг 4: Обработка 401 — токен мог истечь между проверкой и отправкой
            if (response.statusCode() == 401) {
                log.warn("Got 401 for '{}' — refreshing and retrying", hostName);
                tokenData.forceExpire();
                refreshTokens(hostName);
                tokenData = ApiTokenProvider.get(hostName);

                // Update header with new token
                // Обновляем заголовок новым токеном
                requestInfo.addHeader("Authorization", "Bearer " + tokenData.getAccessToken());

                // Retry once
                // Повторяем один раз
                response = client.sendRequest(requestInfo);

                if (response.statusCode() == 401) {
                    log.error("Still 401 after refresh for '{}' — authentication invalid", hostName);
                    throw new TokenException("Authentication invalid for '" + hostName
                            + "' even after refresh. Re-authenticate.");
                }
            }

            // Step 5: Return
            return response;

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenException("Request with auth failed for '" + hostName + "'", e);
        }
    }

    /**
     * Simple JSON value extractor by key.
     * Простой экстрактор значения из JSON по ключу.
     *
     * Looks for pattern: "key": "value"
     * Temporary solution — replace with Jackson/Gson for production.
     */
    private String extractJsonValue(String json, String key) {
        if (json == null) return null;

        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        // Skip whitespace after colon
        int i = colonIndex + 1;
        while (i < json.length() && json.charAt(i) == ' ') i++;

        if (i >= json.length()) return null;

        // Check if value is a string (starts with quote) or a number
        if (json.charAt(i) == '"') {
            int valueStart = i + 1;
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd == -1) return null;
            return json.substring(valueStart, valueEnd);
        } else {
            // Number value (e.g. expires_in: 3600)
            int valueStart = i;
            int valueEnd = valueStart;
            while (valueEnd < json.length()
                    && json.charAt(valueEnd) != ','
                    && json.charAt(valueEnd) != '}'
                    && json.charAt(valueEnd) != ' ') {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }

    // ──── Exception ────

    public static class TokenException extends RuntimeException {
        public TokenException(String message) {
            super(message);
        }

        public TokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}