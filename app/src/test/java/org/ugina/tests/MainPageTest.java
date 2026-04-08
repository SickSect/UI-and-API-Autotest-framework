package org.ugina.tests;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ugina.Data.PageDriverSetupData;
import org.ugina.pages.MainPage;

import java.net.URL;
import java.time.Duration;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.ugina.utils.ConfigReader.getPageDriverSetupData;

public class MainPageTest implements ITest {

    private AndroidDriver driver;
    private MainPage page;

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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        page = new MainPage(driver, wait);
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) driver.quit();
    }


    @Test
    public void selectCheckBox1(){
        assertFalse(page.isSelectedCheckBox1());
        page.clickCheckBox1();
        assertTrue(page.isSelectedCheckBox1());
        page.clickCheckBox1();
        assertFalse(page.isSelectedCheckBox1());
    }

    @Test
    public void selectCheckBox2(){
        assertFalse(page.isSelectedCheckBox2());
        page.clickCheckBox2();
        assertTrue(page.isSelectedCheckBox2());
        page.clickCheckBox2();
        assertFalse(page.isSelectedCheckBox2());
    }
}
