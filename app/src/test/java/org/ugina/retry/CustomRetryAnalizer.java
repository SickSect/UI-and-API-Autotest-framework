package org.ugina.retry;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.ugina.ApiClient.utils.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Set;

/**
 * Решает, стоит ли перезапустить упавший тест.
 * Implements IRetryAnalyzer — TestNG вызывает retry() после каждого падения.
 *
 * Логика:
 *   - Сетевые ошибки (таймаут, сервер недоступен) → перезапускаем, потому что
 *     проблема может быть временной. Тест сам по себе корректен.
 *   - AssertionError (тест нашёл баг) → НЕ перезапускаем. Повторный запуск
 *     не исправит баг в приложении, а только скроет его.
 *   - Всё остальное → НЕ перезапускаем. Неизвестные ошибки лучше расследовать.
 *
 * Зачем нужно:
 *   В CI тесты падают не только из-за багов. Сервер моргнул на 500мс,
 *   DNS не резолвнулся, эмулятор задумался — и тест красный.
 *   Retry отсеивает такой шум от реальных проблем.
 *
 * Использование:
 *   Автоматически через RetryListener (см. ниже) — не нужно писать
 *   @Test(retryAnalyzer = RetryAnalyzer.class) на каждом тесте.
 */

public class CustomRetryAnalizer implements IRetryAnalyzer {

    private static final int MAX_RETRY_COUNT = 3;
    private final ThreadLocal<Integer> currentRetryCount = ThreadLocal.withInitial(() -> 0);
    private static final Log log = Log.forClass(CustomRetryAnalizer.class);

    // Инфраструктурные исключения — перезапускаем
    private static final Set<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS = Set.of(
            HttpTimeoutException.class,
            ConnectException.class,
            IOException.class
    );

    private boolean isRetryable(Throwable failure){
        Throwable current = failure;
        while (current != null) {
            for (Class<? extends Throwable> retryable : RETRYABLE_EXCEPTIONS) {
                if (retryable.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }


    @Override
    public boolean retry(ITestResult result) {
        Throwable failure = result.getThrowable();

        if (!isRetryable(failure)) {
            log.info("✗ NOT retrying [{}] — error is not retryable: {}",
                    result.getName(), failure.getClass().getSimpleName());
            return false;
        }

        if (currentRetryCount.get() < MAX_RETRY_COUNT) {
            currentRetryCount.set(currentRetryCount.get() + 1);
            log.warn("↻ RETRYING [{}] — attempt {}/{} — reason: {}",
                    result.getName(), currentRetryCount, MAX_RETRY_COUNT,
                    failure.getMessage());
            return true;
        }

        log.error("✗ FAILED [{}] — all {} retries exhausted — last error: {}",
                result.getName(), MAX_RETRY_COUNT, failure.getMessage());
        return false;
    }
}
