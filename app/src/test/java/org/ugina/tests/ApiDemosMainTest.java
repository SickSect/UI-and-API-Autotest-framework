package org.ugina.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.Capabilities;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.pages.ApiDemosMainPage;

import java.net.URL;

public class ApiDemosMainTest {

    private AndroidDriver driver;
    private ApiDemosMainPage loginPage;

    @BeforeClass
    public void setUp() throws Exception {
        Capabilities options = new BaseOptions()
                .amend("platformName", "Android")
                .amend("appium:automationName", "UiAutomator2")
                .amend("appium:deviceName", "Android Emulator")
                .amend("appium:appPackage", "io.appium.android.apis")
                .amend("appium:appActivity", ".ApiDemos")
                .amend("appium:noReset", true);

        driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
        loginPage = new ApiDemosMainPage(driver);
    }

    @Test
    public void testClickAccessibility() {
        loginPage.clickAccessibility();
        assert loginPage.isOnAccessibilityScreen();
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) driver.quit();
    }
}
