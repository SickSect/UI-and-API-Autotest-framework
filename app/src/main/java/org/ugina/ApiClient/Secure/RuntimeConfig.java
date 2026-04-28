package org.ugina.ApiClient.Secure;

import org.ugina.ApiClient.utils.Log;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and stores all runtime parameters passed via -D flags and environment variables.
 * Собирает и хранит все рантайм-параметры, переданные через -D флаги и переменные окружения.
 *
 * This class is a STORAGE — it collects parameters and provides them to other classes.
 * Этот класс — ХРАНИЛИЩЕ — он собирает параметры и предоставляет их другим классам.
 *
 * What goes where:
 *   RuntimeConfig  → -D parameters (stand, threads, flags) + secrets (env vars)
 *   StandConfig    → stand-specific data (hosts, DB URLs) from properties files
 *
 * How it works:
 *   1. On first access — collects ALL -D parameters from System.getProperties()
 *   2. Stores them in ConcurrentHashMap (thread-safe)
 *   3. Other classes read via RuntimeConfig.get("key")
 *   4. Secrets read via RuntimeConfig.getSecret("ENV_KEY") from environment
 *
 * The framework user configures their project by:
 *   1. Passing -D flags in CLI: ./gradlew test -Dstand=prod -Dthreads=4 -Dretry=3
 *   2. Setting env vars for secrets: export DB_PASSWORD=secret
 *   3. Reading values in their code: RuntimeConfig.get("threads", "1")
 *
 * Built-in parameters (used by the framework itself):
 *   -Dstand          → which stand to use (default: "dev"), read by StandConfig
 *   -Dtest.mode      → local/cloud for UI tests (default: "local")
 *
 * Custom parameters (defined by the user for their project):
 *   -Dthreads=4      → RuntimeConfig.get("threads")
 *   -Dretry=3        → RuntimeConfig.get("retry")
 *   -Denv=qa         → RuntimeConfig.get("env")
 *   -Dany.key=value  → RuntimeConfig.get("any.key")
 *
 * Usage:
 *
 *   // Read a parameter (returns null if not passed)
 *   String value = RuntimeConfig.get("my.param");
 *
 *   // Read with default (returns "4" if -Dthreads was not passed)
 *   String threads = RuntimeConfig.get("threads", "4");
 *
 *   // Read a secret from environment
 *   String password = RuntimeConfig.getSecret("DB_PASSWORD");
 *
 *   // Read a secret with fallback for local dev
 *   String password = RuntimeConfig.getSecretOrDefault("DB_PASSWORD", "localpass");
 *
 *   // Set a value programmatically
 *   RuntimeConfig.set("custom.flag", "true");
 *
 *   // Check if a parameter was passed
 *   if (RuntimeConfig.has("debug")) { ... }
 *
 * CLI examples:
 *   ./gradlew apiTest -Dstand=prod -Dthreads=4 -Dretry=3 -Ddebug=true
 *   ./gradlew uiTest -Dtest.mode=cloud -Dstand=staging
 */
public class RuntimeConfig {

    private static final Log log = Log.forClass(RuntimeConfig.class);

    // All collected parameters: -D flags + programmatically set values.
    // ConcurrentHashMap: safe for parallel TestNG threads.
    private static final ConcurrentHashMap<String, String> params = new ConcurrentHashMap<>();

    // Whether parameters have been collected from System.getProperties()
    private static volatile boolean collected = false;

    private RuntimeConfig() {
    }

    // ──── Collection ────

    /**
     * Collects all -D parameters from System.getProperties().
     * Собирает все -D параметры из System.getProperties().
     *
     * Called automatically on first get() call.
     * Вызывается автоматически при первом вызове get().
     *
     * System.getProperties() contains everything passed via -Dkey=value
     * plus JVM defaults (java.version, os.name, etc.).
     * We store all of them — filtering is the caller's job.
     */
    private static synchronized void collectIfNeeded() {
        if (collected) return;

        Properties sysProps = System.getProperties();
        for (String key : sysProps.stringPropertyNames()) {
            params.put(key, sysProps.getProperty(key));
        }

        collected = true;
        log.info("RuntimeConfig collected {} system properties", params.size());
    }

    // ──── Parameters (-D flags) ────

    /**
     * Gets a parameter value by key. Returns null if not found.
     * Получает значение параметра по ключу. Возвращает null если не найден.
     *
     * RuntimeConfig.get("stand")     → "prod" (if -Dstand=prod was passed)
     * RuntimeConfig.get("missing")   → null
     */
    public static String get(String key) {
        collectIfNeeded();
        return params.get(key);
    }

