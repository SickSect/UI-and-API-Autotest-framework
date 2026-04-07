package org.ugina.utils;

import org.ugina.Data.PageDriverSetupData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("unchecked")
public class ConfigReader {
    private static final Properties props = new Properties();

    static {
        try (InputStream is = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is == null) throw new RuntimeException("❌ Файл config.properties не найден");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("❌ Ошибка чтения конфига", e);
        }
    }

    // 🔹 Базовый геттер
    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) throw new RuntimeException("❌ Ключ не найден: " + key);
        return value;
    }

    public static PageDriverSetupData getPageDriverSetupData() {
        PageDriverSetupData pageDriverSetupData = new PageDriverSetupData();
        pageDriverSetupData.appiumDeviceName = get("appium.device_name");
        pageDriverSetupData.platformName = get("appium.platformName");
        pageDriverSetupData.appiumDeviceName = get("appium.deviceName");
        pageDriverSetupData.appiumAutomationName = get("appium.automationName");
        pageDriverSetupData.appiumAppPackage = get("appium.appPackage");
        pageDriverSetupData.appiumAppActivity = get("appium.appActivity");
        pageDriverSetupData.appiumNoReset = Boolean.parseBoolean(get("appium.noReset"));
        return pageDriverSetupData;
    }

    // 🔹 Типизированные геттеры
    public static boolean getBoolean(String key) { return Boolean.parseBoolean(get(key)); }
    public static int getInt(String key) { return Integer.parseInt(get(key)); }
}
