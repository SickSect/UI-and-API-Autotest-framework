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


```angular2html
  full-e2e-tests:
    name: 🔴 Full Appium Tests
    runs-on: ubuntu-latest
    timeout-minutes: 40

    if: >
      github.event_name == 'pull_request' || 
      github.ref == 'refs/heads/main' ||
      (github.event_name == 'workflow_dispatch' && inputs.run_full_tests == true)

    needs: build-check

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Cache Appium
        uses: actions/cache@v4
        with:
          path: ~/.appium
          key: appium-${{ runner.os }}-2.33.0

      - name: Install Appium & Driver
        run: |
          npm install -g appium
          appium driver install uiautomator2
          appium &
          sleep 5  # Ждём старта сервера

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Run E2E Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          profile: pixel_5
          target: google_apis
          cache-key: android-emulator-34-x86_64
          script: |
            ./gradlew clean testWithReport -Dconfig.file=ci.properties

      - name: Upload Allure Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: allure-report-${{ github.run_number }}
          path: app/build/reports/allure-report
          retention-days: 7


full-e2e-tests:
name: 🔴 Full Appium Tests
runs-on: ubuntu-latest
timeout-minutes: 40

if: >
github.event_name == 'pull_request' ||
github.ref == 'refs/heads/main' ||
(github.event_name == 'workflow_dispatch' && inputs.run_full_tests == true)

needs: build-check

steps:
- name: Checkout code
uses: actions/checkout@v4

- name: Set up JDK
uses: actions/setup-java@v4
with:
distribution: 'temurin'
java-version: '17'

- name: Set up Node.js
uses: actions/setup-node@v4
with:
node-version: '20'

- name: Cache Appium
uses: actions/cache@v4
with:
path: ~/.appium
key: appium-${{ runner.os }}-2.33.0

- name: Install Appium & Driver
run: |
npm install -g appium
appium driver install uiautomator2
appium &
sleep 5  # Ждём старта сервера

- name: Make gradlew executable
run: chmod +x ./gradlew

- name: Run E2E Tests
uses: reactivecircus/android-emulator-runner@v2
with:
api-level: 30
arch: x86_64
profile: pixel_5
target: 'google_apis'
force-avd-creation: true
emulator-boot-timeout: 600
disable-animations: true
disable-spellchecker: true
boot-timeout: 600
emulator-options: >
-no-window -gpu swiftshader_indirect -no-snapshot
-noaudio -no-boot-anim -camera-back none
script: |
adb wait-for-device shell getprop sys.boot_completed
echo "✅ Emulator is ready!"
./gradlew clean testWithReport -Dconfig.file=ci.properties

- name: Upload Allure Report
if: always()
uses: actions/upload-artifact@v4
with:

name: allure-report-${{ github.run_number }}
path: app/build/reports/allure-report
retention-days: 7



taskkill /F /IM java.exe
```