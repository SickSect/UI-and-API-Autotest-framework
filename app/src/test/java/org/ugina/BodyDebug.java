package org.ugina.apiTests;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class BodyDebug {
    public static void main(String[] args) throws Exception {
        // Мини-сервер: печатает что получил
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/test", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            System.out.println("SERVER GOT: [" + new String(body) + "] length=" + body.length);

            String resp = "ok";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        // Клиент: отправляет POST с body
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/test"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"test\"}", StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("CLIENT GOT: status=" + response.statusCode() + " body=" + response.body());

        server.stop(0);
    }
}