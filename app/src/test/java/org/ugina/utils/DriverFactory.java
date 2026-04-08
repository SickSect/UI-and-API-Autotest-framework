package org.ugina.utils;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebElement;
import org.slf4j.MDC;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

public class DriverFactory {

    private AndroidDriver driver;


    @BeforeClass
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

            MDC.put("deviceName", String.valueOf(options.getCapability("deviceName")));
            MDC.put("testFramework", "JUnit5");

            return driver;
        } catch (Exception e) {
            throw new RuntimeException("❌ Не удалось создать драйвер", e);
        }
    }

    public void quitDriver() {
        if (driver != null) driver.quit();
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
