package org.ugina.apiTests;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Client.ApiRequestClient;
import org.ugina.ApiClient.Data.JsonRequestBody;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.Data.XmlRequestBody;

public class SimpleApiTest {

    private ApiRequestClient apiClient;

    @BeforeClass
    public void setUp() {
        apiClient = ApiClientProvider.get("test");
    }

    // ──── GET ────

    @Test
    public void testGetPosts() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/posts/1");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertBodyContains("\"title\"");
    }

    // ════════════════════════════════════════════
    //  БЕЗ ТЕЛА (GET, DELETE)
    // ════════════════════════════════════════════

    @Test
    public void testGetWithoutBody() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/users");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertBodyContains("\"email\"")
                .assertBodyNotEmpty();
    }

    @Test
    public void testGetSingleResource() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/users/1");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertBodyContains("Leanne Graham");
    }

    @Test
    public void testDeleteWithoutBody() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("DELETE");
        requestInfo.setPath("/posts/1");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200);
    }

    // ════════════════════════════════════════════
    //  С QUERY-ПАРАМЕТРАМИ, БЕЗ ТЕЛА
    // ════════════════════════════════════════════

    @Test
    public void testGetWithSingleQueryParam() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/comments");
        requestInfo.addQueryParam("postId", "1");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertBodyContains("\"postId\": 1");
    }

    @Test
    public void testGetWithMultipleQueryParams() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/posts");
        requestInfo.addQueryParam("userId", "1");
        requestInfo.addQueryParam("id", "1");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertBodyContains("\"userId\": 1")
                .assertBodyContains("\"id\": 1");
    }

    // ════════════════════════════════════════════
    //  QUERY-ПАРАМЕТРЫ + JSON ТЕЛО
    // ════════════════════════════════════════════

    @Test
    public void testPostWithJsonAndQueryParams() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "title": "Test Post",
                    "body": "Content with query params",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/posts");
        requestInfo.addQueryParam("source", "test");
        requestInfo.addQueryParam("version", "2");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(201)
                .assertBodyContains("\"id\"");
    }

    @Test
    public void testPutWithJsonAndQueryParams() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "id": 1,
                    "title": "Updated Title",
                    "body": "Updated body",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("PUT");
        requestInfo.setPath("/posts/1");
        requestInfo.addQueryParam("force", "true");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(200);
    }

    // ════════════════════════════════════════════
    //  QUERY-ПАРАМЕТРЫ + XML ТЕЛО
    // ════════════════════════════════════════════

    @Test
    public void testPostWithXmlAndQueryParams() throws Exception {
        XmlRequestBody body = new XmlRequestBody("""
                <?xml version="1.0" encoding="UTF-8"?>
                <post>
                    <title>XML Post</title>
                    <body>Created via XML with query params</body>
                    <userId>1</userId>
                </post>
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/posts");
        requestInfo.addQueryParam("format", "xml");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(201);
    }

    // ════════════════════════════════════════════
    //  ВСЕ HTTP-МЕТОДЫ С JSON
    // ════════════════════════════════════════════

    @Test
    public void testPostWithJson() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "title": "New Post",
                    "body": "Post content",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/posts");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(201)
                .assertBodyContains("\"id\"")
                .assertBodyNotEmpty();
    }

    @Test
    public void testPutWithJson() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "id": 1,
                    "title": "Fully Replaced",
                    "body": "New body",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("PUT");
        requestInfo.setPath("/posts/1");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(200);
    }

    @Test
    public void testPatchWithJson() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "title": "Partially Updated"
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("PATCH");
        requestInfo.setPath("/posts/1");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(200);
    }

    // ════════════════════════════════════════════
    //  С КАСТОМНЫМИ ЗАГОЛОВКАМИ
    // ════════════════════════════════════════════

    @Test
    public void testGetWithCustomHeaders() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/users/1");
        requestInfo.addHeader("Accept", "application/json");
        requestInfo.addHeader("X-Custom-Header", "test-value");
        requestInfo.addHeader("Cache-Control", "no-cache");

        apiClient.sendRequest(requestInfo)
                .assertStatus(200)
                .assertHeader("content-type", "application/json");
    }

    @Test
    public void testPostWithHeadersAndJson() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "title": "Post with headers",
                    "body": "Testing custom headers",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/posts");
        requestInfo.addHeader("Accept", "application/json");
        requestInfo.addHeader("X-Request-Id", "abc-123");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(201)
                .assertBodyContains("\"id\"");
    }

    // ════════════════════════════════════════════
    //  ВСЁ ВМЕСТЕ: ЗАГОЛОВКИ + QUERY + ТЕЛО
    // ════════════════════════════════════════════

    @Test
    public void testFullRequestWithEverything() throws Exception {
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "title": "Full combo",
                    "body": "Headers + query + JSON body",
                    "userId": 1
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/posts");
        requestInfo.addHeader("Accept", "application/json");
        requestInfo.addHeader("X-Request-Id", "full-combo-001");
        requestInfo.addQueryParam("debug", "true");
        requestInfo.addQueryParam("source", "automated-test");
        requestInfo.setBody(body);

        apiClient.sendRequest(requestInfo)
                .assertStatus(201)
                .assertBodyContains("\"id\"")
                .assertBodyNotEmpty()
                .assertDurationLessThan(10000);
    }

    // ════════════════════════════════════════════
    //  НЕГАТИВНЫЕ КЕЙСЫ
    // ════════════════════════════════════════════

    @Test
    public void testGetNonExistentResource() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/users/99999");

        apiClient.sendRequest(requestInfo)
                .assertStatus(404);
    }

    @Test
    public void testGetNonExistentEndpoint() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/this-does-not-exist");

        apiClient.sendRequest(requestInfo)
                .assertStatus(404);
    }
}