package org.ugina.pages;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.MDC;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.ugina.utils.ContextLogger;

public class MainPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    @BeforeTest
    public void beforeTest() {
        // ✅ Контекст устройства (для каждого потока свой)
        MDC.put("deviceName", "Pixel_6_API_34");
        MDC.put("platformVersion", "14");
    }

    @AfterTest
    void tearDown() {
        // ✅ Очистка ThreadLocal + MDC (обязательно для параллельных запусков)
        ContextLogger.clearContext();
    }

    /**
     * PAGE ELEMENTS
     */
    private final By saveButton =
            AppiumBy.androidUIAutomator("new UiSelector().text(\"Save\")");

    private final By checkBox1 =
            AppiumBy.androidUIAutomator("new UiSelector().text(\"Checkbox 1\")");

    private final By checkBox2 =
            AppiumBy.androidUIAutomator("new UiSelector().text(\"Checkbox 2\")");


    public MainPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /**
     * checkbox 1 click
     */
    public void clickCheckBox1(){
        ContextLogger.step("CLICK CheckBox1", "clickCheckBox1()", "No params");
        WebElement checkbox = wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(checkBox1)
        );
        checkbox.click();

        // 🔹 Ждём, пока состояние реально изменится (защита от flaky-тестов)
        wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.attributeToBeNotEmpty(checkbox, "checked")
        );
    }

    /**
     * checkbox 2 click
     */
    public void clickCheckBox2(){
        ContextLogger.step("CLICK CheckBox2", "clickCheckBox2()", "No params");
        WebElement checkbox = wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable(checkBox2)
        );
        checkbox.click();

        // 🔹 Ждём, пока состояние реально изменится (защита от flaky-тестов)
        wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.attributeToBeNotEmpty(checkbox, "checked")
        );
    }

    /**
     * is Selected CheckBox1
     * @return boolean
     */
    public boolean isSelectedCheckBox1(){
        ContextLogger.step("Validate checkbox1 condition", "isSelectedCheckBox1()", "No params");
        WebElement checkbox = wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(checkBox1)
        );

        // 🔹 В Android для CheckBox используем атрибут "checked", а не isSelected()
        String checked = checkbox.getAttribute("checked");
        ContextLogger.step("Checkbox 'checked' attribute: " + checked, "isSelectedCheckBox1()", "result");

        return checked.equalsIgnoreCase("true");
    }

    /**
     * is Selected CheckBox2
     * @return boolean
     */
    public boolean isSelectedCheckBox2(){
        ContextLogger.step("Validate checkbox2 condition", "isSelectedCheckBox2()", "No params");
        WebElement checkbox = wait.until(
                org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(checkBox2)
        );

        // 🔹 В Android для CheckBox используем атрибут "checked", а не isSelected()
        String checked = checkbox.getAttribute("checked");
        ContextLogger.step("Checkbox 'checked' attribute: " + checked, "isSelectedCheckBox2()", "result");

        return checked.equalsIgnoreCase("true");
    }

}
