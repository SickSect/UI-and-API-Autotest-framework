package org.ugina.utils;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URL;

public class DriverFactory {

    private AndroidDriver driver;

    @BeforeClass
    public void setUp() {
        Capabilities options = new BaseOptions()
                .amend("platformName", "Android")
                .amend("appium:automationName", "UiAutomator2")
                .amend("appium:deviceName", "Android Emulator")
                .amend("appium:appPackage", "io.appium.android.apis")
                .amend("appium:appActivity", ".ApiDemos")
                .amend("appium:noReset", true)
                .amend("appium:app", "C:\\Users\\sicke\\Desktop\\ApiDemos-debug.apk")
                .amend("appium:ensureWebviewsHavePages", true)
                .amend("appium:nativeWebScreenshot", true)
                .amend("appium:newCommandTimeout", 3600)
                .amend("appium:connectHardwareKeyboard", true);

        driver = new AndroidDriver(this.getUrl(), options);
    }

    public void getDriver(){

    }

    @Test
    public void sampleTest() {

    }

    @Test
    public void testAppLoads() {
        // 🔹 Простейший локатор, который точно есть в ApiDemos
        WebElement anyText = driver.findElement(By.className("android.widget.TextView"));
        assert anyText.isDisplayed() : "Элемент не найден";
    }

    @AfterClass
    public void tearDown() {
        driver.quit();
    }

    private URL getUrl() {
        try {
            return new URL("http://127.0.0.1:4723");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
