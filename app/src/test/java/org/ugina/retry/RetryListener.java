package org.ugina.retry;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class RetryListener implements IAnnotationTransformer {
    /**
     * Вызывается TestNG для каждого @Test метода перед запуском.
     *
     * @param annotation аннотация @Test — можно модифицировать
     * @param testClass  класс теста (может быть null)
     * @param testConstructor конструктор (может быть null)
     * @param testMethod метод теста (может быть null)
     */
    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {

        // Подставляем RetryAnalyzer только если у теста ещё нет своего.
        // Это позволяет на конкретном тесте указать другой analyzer
        // через @Test(retryAnalyzer = MyCustomRetry.class) — он не будет перезаписан.
        if (annotation.getRetryAnalyzerClass() == null) {
            annotation.setRetryAnalyzer(CustomRetryAnalizer.class);
        }
    }
}
