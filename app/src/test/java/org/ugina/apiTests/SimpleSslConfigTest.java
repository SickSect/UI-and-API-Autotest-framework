package org.ugina.apiTests;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.ugina.ApiClient.Config.SslConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Тесты для SslConfig — проверяем что сертификаты читаются из resources,
 * SSLContext собирается, и HttpClient с ним работает.
 *
 * Сертификаты лежат в src/test/resources/certs/:
 *   TESTclient.p12     — клиентский сертификат (пароль: keypass)
 *   TESTtruststore.p12 — доверенные сертификаты (пароль: trustpass)
 *
 * Сгенерированы через keytool:
 *   keytool -genkeypair -alias client -keyalg RSA -keysize 2048
 *           -storetype PKCS12 -keystore TESTTESTclient.p12 -storepass keypass
 */

public class SimpleSslConfigTest {

    // ════════════════════════════════════════════
    //  ЗАГРУЗКА СЕРТИФИКАТОВ
    // ════════════════════════════════════════════

    @Test
    public void testLoadTrustStoreOnly() {
        SslConfig config = SslConfig.builder()
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        SSLContext sslContext = config.createSslContext();

        Assert.assertNotNull(sslContext);
        Assert.assertEquals(sslContext.getProtocol(), "TLS");
    }

    @Test
    public void testLoadKeyStoreOnly() {
        SslConfig config = SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "keypass")
                .build();

        SSLContext sslContext = config.createSslContext();

        Assert.assertNotNull(sslContext);
    }

    @Test
    public void testLoadFullMtlsConfig() {
        // Полная цепочка: клиентский сертификат + truststore с CA
        // В логах видно обе записи из TESTclient.p12:
        //   [client] — наш сертификат, подписанный CA
        //   [ca]     — сертификат CA (нужен для цепочки доверия)
        SslConfig config = SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "keypass")
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        SSLContext sslContext = config.createSslContext();

        Assert.assertNotNull(sslContext);
    }

    // ════════════════════════════════════════════
    //  СЕРТИФИКАТЫ ПРИКРЕПЛЯЮТСЯ К HttpClient
    // ════════════════════════════════════════════

    @Test
    public void testSslContextAttachesToHttpClient() {
        // Проверяем что SSLContext подключается к HttpClient без ошибок
        SslConfig config = SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "keypass")
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        SSLContext sslContext = config.createSslContext();

        // Создаём HttpClient с нашим SSLContext
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Проверяем что клиент использует наш SSLContext, а не дефолтный
        Assert.assertSame(client.sslContext(), sslContext,
                "HttpClient должен использовать наш SSLContext");
    }

    @Test
    public void testSslParametersAvailable() {
        // Проверяем что SSL-параметры доступны — значит сертификаты прикрепились
        SslConfig config = SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "keypass")
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        SSLContext sslContext = config.createSslContext();
        SSLParameters params = sslContext.getDefaultSSLParameters();

        // Должны быть доступны протоколы TLS
        Assert.assertNotNull(params.getProtocols());
        Assert.assertTrue(params.getProtocols().length > 0,
                "Должен быть хотя бы один TLS-протокол");

        // Должны быть доступны cipher suites (алгоритмы шифрования)
        Assert.assertNotNull(params.getCipherSuites());
        Assert.assertTrue(params.getCipherSuites().length > 0,
                "Должен быть хотя бы один cipher suite");
    }

    // ════════════════════════════════════════════
    //  HTTPS-ЗАПРОС С СЕРТИФИКАТАМИ
    // ════════════════════════════════════════════

    @Test
    public void testHttpsRequestWithTrustStore() throws Exception {
        SslConfig config = SslConfig.builder()
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(config.createSslContext())
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://jsonplaceholder.typicode.com/users/1"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertTrue(response.body().contains("Leanne Graham"));
    }

    @Test
    public void testHttpsRequestWithFullMtls() throws Exception {
        // Полный mTLS-конфиг: KeyStore + TrustStore.
        // jsonplaceholder не требует клиентский сертификат,
        // но мы проверяем что наличие KeyStore не ломает запрос —
        // клиент просто не предъявит сертификат если сервер не просит.
        SslConfig config = SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "keypass")
                .trustStore("certs/TESTtruststore.p12", "trustpass")
                .build();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(config.createSslContext())
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://jsonplaceholder.typicode.com/posts/1"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(response.statusCode(), 200);
    }

    // ════════════════════════════════════════════
    //  НЕГАТИВНЫЕ КЕЙСЫ
    // ════════════════════════════════════════════

    @Test(expectedExceptions = SslConfig.SslConfigException.class)
    public void testWrongKeyStorePassword() {
        SslConfig.builder()
                .keyStore("certs/TESTclient.p12", "WRONG")
                .build()
                .createSslContext();
    }

    @Test(expectedExceptions = SslConfig.SslConfigException.class)
    public void testWrongTrustStorePassword() {
        SslConfig.builder()
                .trustStore("certs/TESTtruststore.p12", "WRONG")
                .build()
                .createSslContext();
    }

    @Test(expectedExceptions = SslConfig.SslConfigException.class)
    public void testFileNotFound() {
        SslConfig.builder()
                .keyStore("certs/does-not-exist.p12", "pass")
                .build()
                .createSslContext();
    }

    @Test(expectedExceptions = SslConfig.SslConfigException.class)
    public void testNothingConfigured() {
        SslConfig.builder().build();
    }
}