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
import java.time.Duration;

public class DriverFactory {

    private AndroidDriver driver;

    public AndroidDriver createDriver() {
        try {
            Capabilities options = new BaseOptions()
                    .amend("platformName", ConfigReader.get("appium.platformName"))
                    .amend("appium:automationName", ConfigReader.get("appium.automationName"))
                    .amend("appium:deviceName", ConfigReader.get("appium.deviceName"))
                    .amend("appium:appPackage", ConfigReader.get("app.package"))
                    .amend("appium:appActivity", ConfigReader.get("app.activity"))
                    .amend("appium:noReset", ConfigReader.getBoolean("app.noReset"))
                    .amend("appium:app", ConfigReader.get("app.path"))
                    .amend("appium:newCommandTimeout", ConfigReader.getInt("appium.newCommandTimeout"));

            driver = new AndroidDriver(
                    new URL(ConfigReader.get("appium.serverUrl")),
                    options
            );

            driver.manage().timeouts().implicitlyWait(
                    Duration.ofSeconds(ConfigReader.getInt("timeouts.implicitWait"))
            );

            return driver;
        } catch (Exception e) {
            throw new RuntimeException("❌ Не удалось создать драйвер", e);
        }
    }

    public void quitDriver() {
        if (driver != null) driver.quit();
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
            return new URL(ConfigReader.get("appium.serverUrl"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
