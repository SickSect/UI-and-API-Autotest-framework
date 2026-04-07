package org.ugina.tests;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.Capabilities;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.Data.PageDriverSetupData;
import org.ugina.pages.ApiDemosMainPage;

import java.net.URL;

import static org.ugina.utils.ConfigReader.getPageDriverSetupData;

public class ApiDemosMainTest implements IPage {

    private AndroidDriver driver;
    private ApiDemosMainPage page;

    @BeforeClass
    public void setUp() throws Exception {
        PageDriverSetupData pageDriverSetupData = getPageDriverSetupData();
        Capabilities options = new BaseOptions()
                .amend("platformName", pageDriverSetupData.platformName)
                .amend("appium:automationName", pageDriverSetupData.appiumAutomationName)
                .amend("appium:deviceName", pageDriverSetupData.appiumDeviceName)
                .amend("appium:appPackage", pageDriverSetupData.appiumAppPackage)
                .amend("appium:appActivity", pageDriverSetupData.appiumAppActivity)
                .amend("appium:noReset", pageDriverSetupData.appiumNoReset);

        driver = new AndroidDriver(new URL(pageDriverSetupData.url), options);
        page = new ApiDemosMainPage(driver);
    }

    @Test
    public void testClickAccessibility() {
        page.clickAccessibility();
        assert page.isOnAccessibilityScreen();
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) driver.quit();
    }
}
