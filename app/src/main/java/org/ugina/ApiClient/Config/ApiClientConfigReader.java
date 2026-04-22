package org.ugina.ApiClient.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads configuration from apiclient.properties file.
 * Читает конфигурацию из файла apiclient.properties.
 *
 * Location: src/main/resources/apiclient.properties
 *
 * Example config:
 *   api.baseUrl=https://jsonplaceholder.typicode.com
 *   timeout.connection=30
 *
 *   # Optional SSL
 *   ssl.keyStore=certs/client.p12
 *   ssl.keyStorePassword=keypass
 *   ssl.trustStore=certs/truststore.p12
 *   ssl.trustStorePassword=trustpass
 *
 *   # Optional named hosts for multi-host testing
 *   hosts.auth=https://auth.example.com
 *   hosts.payment=https://pay.example.com
 */
@SuppressWarnings("unchecked")
public class ApiClientConfigReader {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = ApiClientConfigReader.class.getClassLoader()
                .getResourceAsStream("apiclient.properties")) {
            if (is == null) throw new RuntimeException("❌ File apiclient.properties not found");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("❌ Error reading config", e);
        }
    }

    /**
     * Returns value by key. Throws if key is missing.
     * Возвращает значение по ключу. Бросает исключение если ключ отсутствует.
     */
    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) throw new RuntimeException("❌ Key not found: " + key);
        return value;
    }

    /**
     * Returns value by key, or defaultValue if key is missing.
     * Возвращает значение по ключу, или defaultValue если ключ отсутствует.
     *
     * Used for optional settings (SSL, timeouts with defaults).
     * Используется для опциональных настроек.
     */
    public static String getOrDefault(String key, String defaultValue) {
        String value = props.getProperty(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns int value. Throws if key is missing.
     * Возвращает int значение. Бросает исключение если ключ отсутствует.
     */
    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    /**
     * Returns int value, or defaultValue if key is missing.
     * Возвращает int значение, или defaultValue если ключ отсутствует.
     */
    public static int getIntOrDefault(String key, int defaultValue) {
        String value = props.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    /**
     * Returns boolean value. Throws if key is missing.
     * Возвращает boolean значение. Бросает исключение если ключ отсутствует.
     */
    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * Checks if key exists in config.
     * Проверяет, существует ли ключ в конфиге.
     */
    public static boolean hasKey(String key) {
        return props.getProperty(key) != null;
    }
}