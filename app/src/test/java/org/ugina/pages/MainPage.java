package org.ugina.pages;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
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

    private final By inputField = AppiumBy.androidUIAutomator(
            "new UiSelector().className(\"android.widget.EditText\").instance(0)"
    );

    private final By radioButton1 = AppiumBy.androidUIAutomator
            ("new UiSelector().text(\"RadioButton 1\")");

    private final By radioButton2 = AppiumBy.androidUIAutomator
            ("new UiSelector().text(\"RadioButton 2\")");

    public MainPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

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

    public boolean isInputFieldEmpty() {
        ContextLogger.step("Check if input field is empty", "isInputFieldEmpty()", "No params");
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(inputField));
        String text = input.getText();
        ContextLogger.step("Field contains", "isInputFieldEmpty()", text);
        return text == null || text.trim().isEmpty();
    }

    public boolean isInputFieldContainsBasicText(String baseText) {
        ContextLogger.step("Check if input field is contains basic text", "isInputFieldContainsBasicText()", baseText);
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(inputField));
        String text = input.getText();
        ContextLogger.step("Field contains", "isInputFieldContainsBasicText()", text);
        return text.equals(baseText);
    }

    public void enterText(String text) {
        ContextLogger.step("Enter text", "enterText()", "text='" + text + "'");
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(inputField));
        input.clear();
        input.sendKeys(text);
        // Ждём, пока атрибут 'text' обновится (вместо 'value')
        wait.until(ExpectedConditions.attributeContains(inputField, "text", text));
    }

    public String getInputText() {
        ContextLogger.step("Get text from input field", "getInputText()", "No params");
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(inputField));
        String text = input.getText();
        return text == null ? "" : text.trim();
    }

    public void clearInputField() {
        ContextLogger.step("Clear input field", "clearInputField()", "No params");
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(inputField));
        input.clear();
        // Ждём, пока атрибут 'text' станет пустым
        wait.until(ExpectedConditions.attributeContains(inputField, "text", ""));
    }

    public boolean isRadioButton1Selected() {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(radioButton1));
        return "true".equalsIgnoreCase(el.getAttribute("checked"));
    }

    public boolean isRadioButton2Selected() {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(radioButton2));
        return "true".equalsIgnoreCase(el.getAttribute("checked"));
    }

    public void clickRadioButton1(String name) {
        ContextLogger.step("Click radio button", "clickRadioButton1()", "name='" + name + "'");
        wait.until(ExpectedConditions.elementToBeClickable(radioButton1)).click();
    }

    public void clickRadioButton2(String name) {
        ContextLogger.step("Click radio button", "clickRadioButton2()", "name='" + name + "'");
        wait.until(ExpectedConditions.elementToBeClickable(radioButton2)).click();
    }

}