    /**
     * Gets a parameter with a default value.
     * Получает параметр с дефолтным значением.
     *
     * RuntimeConfig.get("threads", "4")
     *   -Dthreads=8 was passed → "8"
     *   nothing passed         → "4"
     */
    public static String get(String key, String defaultValue) {
        collectIfNeeded();
        return params.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a parameter as int with default.
     * Получает параметр как int с дефолтом.
     *
     * RuntimeConfig.getInt("threads", 4)
     *   -Dthreads=8 → 8
     *   not passed   → 4
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse '{}' as int for key '{}', using default {}", value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a parameter as boolean with default.
     * Получает параметр как boolean с дефолтом.
     *
     * RuntimeConfig.getBoolean("debug", false)
     *   -Ddebug=true → true
     *   not passed    → false
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * Sets a parameter programmatically.
     * Устанавливает параметр программно.
     *
     * Overrides -D value if same key was passed.
     * Useful in @BeforeSuite for computed values.
     */
    public static void set(String key, String value) {
        collectIfNeeded();
        params.put(key, value);
        log.debug("RuntimeConfig set: {}={}", key, value);
    }

    /**
     * Checks if a parameter exists (was passed via -D or set programmatically).
     * Проверяет, существует ли параметр.
     */
    public static boolean has(String key) {
        collectIfNeeded();
        return params.containsKey(key);
    }

    // ──── Secrets (environment variables) ────

    /**
     * Gets a secret from environment variables. Throws if not found.
     * Получает секрет из переменных окружения. Бросает исключение если не найден.
     *
     * Why env vars and not -D flags?
     *   -Dpassword=secret is visible in process list (ps aux).
     *   Environment variables are not visible in process list.
     *
     * Jenkins:
     *   environment {
     *       DB_PASSWORD = credentials('db-password-id')
     *   }
     *
     * Local:
     *   export DB_PASSWORD=mypassword
     *
     * @param envKey environment variable name (e.g. "DB_PASSWORD")
     * @throws RuntimeException if not found
     */
    public static String getSecret(String envKey) {
        String value = System.getenv(envKey);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException("Secret not found in environment: " + envKey
                    + "\n  Jenkins: credentials('" + envKey.toLowerCase() + "') in Jenkinsfile"
                    + "\n  Local:   export " + envKey + "=value");
        }
        log.debug("Secret loaded: {} (length={})", envKey, value.length());
        return value;
    }

    /**
     * Gets a secret with fallback for local development.
     * Получает секрет с фолбэком для локальной разработки.
     *
     * On Jenkins: reads from env.
     * Locally: uses defaultValue so tests work without setting env vars.
     */
    public static String getSecretOrDefault(String envKey, String defaultValue) {
        String value = System.getenv(envKey);
        if (value == null || value.isEmpty()) {
            log.warn("Secret '{}' not found in env, using default", envKey);
            return defaultValue;
        }
        return value;
    }

    // ──── Info ────

    /**
     * Returns all collected parameters as an unmodifiable map.
     * Возвращает все собранные параметры как неизменяемую карту.
     *
     * Useful for logging/debugging:
     *   RuntimeConfig.getAll().forEach((k, v) -> log.info("{}={}", k, v));
     */
    public static Map<String, String> getAll() {
        collectIfNeeded();
        return Map.copyOf(params);
    }

    /**
     * Logs all custom (non-JVM) parameters for debugging.
     * Логирует все кастомные (не-JVM) параметры для отладки.
     *
     * Filters out JVM defaults (java.*, sun.*, os.*, file.*, etc.)
     * to show only what the user passed via -D flags.
     */
    public static void printCustomParams() {
        collectIfNeeded();
        log.info("══════ RUNTIME PARAMS ══════");
        params.entrySet().stream()
                .filter(e -> !isJvmProperty(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.info("  {}={}", e.getKey(), e.getValue()));
        log.info("════════════════════════════");
    }

    /**
     * Checks if a property key is a JVM/system default (not user-provided).
     */
    private static boolean isJvmProperty(String key) {
        return key.startsWith("java.")
                || key.startsWith("sun.")
                || key.startsWith("os.")
                || key.startsWith("file.")
                || key.startsWith("user.")
                || key.startsWith("path.")
                || key.startsWith("line.")
                || key.startsWith("awt.")
                || key.startsWith("jdk.");
    }
}
