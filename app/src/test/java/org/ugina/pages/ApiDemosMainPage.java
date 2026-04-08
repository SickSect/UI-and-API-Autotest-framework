package org.ugina.pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;

public class ApiDemosMainPage {

    private final AndroidDriver driver;

    public ApiDemosMainPage(AndroidDriver driver) {
        this.driver = driver;
    }

    public boolean isOnAccessibilityScreen() {
        return driver.findElement(By.className("android.widget.TextView")).isDisplayed();
    }
}
