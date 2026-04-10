package org.ugina.tests;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.ugina.pages.MainPage;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MainPageTest extends BaseTest {

    @Test(priority = 1)
    @Story("Check box")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Testing pressing on check box and checking status")
    public void selectCheckBox1(){
        MainPage page = new MainPage(driver, wait);
        assertFalse(page.isSelectedCheckBox1());
        page.clickCheckBox1();
        assertTrue(page.isSelectedCheckBox1());
        page.clickCheckBox1();
        assertFalse(page.isSelectedCheckBox1());
    }

    @Test(priority = 2)
    @Story("Check box")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Testing pressing on check box and checking status")
    public void selectCheckBox2(){
        MainPage page = new MainPage(driver, wait);
        assertFalse(page.isSelectedCheckBox2());
        page.clickCheckBox2();
        assertTrue(page.isSelectedCheckBox2());
        page.clickCheckBox2();
        assertFalse(page.isSelectedCheckBox2());
    }

    @Test(priority = 3)
    @Story("Input Field")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Full cycle: hint → input → verify → clear → verify hint")
    public void testInputFieldFullCycle() {
        MainPage page = new MainPage(driver, wait);
        Assert.assertTrue(page.isInputFieldContainsBasicText("hint text"), "Шаг 1: поле должно быть заполнено базовым текстом");
        String testData = "Test123";
        page.enterText(testData);
        Assert.assertEquals(page.getInputText(), testData,
                "Шаг 3: текст не совпадает после ввода");
        page.clearInputField();
        Assert.assertTrue(page.isInputFieldContainsBasicText("hint text"),
                "Шаг 5: поле должно быть пустым после очистки");
    }

    @Test(priority = 4)
    @Story("Radio Button")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Test radio buttons on page")
    public void testRadioButtonFlow() {
        MainPage page = new MainPage(driver, wait);
        Assert.assertTrue(page.isRadioButton1Selected(), "Шаг 1: RadioButton 1 выбран по умолчанию");
        page.clickRadioButton2("RadioButton 2");
        Assert.assertTrue(page.isRadioButton2Selected(), "Шаг 2: RadioButton 2 должен стать выбранным");
        Assert.assertFalse(page.isRadioButton1Selected(), "Шаг 3: RadioButton 1 должен сняться (exclusive)");
        page.clickRadioButton1("RadioButton 1");
        Assert.assertTrue(page.isRadioButton1Selected(), "Шаг 2: RadioButton 1 должен стать выбранным");
        Assert.assertFalse(page.isRadioButton2Selected(), "Шаг 3: RadioButton 2 должен сняться (exclusive)");
        attachScreenshot("TEST");
    }
}
