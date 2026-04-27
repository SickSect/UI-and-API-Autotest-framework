package org.ugina.apiTests;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.Token.ApiTokenManager;
import org.ugina.ApiClient.Token.ApiTokenProvider;
import org.ugina.MOCK.MockAuthServer;

/**
 * End-to-end test — full framework chain in a single scenario.
 * E2E тест — полная цепочка фреймворка в одном сценарии.
 *
 * This test demonstrates the entire lifecycle:
 *   1. Start mock server
 *   2. Register host
 *   3. Access public endpoint (no auth)
 *   4. Authenticate (login)
 *   5. Access protected endpoint (with token)
 *   6. Token gets invalidated (simulated expiration)
 *   7. Auto-refresh + retry on 401
 *   8. Multiple requests with refreshed token
 *   9. Re-authenticate with new credentials
 *   10. Cleanup
 *
 * All steps are sequential — each depends on the previous.
 * Uses parallel="false" in testing.xml.
 */
public class SimpleEndToEndTest {

    private MockAuthServer mockServer;
    private ApiTokenManager tokenManager;
    private static final String HOST = "e2e-test";

    @BeforeClass
    public void setUp() throws Exception {
        // Step 0: Start mock server on random port
        // Шаг 0: Запускаем мок-сервер на случайном порту
        mockServer = new MockAuthServer(0);
        mockServer.start();

        // Register host in ApiClientProvider
        // Регистрируем хост в ApiClientProvider
        ApiClientProvider.register(HOST, mockServer.getBaseUrl());

        // Create token manager with auth endpoints
        // Создаём менеджер токенов с эндпоинтами авторизации
        tokenManager = new ApiTokenManager("/auth/login", "/auth/refresh");
    }

    @AfterClass
    public void tearDown() {
        mockServer.stop();
        ApiClientProvider.reset(HOST);
        ApiTokenProvider.reset(HOST);
    }

    // ──────────────────────────────────────────────────────
    //  STEP 1: Public endpoint works without authentication
    // ──────────────────────────────────────────────────────

    @Test(priority = 1)
    public void step1_publicEndpointWithoutAuth() throws Exception {
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/public");

        // Direct call — no tokens needed
        // Прямой вызов — токены не нужны
        ApiClientProvider.get(HOST).sendRequest(request)
                .assertStatus(200)
                .assertBodyContains("public data");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 2: Protected endpoint rejects without token
    // ──────────────────────────────────────────────────────

    @Test(priority = 2)
    public void step2_protectedEndpointRejectsWithoutToken() throws Exception {
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        // No Authorization header → 401
        ApiClientProvider.get(HOST).sendRequest(request)
                .assertStatus(401)
                .assertBodyContains("missing_token");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 3: Authenticate — get access + refresh tokens
    // ──────────────────────────────────────────────────────

    @Test(priority = 3)
    public void step3_authenticate() {
        tokenManager.authenticate(HOST, "admin", "password123");

        // Verify tokens are stored
        // Проверяем что токены сохранены
        Assert.assertTrue(ApiTokenProvider.has(HOST),
                "Tokens should be registered after login");
        Assert.assertNotNull(ApiTokenProvider.get(HOST).getAccessToken(),
                "Access token should not be null");
        Assert.assertNotNull(ApiTokenProvider.get(HOST).getRefreshToken(),
                "Refresh token should not be null");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 4: Access protected endpoint with valid token
    // ──────────────────────────────────────────────────────

    @Test(priority = 4, dependsOnMethods = "step3_authenticate")
    public void step4_accessProtectedWithToken() {
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        // sendWithAuth adds Authorization header automatically
        // sendWithAuth добавляет заголовок Authorization автоматически
        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200)
                .assertBodyContains("success")
                .assertBodyContains("admin");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 5: Invalidate token → 401 → auto-refresh → retry → success
    // ──────────────────────────────────────────────────────

    @Test(priority = 5, dependsOnMethods = "step4_accessProtectedWithToken")
    public void step5_autoRefreshOnExpiredToken() {
        // Simulate token expiration on server
        // Симулируем истечение токена на сервере
        mockServer.invalidateAccessToken();
        int refreshesBefore = mockServer.getRefreshCount();

        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        // sendWithAuth should:
        //   1. Send request → get 401
        //   2. forceExpire() → mark token as expired
        //   3. refreshTokens() → get new token pair
        //   4. Retry request → get 200
        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200)
                .assertBodyContains("success");

        // Verify refresh was called exactly once
        // Проверяем что refresh вызвался ровно один раз
        Assert.assertEquals(mockServer.getRefreshCount(), refreshesBefore + 1,
                "Refresh should be called exactly once");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 6: Subsequent requests work with refreshed token
    // ──────────────────────────────────────────────────────

    @Test(priority = 6, dependsOnMethods = "step5_autoRefreshOnExpiredToken")
    public void step6_subsequentRequestsAfterRefresh() {
        int refreshesBefore = mockServer.getRefreshCount();

        // Multiple requests — all should succeed without additional refresh
        // Несколько запросов — все должны пройти без дополнительного refresh
        for (int i = 0; i < 3; i++) {
            RequestInfo request = new RequestInfo();
            request.setMethod("GET");
            request.setPath("/api/protected");

            tokenManager.sendWithAuth(HOST, request)
                    .assertStatus(200);
        }

        Assert.assertEquals(mockServer.getRefreshCount(), refreshesBefore,
                "No additional refresh should happen with valid token");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 7: Re-authenticate replaces old tokens
    // ──────────────────────────────────────────────────────

    @Test(priority = 7, dependsOnMethods = "step6_subsequentRequestsAfterRefresh")
    public void step7_reauthenticateReplacesTokens() {
        String oldAccessToken = ApiTokenProvider.get(HOST).getAccessToken();

        // Login again
        // Логинимся заново
        tokenManager.authenticate(HOST, "admin", "password123");

        String newAccessToken = ApiTokenProvider.get(HOST).getAccessToken();

        // New token should be different from old
        // Новый токен должен отличаться от старого
        Assert.assertNotEquals(newAccessToken, oldAccessToken,
                "Re-authentication should produce a new token");

        // New token should work
        // Новый токен должен работать
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/protected");

        tokenManager.sendWithAuth(HOST, request)
                .assertStatus(200)
                .assertBodyContains("success");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 8: Wrong credentials are rejected
    // ──────────────────────────────────────────────────────

    @Test(priority = 8, expectedExceptions = ApiTokenManager.TokenException.class)
    public void step8_wrongCredentialsRejected() {
        tokenManager.authenticate(HOST, "hacker", "wrong");
    }

    // ──────────────────────────────────────────────────────
    //  STEP 9: Public endpoint still works after all operations
    // ──────────────────────────────────────────────────────

    @Test(priority = 9)
    public void step9_publicEndpointStillWorks() throws Exception {
        RequestInfo request = new RequestInfo();
        request.setMethod("GET");
        request.setPath("/api/public");

        ApiClientProvider.get(HOST).sendRequest(request)
                .assertStatus(200)
                .assertBodyContains("public data");
    }
}