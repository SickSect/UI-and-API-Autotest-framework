package org.ugina.retry;

import io.qameta.allure.Allure;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class FlakyListener implements ITestListener {


    @Override
    public void onTestSuccess(ITestResult result) {
        Object attempt = result.getAttribute("retryAttempt");
        if (attempt != null) {
            System.out.printf("⚠️  FLAKY: %s passed on retry #%s%n",
                    result.getMethod().getMethodName(), attempt);
            Allure.label("flaky", "true");
        }
    }
}
