package org.ugina.tests;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
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
    private PageDriverSetupData pageDriverSetupData;

    @BeforeClass
    public void setUp() throws Exception {
        pageDriverSetupData = getPageDriverSetupData();
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

    @Test(priority = 4, description = "Full check for input field")
    public void testInputFieldFullCycle() {
        Assert.assertTrue(page.isInputFieldContainsBasicText("hint text"), "Шаг 1: поле должно быть заполнено базовым текстом");
        String testData = "Test123";
        page.enterText(testData);
        Assert.assertEquals(page.getInputText(), testData,
                "Шаг 3: текст не совпадает после ввода");
        page.clearInputField();
        Assert.assertTrue(page.isInputFieldContainsBasicText("hint text"),
                "Шаг 5: поле должно быть пустым после очистки");
    }

    @Test(priority = 5, description = "RadioButtons interaction")
    public void testCheckboxAndRadioFlow() {
        Assert.assertTrue(page.isRadioButton1Selected(), "Шаг 1: RadioButton 1 выбран по умолчанию");
        page.clickRadioButton2("RadioButton 2");
        Assert.assertTrue(page.isRadioButton2Selected(), "Шаг 2: RadioButton 2 должен стать выбранным");
        Assert.assertFalse(page.isRadioButton1Selected(), "Шаг 3: RadioButton 1 должен сняться (exclusive)");
        page.clickRadioButton1("RadioButton 1");
        Assert.assertTrue(page.isRadioButton1Selected(), "Шаг 2: RadioButton 1 должен стать выбранным");
        Assert.assertFalse(page.isRadioButton2Selected(), "Шаг 3: RadioButton 2 должен сняться (exclusive)");
    }
}
