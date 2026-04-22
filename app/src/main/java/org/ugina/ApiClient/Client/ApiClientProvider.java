package org.ugina.ApiClient.Client;

import org.ugina.ApiClient.Config.ApiClientConfigReader;
import org.ugina.ApiClient.Config.SslConfig;
import org.ugina.ApiClient.utils.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ApiRequestClient instances — one per host.
 * Управляет экземплярами ApiRequestClient — один на каждый хост.
 *
 * Supports two modes:
 * Поддерживает два режима:
 *
 *   1. Single host (default) — one client for the whole project:
 *      Один хост (по умолчанию) — один клиент на весь проект:
 *
 *        ApiClientProvider.get().sendRequest(request);
 *        // baseUrl читается из конфига: api.baseUrl=https://api.example.com
 *
 *   2. Multiple named hosts — for testing across different APIs in parallel:
 *      Несколько именованных хостов — для параллельного тестирования разных API:
 *
 *        // In config (apiclient.properties):
 *        hosts.auth=https://auth.example.com
 *        hosts.payment=https://pay.example.com
 *
 *        // In tests:
 *        ApiClientProvider.get("auth").sendRequest(loginRequest);     // → auth host
 *        ApiClientProvider.get("payment").sendRequest(payRequest);    // → payment host
 *
 * Thread safety / Потокобезопасность:
 *   ConcurrentHashMap — потокобезопасная Map. Несколько потоков могут
 *   одновременно читать и писать без synchronized.
 *   computeIfAbsent() — атомарно проверяет "есть ли значение?" и создаёт
 *   если нет. Два потока не создадут два клиента для одного ключа.
 */
public class ApiClientProvider {

    private static final Log log = Log.forClass(ApiClientProvider.class);
    private static final String DEFAULT_KEY = "_default_";
    private static final ConcurrentHashMap<String, ApiRequestClient> clients = new ConcurrentHashMap<>();

    private ApiClientProvider() {
    }

    /**
     * Explicitly sets the default host URL.
     * Явно задаёт URL основного хоста.
     */
    public static void init(String baseUrl) {
        ApiRequestClient client = new ApiRequestClient(baseUrl);
        clients.put(DEFAULT_KEY, client);
        log.info("Default client initialized | baseUrl={}", baseUrl);
    }

    /**
     * Explicitly sets the default host URL with SSL.
     * Явно задаёт URL основного хоста с SSL.
     */
    public static void init(String baseUrl, SslConfig sslConfig) {
        ApiRequestClient client = new ApiRequestClient(baseUrl, sslConfig);
        clients.put(DEFAULT_KEY, client);
        log.info("Default client initialized | baseUrl={} | SSL=enabled", baseUrl);
    }

    // ──── Named hosts ────

    /**
     * Returns a client by name. Creates lazily on first call.
     * Возвращает клиент по имени. Создаёт при первом вызове.
     *
     * How it resolves the URL / Как определяет URL:
     *
     *   get("auth") → looks for key "hosts.auth" in config
     *                  ищет ключ "hosts.auth" в конфиге
     *
     *   get() → looks for key "api.baseUrl" in config
     *           ищет ключ "api.baseUrl" в конфиге
     *
     * computeIfAbsent() — atomic operation from ConcurrentHashMap:
     *   1. Checks if key exists in the map
     *   2. If yes — returns existing client (no locking, fast)
     *   3. If no — calls createClient(), puts result in map, returns it
     *   All in one atomic step — no race conditions.
     *
     * computeIfAbsent() — атомарная операция ConcurrentHashMap:
     *   1. Проверяет, есть ли ключ в map
     *   2. Если да — возвращает существующий клиент (без блокировки)
     *   3. Если нет — вызывает createClient(), кладёт в map, возвращает
     *   Всё за одну атомарную операцию — нет гонок между потоками.
     *
     * @param name host name as registered in config / имя хоста из конфига
     */
    public static ApiRequestClient get(String name) {
        return clients.computeIfAbsent(name, ApiClientProvider::createClient);
    }

    /**
     * Explicitly registers a named host.
     * Явно регистрирует именованный хост.
     *
     *   ApiClientProvider.register("auth", "https://auth.example.com");
     *   ApiClientProvider.get("auth").sendRequest(request);
     */
    public static void register(String name, String baseUrl) {
        ApiRequestClient client = new ApiRequestClient(baseUrl);
        clients.put(name, client);
        log.info("Client registered | name={} | baseUrl={}", name, baseUrl);
    }

    /**
     * Explicitly registers a named host with SSL.
     * Явно регистрирует именованный хост с SSL.
     */
    public static void register(String name, String baseUrl, SslConfig sslConfig) {
        ApiRequestClient client = new ApiRequestClient(baseUrl, sslConfig);
        clients.put(name, client);
        log.info("Client registered | name={} | baseUrl={} | SSL=enabled", name, baseUrl);
    }

    // ──── Reset ────

    /**
     * Removes all clients. Next get() calls will recreate from config.
     * Удаляет все клиенты. Следующие вызовы get() пересоздадут из конфига.
     */
    public static void resetAll() {
        clients.clear();
        log.info("All clients reset");
    }

    /**
     * Removes a specific named client.
     * Удаляет конкретный именованный клиент.
     */
    public static void reset(String name) {
        clients.remove(name);
        log.info("Client reset | name={}", name);
    }

    // ──── Internal ────

    /**
     * Creates a client from config by name.
     * Создаёт клиент из конфига по имени.
     *
     * Mapping / Маппинг:
     *   "_default_" → reads "api.baseUrl" from config
     *   "auth"      → reads "hosts.auth" from config
     *   "payment"   → reads "hosts.payment" from config
     */
    private static ApiRequestClient createClient(String name) {
        String baseUrl;

        if (DEFAULT_KEY.equals(name)) {
            // Default host — main API
            baseUrl = ApiClientConfigReader.get("api.baseUrl");
            log.info("Creating default client from config | baseUrl={}", baseUrl);
        } else {
            // Named host — look up "hosts.<name>"
            String configKey = "hosts." + name;
            baseUrl = ApiClientConfigReader.get(configKey);
            log.info("Creating named client from config | name={} | key={} | baseUrl={}",
                    name, configKey, baseUrl);
        }

        // Check for SSL config
        // Проверяем наличие SSL в конфиге
        String keyStorePath = ApiClientConfigReader.getOrDefault("ssl.keyStore", null);
        String trustStorePath = ApiClientConfigReader.getOrDefault("ssl.trustStore", null);

        if (keyStorePath != null || trustStorePath != null) {
            SslConfig.Builder sslBuilder = SslConfig.builder();

            if (keyStorePath != null) {
                sslBuilder.keyStore(keyStorePath, ApiClientConfigReader.get("ssl.keyStorePassword"));
            }
            if (trustStorePath != null) {
                sslBuilder.trustStore(trustStorePath, ApiClientConfigReader.get("ssl.trustStorePassword"));
            }

            return new ApiRequestClient(baseUrl, sslBuilder.build());
        }

        return new ApiRequestClient(baseUrl);
    }
}