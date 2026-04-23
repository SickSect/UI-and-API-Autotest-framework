package org.ugina.apiTests;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.Token.ApiTokenManager;
import org.ugina.MOCK.MockAuthServer;

import java.io.IOException;

/**
 * Tests for token lifecycle: login, access, expiration, refresh, retry.
 * Тесты жизненного цикла токенов: логин, доступ, истечение, обновление, повтор.
 *
 * Uses MockAuthServer — embedded HTTP server in the test process.
 * Использует MockAuthServer — встроенный HTTP-сервер в процессе теста.
 * No external services needed.
 * Никаких внешних сервисов не нужно.
 */

public class SimpleTokenTest {
    private MockAuthServer authServer;
    private ApiTokenManager tokenManager;
    private static final String HOST = "mock-auth";

    @BeforeClass
    public void setup() throws IOException {
        // MOCK PART ONLY FOR DEMO
        authServer = new MockAuthServer(0);
        authServer.start();
        //
        ApiClientProvider.register(HOST, authServer.getBaseUrl());
        tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
    }

    @AfterClass
    public void teardown() throws IOException {
        authServer.stop();
        ApiClientProvider.reset(HOST);
    }

    // ════════════════════════════════════════════
    //  AUTHENTICATION
    // ════════════════════════════════════════════

    @Test(priority = 1)
    public void testSuccessfulLogin() {
        // Valid credentials → should get tokens
        // Валидные credentials → должны получить токены
        tokenManager.authenticate(HOST, "admin", "password123");

        // Tokens should now be stored
        Assert.assertTrue(
                org.ugina.ApiClient.Token.ApiTokenProvider.has(HOST),
                "Tokens should be registered after login");
    }

    @Test(priority = 2, expectedExceptions = ApiTokenManager.TokenException.class)
    public void testFailedLogin() {
        // Wrong credentials → should throw
        // Неверные credentials → должно выбросить исключение
        tokenManager.authenticate(HOST, "wrong", "wrong");
    }

    // ════════════════════════════════════════════
    //  PROTECTED ENDPOINT ACCESS
    // ════════════════════════════════════════════

    @Test(priority = 3, dependsOnMethods = "testSuccessfulLogin")
    public void testAccessProtectedEndpoint() {
        // With valid token → 200
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200)
                .assertBodyContains("\"message\"")
                .assertBodyContains("success");
    }

    @Test(priority = 4, dependsOnMethods = "testSuccessfulLogin")
    public void testAccessPublicEndpoint() {
        // Public endpoint works without tokens too
        // Публичный эндпоинт работает и без токенов
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/public");

        // Use client directly, no auth
        try {
            ApiClientProvider.get(HOST).sendRequest(request)
                    .assertStatus(200)
                    .assertBodyContains("public data");
        } catch (Exception e) {
            Assert.fail("Public endpoint should work without auth: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════
    //  TOKEN EXPIRATION + AUTO-REFRESH
    // ════════════════════════════════════════════

    @Test(priority = 5, dependsOnMethods = "testSuccessfulLogin")
    public void testAutoRefreshOn401() {
        // Invalidate access token on server → next request gets 401 → auto-refresh → retry
        // Инвалидируем access token на сервере → следующий запрос получит 401 → авто-обновление → повтор
        authServer.invalidateAccessToken();

        int refreshesBefore = authServer.getRefreshCount();

        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        // sendWithAuth should handle 401 → refresh → retry automatically
        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200)
                .assertBodyContains("success");

        // Verify refresh was called exactly once
        // Проверяем что refresh вызвался ровно один раз
        Assert.assertEquals(authServer.getRefreshCount(), refreshesBefore + 1,
                "Refresh should be called exactly once on 401");
    }

    // ════════════════════════════════════════════
    //  MULTIPLE REQUESTS AFTER REFRESH
    // ════════════════════════════════════════════

    @Test(priority = 6, dependsOnMethods = "testAutoRefreshOn401")
    public void testSubsequentRequestsAfterRefresh() {
        // After refresh, new token should work for subsequent requests
        // После обновления, новый токен должен работать для последующих запросов
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        int refreshesBefore = authServer.getRefreshCount();

        // Should NOT trigger another refresh
        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200);

        Assert.assertEquals(authServer.getRefreshCount(), refreshesBefore,
                "No additional refresh should happen with valid token");
    }

    // ════════════════════════════════════════════
    //  RE-AUTHENTICATION
    // ════════════════════════════════════════════

    @Test(priority = 7)
    public void testReauthenticate() {
        // Login again with fresh credentials → old tokens replaced
        // Логинимся заново → старые токены заменяются
        tokenManager.authenticate(HOST, "admin", "password123");

        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200);
    }
}
