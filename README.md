https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk

# 1. Задай переменную для текущей сессии
```angular2html
$env:ANDROID_HOME = "C:\Users\USERNAME\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\USERNAME\AppData\Local\Android\Sdk"
```

# 2. Запусти Appium в том же окне
appium


открой инспекто и настрой json


{
"platformName": "Android",
"appium:automationName": "UiAutomator2",
"appium:deviceName": "Android Emulator",
"appium:appPackage": "io.appium.android.apis",
"appium:appActivity": ".ApiDemos",
"appium:noReset": true,
"appium:app": "C:\\appium-tests\\apps\\ApiDemos-debug.apk"
}

🔍 Разбор инструментов, которые ты установил
## 1️⃣ adb (Android Debug Bridge)
Что это: Консольная утилита от Google. Часть platform-tools.
Зачем: Это единственный легальный способ управлять Android-устройством с компьютера без рута.
Что умеет:
Устанавливать/удалять APK
Делать скриншоты и записывать видео
Эмулировать нажатия, свайпы, ввод текста
Читать логи приложения (logcat)
Получать дерево UI-элементов
Где работает: На твоём ПК. Общается с эмулятором/телефоном по USB или сети.
## 2️⃣ Appium Server (appium)
Что это: Node.js-сервер, который слушает порт 4723.
Зачем: Переводчик между твоим кодом и устройством. Ты пишешь на Java, Appium превращает это в команды для Android/iOS.
Как работает:
Принимает HTTP-запрос от Java-клиента: POST /session
Загружает нужный драйвер (uiautomator2 для Android)
Драйвер вызывает adb → устройство выполняет действие
Возвращает результат обратно в Java
Важно: Appium не внедряется в приложение. Он управляет им "снаружи", как пользователь. Это значит: можно тестировать любое приложение, даже без доступа к исходному коду.
## 3️⃣ Appium Java Client (io.appium:java-client)
Что это: Библиотека, которую ты подключаешь в build.gradle.
Зачем: Даёт тебе удобные классы: AndroidDriver, AppiumBy, MobileElement.
Как работает: Когда ты пишешь driver.findElement(...).click(), клиент упаковывает это в JSON и отправляет на http://127.0.0.1:4723. Без этой библиотеки пришлось бы писать HTTP-запросы вручную.
## 4️⃣ Appium Inspector
Что это: Графическое приложение (работает в браузере).
Зачем: "Рентген" экрана телефона. Показывает, как Appium видит интерфейс в реальном времени.
Что даёт:
Видишь дерево всех элементов (кнопки, поля, тексты)
Копируешь точные локаторы (id, accessibilityId, xpath)
Проверяешь, кликается ли элемент, виден ли он
Без него: Придётся гадать локаторы вручную или использовать adb shell uiautomator dump. С ним — 10 секунд вместо 10 минут.
## 5️⃣ TestNG + Gradle
TestNG: Фреймворк, который находит методы с @Test, запускает их, ловит исключения, пишет PASSED/FAILED.
Gradle: Система сборки. Качает зависимости, компилирует Java-код, запускает TestNG, генерирует отчёты.


```angular2html
Твой Java-код
     ↓ (вызывает driver.findElement().click())
Appium Java Client
     ↓ (отправляет HTTP POST на http://127.0.0.1:4723)
Appium Server
     ↓ (выбирает драйвер uiautomator2)
UiAutomator2 Driver
     ↓ (вызывает adb shell commands)
adb + Эмулятор/Телефон
     ↓ (физически нажимает на экран)
Приложение реагирует
     ↑ (возвращает результат: "элемент найден, клик выполнен")
Appium Server → Java Client → Твой код получает ответ
     ↓
TestNG проверяет: assertThat(isWelcomeDisplayed()).isTrue()
     ↓
Gradle пишет в консоль: ✅ PASSED
```