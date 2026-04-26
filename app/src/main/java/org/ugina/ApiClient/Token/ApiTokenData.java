package org.ugina.ApiClient.Token;

import java.time.Instant;

/**
 * Stores a pair of tokens: access + refresh, with expiration time.
 * Хранит пару токенов: access + refresh, со временем истечения.
 *
 * Why track expiration ourselves?
 * Зачем отслеживать время жизни самим?
 *
 *   Without it — we send a request, get 401, then refresh, then retry.
 *   That's 3 HTTP calls instead of 1.
 *
 *   Без этого — отправляем запрос, получаем 401, обновляем, повторяем.
 *   Это 3 HTTP-вызова вместо 1.
 *
 *   With expiration check — we see the token is expired BEFORE sending,
 *   refresh proactively, then send once. 2 calls instead of 3.
 *
 *   С проверкой времени — видим что токен истёк ДО отправки,
 *   обновляем заранее, отправляем один раз. 2 вызова вместо 3.
 *
 * Usage / Использование:
 *
 *   ApiTokenData data = new ApiTokenData("eyJ...", "dGh...", 3600);
 *   data.getAccessToken();   // → "eyJ..."
 *   data.isExpired();        // → false (just created)
 *   // ... 1 hour later ...
 *   data.isExpired();        // → true
 */
public class ApiTokenData {

    private String accessToken;
    private String refreshToken;

    // Moment when access token expires (UTC).
    // Момент истечения access token (UTC).
    private Instant expiresAt;

    // Buffer in seconds — refresh token slightly before it expires.
    // Буфер в секундах — обновляем токен чуть раньше, чем он истечёт.
    // Without buffer: token expires at 12:00:00, request sent at 11:59:59,
    // reaches server at 12:00:01 → 401. With 30s buffer: refresh at 11:59:30.
    private static final long EXPIRATION_BUFFER_SECONDS = 30;

    /**
     * Full constructor with known expiration.
     * Полный конструктор с известным временем жизни.
     *
     * @param accessToken  access token from server / access токен от сервера
     * @param refreshToken refresh token from server / refresh токен от сервера
     * @param expiresInSeconds how many seconds until access token expires /
     *                         через сколько секунд истечёт access токен
     */
    public ApiTokenData(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds);
    }

    /**
     * Принудительно помечает токен как expired.
     * Вызывается когда сервер вернул 401 — значит токен невалиден,
     * даже если по времени он ещё "живой".
     */
    public void forceExpire() {
        this.expiresAt = Instant.now().minusSeconds(1);
    }

    /**
     * Constructor without expiration — token never expires (until 401).
     * Конструктор без времени жизни — токен не истекает (пока не получим 401).
     *
     * Use when the API doesn't return expires_in.
     * Используй когда API не возвращает expires_in.
     */
    public ApiTokenData(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = null;  // null = unknown expiration, rely on 401
    }

    // ──── Getters ────

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    // ──── Expiration check ────

    /**
     * Checks if access token is expired (or about to expire within buffer).
     * Проверяет, истёк ли access token (или скоро истечёт в пределах буфера).
     *
     * @return true if expired or expiration unknown and should check via 401
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            // Unknown expiration — assume valid, rely on 401 from server
            // Неизвестное время жизни — считаем валидным, полагаемся на 401
            return false;
        }
        // Expired if current time + buffer > expiresAt
        // Истёк если текущее время + буфер > время истечения
        return Instant.now().plusSeconds(EXPIRATION_BUFFER_SECONDS).isAfter(expiresAt);
    }

    // ──── Update ────

    /**
     * Updates tokens after refresh. Called by ApiTokenManager.
     * Обновляет токены после refresh. Вызывается из ApiTokenManager.
     *
     * @param newAccessToken  new access token / новый access токен
     * @param newRefreshToken new refresh token (null = keep old) /
     *                        новый refresh токен (null = оставить старый)
     * @param expiresInSeconds new expiration / новое время жизни
     */
    public void update(String newAccessToken, String newRefreshToken, long expiresInSeconds) {
        this.accessToken = newAccessToken;
        if (newRefreshToken != null) {
            this.refreshToken = newRefreshToken;
        }
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds);
    }

    /**
     * Updates tokens without expiration info.
     * Обновляет токены без информации о времени жизни.
     */
    public void update(String newAccessToken, String newRefreshToken) {
        this.accessToken = newAccessToken;
        if (newRefreshToken != null) {
            this.refreshToken = newRefreshToken;
        }
        this.expiresAt = null;
    }

    @Override
    public String toString() {
        return String.format("ApiTokenData{access=%s..., refresh=%s..., expiresAt=%s, expired=%s}",
                accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) : "null",
                refreshToken != null ? refreshToken.substring(0, Math.min(10, refreshToken.length())) : "null",
                expiresAt != null ? expiresAt.toString() : "unknown",
                isExpired());
    }
}