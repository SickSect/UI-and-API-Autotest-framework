# 🛠️ Настройка окружения: Appium + Android SDK (Windows)

# link - https://habr.com/ru/companies/otus/articles/682268/

## 1️⃣ Node.js & npm
```powershell
winget install OpenJS.NodeJS.LTS
# ⚠️ Перезапусти терминал!
node -v  # должно быть >= v18
npm -v   # должно быть >= 9
```

## 2️⃣ Appium Server
```npm install -g appium
appium driver install uiautomator2
appium  # запусти сервер и оставь окно открытым (слушает порт 4723)

```
## 3️⃣ Android SDK (Command Line Tools)

```Скачай архив: commandlinetools-win
Распакуй содержимое в C:\android-sdk\
✅ Убедись, что путь к менеджерам строго такой:
C:\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

## 4️⃣ Переменные среды (выполни в PowerShell)

```angular2html
$SDK = "C:\android-sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $SDK, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SDK, "User")
$oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$oldPath;$SDK\platform-tools;$SDK\emulator;$SDK\cmdline-tools\latest\bin"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")
Write-Host "✅ Готово. ЗАКРОЙ ВСЕ окна терминала и открой НОВОЕ."
```

## 5️⃣ Установка компонентов SDK (в НОВОМ терминале)

```angular2html
sdkmanager --licenses  # нажимай `y` + Enter, пока не появится "All SDK package licenses accepted"
sdkmanager "platform-tools" "emulator" "platforms;android-36" "system-images;android-36;google_apis;x86_64"
```

## 6️⃣ Создание и запуск эмулятора
```angular2html
avdmanager create avd -n "TestAVD36" -k "system-images;android-36;google_apis;x86_64" -d "pixel_5"
# На вопрос "Do you wish to create a custom hardware profile [no]?" введи: n

emulator -avd TestAVD36 -no-snapshot-load -gpu swiftshader_indirect
# ⚠️ Не закрывай это окно. Дождись загрузки рабочего стола Android (1–3 мин)
```

# ✅ Проверка готовности
```adb devices
# Ожидаемый вывод:
# List of devices attached
# emulator-5554   device
```


## Установка ApiDemos-debug.apk

```Invoke-WebRequest -Uri "https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk" -OutFile "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"
adb install -r "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"
```

## Запуск
```angular2html
Поочередно в разных терминалах
appium

>emulator -avd TestAVD36 -no-snapshot-load -gpu swiftshader_indirect

затем открыть appium-inspector 

внести данные и начать сессию
```