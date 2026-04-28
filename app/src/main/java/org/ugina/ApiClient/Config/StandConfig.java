package org.ugina.ApiClient.Config;

import org.ugina.ApiClient.Client.ApiClientProvider;
import org.ugina.ApiClient.Db.IDbClient;
import org.ugina.ApiClient.utils.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads stand-specific configuration (hosts, DB, credentials) from properties files.
 * Загружает конфигурацию стенда (хосты, БД, креды) из properties-файлов.
 *
 * How it works / Как работает:
 *
 *   1. You pass stand name via CLI: ./gradlew apiTest -Dstand=prod
 *   2. StandConfig loads file: stands/prod.properties from classpath
 *   3. All hosts and DB connections become available via get() methods
 *   4. Hosts are auto-registered in ApiClientProvider
 *   5. DB connections are created on demand via getDb()
 *
 * File location: src/main/resources/stands/{stand}.properties
 *
 * File format:
 *   hosts.main=https://api.example.com          → ApiClientProvider.get("main")
 *   hosts.auth=https://auth.example.com          → ApiClientProvider.get("auth")
 *   db.main.url=jdbc:postgresql://host:5432/db   → StandConfig.getDb("main")
 *   db.main.username=user                        → used in JDBC connection
 *   tuz.login=autotest_user                      → StandConfig.get("tuz.login")
 *
 * Usage:
 *
 *   // In @BeforeSuite — once for entire test run
 *   StandConfig.init();  // reads -Dstand=..., loads file, registers hosts
 *
 *   // In tests
 *   ApiClientProvider.get("main").sendRequest(request);    // host from config
 *   IDbClient db = StandConfig.getDb("main");              // DB from config
 *   String login = StandConfig.get("tuz.login");           // any property
 *
 * CLI:
 *   ./gradlew apiTest -Dstand=dev       → loads stands/dev.properties
 *   ./gradlew apiTest -Dstand=staging   → loads stands/staging.properties
 *   ./gradlew apiTest -Dstand=prod      → loads stands/prod.properties
 *   ./gradlew apiTest                   → loads stands/dev.properties (default)
 */
public class StandConfig {
    private static final Log log = Log.forClass(StandConfig.class);
    // Loaded properties from stands/{stand}.properties
    private static final Properties props = new Properties();
    // DB clients cache — one per name, created on first getDb() call
    private static final ConcurrentHashMap<String, IDbClient> dbClients = new ConcurrentHashMap<>();
    // Current stand name
    private static String currentStand;
    // Whether init() was called
    private static volatile boolean initialized = false;

    private StandConfig() {}

    // ──── Initialization ────

    /**
     * Loads configuration for the current stand.
     * Загружает конфигурацию для текущего стенда.
     *
     * Reads -Dstand from CLI (default: "dev").
     * Loads stands/{stand}.properties from classpath.
     * Auto-registers all hosts.* entries in ApiClientProvider.
     *
     * Call once in @BeforeSuite.
     */
    public static synchronized void init() {
        if (initialized) {
            log.debug("StandConfig already initialized for '{}'", currentStand);
            return;
        }

        currentStand = System.getProperty("stand", "dev");
        String fileName = "stands/" + currentStand + ".properties";

        log.info("══════ STAND CONFIG ══════");
        log.info("Stand: {}", currentStand);
        log.info("Loading: {}", fileName);

        // Load properties file
        try (InputStream is = StandConfig.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new RuntimeException("Stand config not found: " + fileName
                        + "\nExpected location: src/main/resources/" + fileName
                        + "\nAvailable stands: create files in src/main/resources/stands/");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load stand config: " + fileName, e);
        }

        // Auto-register hosts in ApiClientProvider
        registerHosts();

        // Log loaded config
        // logConfig();

        initialized = true;
        log.info("══════════════════════════");
    }

    private static void registerHosts(){
        props.stringPropertyNames().stream()
                .filter(key -> key.startsWith("hosts."))
                .forEach( key -> {
                    String hostName = key.substring("hosts.".length());
                    String hostUrl = props.getProperty(key);
                    if (!ApiClientProvider.has(hostName))
                        ApiClientProvider.register(hostName, hostUrl);
                    log.info("Host: {} → {}", hostName, hostUrl);
                });
    }

    /**
     * Logs loaded configuration (without secrets).
     */
    private static void logConfig() {
        // Hosts
        props.stringPropertyNames().stream()
                .filter(key -> key.startsWith("hosts."))
                .sorted()
                .forEach(key -> log.info("  {} = {}", key, props.getProperty(key)));

        // DB URLs (without passwords)
        props.stringPropertyNames().stream()
                .filter(key -> key.startsWith("db.") && key.endsWith(".url"))
                .sorted()
                .forEach(key -> log.info("  {} = {}", key, props.getProperty(key)));

        // Other properties
        props.stringPropertyNames().stream()
                .filter(key -> !key.startsWith("hosts.") && !key.startsWith("db."))
                .sorted()
                .forEach(key -> log.info("  {} = {}", key, props.getProperty(key)));
    }

    /**
     * Ensures init() was called.
     */
    private static void ensureInitialized() {
        if (!initialized) {
            init(); // auto-init with default stand
        }
    }
}
