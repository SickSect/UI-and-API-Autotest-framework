package org.ugina.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContextLogger {

    private static final Logger log = LoggerFactory.getLogger(ContextLogger.class);

    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    public static void setTestContext(String testName, WebDriver driver) {
        CURRENT_TEST.set(testName);
        DRIVER.set(driver);
        MDC.put("testName", testName);
        MDC.put("traceId", UUID.randomUUID().toString());
    }

    public static void clearContext() {
        CURRENT_TEST.remove();
        DRIVER.remove();
        MDC.clear();
    }

    public static void step(String action, String locator, String desc, String... params) {
        if (!log.isInfoEnabled()) return;

        String testName = CURRENT_TEST.get();
        String safeParams = formatParamsSafely(params);

        log.info("[{}] step={} | locator={} | desc={}  | params={}",
                testName != null ? testName : "UNKNOWN",
                action, locator, desc, safeParams);
    }

    public static void waitStep(String locator, long timeoutSec, String reason) {
        step("WAIT", locator, String.format("timeout=%ds, reason=%s", timeoutSec, reason));
    }

    public static void onTestFailure(Throwable t) {
        String testName = CURRENT_TEST.get();
        WebDriver driver = DRIVER.get();
        if (driver instanceof TakesScreenshot ts) {
            Path screenshotDir = Paths.get("test-reports/screenshots");
            try { Files.createDirectories(screenshotDir); } catch (IOException ignored) {}

            String fileName = String.format("%s_%s.png",
                    testName != null ? testName.replace(" ", "_") : "test",
                    System.currentTimeMillis());
            File src = ts.getScreenshotAs(OutputType.FILE);
            Path dest = screenshotDir.resolve(fileName);
            try {
                Files.copy(src.toPath(), dest);
                log.error("[{}] FAILED: {} | screenshot={}", testName, t.getMessage(), dest);
            } catch (IOException e) {
                log.error("[{}] FAILED: {} | screenshot=FAILED_TO_SAVE", testName, t.getMessage());
            }
        } else {
            log.error("[{}] FAILED: {}", testName, t.getMessage());
        }
    }


    private static String formatParamsSafely(String[] params) {
        if (params == null || params.length == 0) return "[]";
        return Arrays.stream(params)
                .map(p -> p == null ? "null" : maskSensitive(p))
                .collect(Collectors.joining(", "));
    }

    private static String maskSensitive(String value) {
        String lower = value.toLowerCase();
        boolean isSensitive = lower.contains("password") || lower.contains("token") ||
                lower.contains("secret") || lower.contains("key") ||
                lower.contains("auth") || lower.contains("pass") ||
                lower.contains("pin") || lower.contains("otp");
        return isSensitive ? "****MASKED****" : value;
    }
}
