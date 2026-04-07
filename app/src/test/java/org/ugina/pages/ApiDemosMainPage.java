package org.ugina.pages;


import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;

public class ApiDemosMainPage {

    private final AndroidDriver driver;
    private final By accessibilityBtn = AppiumBy.androidUIAutomator(
            "new UiSelector().text(\"Accessibility\")"
    );

    public ApiDemosMainPage(AndroidDriver driver) {
        this.driver = driver;
    }

    public void clickAccessibility() {
        driver.findElement(accessibilityBtn).click();
    }

    public boolean isOnAccessibilityScreen() {
        return driver.findElement(By.className("android.widget.TextView")).isDisplayed();
    }
}
