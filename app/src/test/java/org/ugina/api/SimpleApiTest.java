package org.ugina.api;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Client.ApiRequestClient;
import org.ugina.ApiClient.Data.JsonRequestBody;
import org.ugina.ApiClient.Data.RequestInfo;
import org.ugina.ApiClient.Data.XmlRequestBody;

import java.net.http.HttpResponse;

/**
 * Тесты для шага 2: один клиент, разные форматы тела.
 *
 * Обрати внимание: apiClient.post() — один метод, но принимает
 * и JsonBody, и XmlBody. Клиент не знает о формате — он просто
 * берёт content() и contentType() из RequestBody.
 */
public class SimpleApiTest {

    private ApiRequestClient apiClient;


    @BeforeClass
    public void setUp() {
        apiClient = new ApiRequestClient("https://jsonplaceholder.typicode.com");
    }

    // ──── GET ────

    @Test
    public void testGetPosts() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/posts/1");
        HttpResponse<String> response = apiClient.sendRequest(requestInfo);
    }

    // ──── Get с JSON ────

    @Test
    public void testGetWithJson() throws Exception {
        // JsonBody оборачивает строку и выставляет Content-Type: application/json
        JsonRequestBody body = new JsonRequestBody("""
                {
                    "name": "Test User",
                    "email": "test@example.com"
                }
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("GET");
        requestInfo.setPath("/users");
        requestInfo.setBody(body);
        HttpResponse<String> response = apiClient.sendRequest(requestInfo);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertTrue(response.body().contains("\"id\""));

        System.out.println("POST /users (JSON) → " + response.statusCode());
        System.out.println("Response: " + response.body());
    }

    // ──── POST с XML ────

    @Test
    public void testPostWithXml() throws Exception {
        // XmlBody — тот же интерфейс, но Content-Type: application/xml
        // jsonplaceholder не понимает XML, но нам важно что запрос уходит корректно
        XmlRequestBody body = new XmlRequestBody("""
                <?xml version="1.0" encoding="UTF-8"?>
                <user>
                    <name>Test User</name>
                    <email>test@example.com</email>
                </user>
                """);

        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setMethod("POST");
        requestInfo.setPath("/users");
        requestInfo.setBody(body);
        HttpResponse<String> response = apiClient.sendRequest(requestInfo);

        response = apiClient.sendRequest(requestInfo);

        // jsonplaceholder вернёт 201 даже для XML — ему всё равно на Content-Type
        // В реальном API ты увидишь разницу в обработке
        Assert.assertEquals(response.statusCode(), 201);

        System.out.println("POST /users (XML) → " + response.statusCode());
        System.out.println("Response: " + response.body());
    }

    // ──── POST с XML ────

/*    @Test
    public void testPostWithXml() throws Exception {
        // XmlBody — тот же интерфейс, но Content-Type: application/xml
        // jsonplaceholder не понимает XML, но нам важно что запрос уходит корректно
        XmlRequestBody body = new XmlRequestBody("""
                <?xml version="1.0" encoding="UTF-8"?>
                <user>
                    <name>Test User</name>
                    <email>test@example.com</email>
                </user>
                """);

        HttpResponse<String> response = apiClient.post("/users", body);

        // jsonplaceholder вернёт 201 даже для XML — ему всё равно на Content-Type
        // В реальном API ты увидишь разницу в обработке
        Assert.assertEquals(response.statusCode(), 201);

        System.out.println("POST /users (XML) → " + response.statusCode());
        System.out.println("Response: " + response.body());
    }*/
}
