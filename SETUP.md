# Mobile Testing Environment Setup (Windows)

Пошаговая настройка окружения для запуска мобильных UI-тестов на Android-эмуляторе.

## Требования

- Windows 10/11
- 8+ GB оперативной памяти (эмулятор требует минимум 2 GB)
- Включённая виртуализация (Hyper-V или Intel HAXM)

## 1. Node.js

Appium — это Node.js приложение, поэтому сначала ставим Node.

```powershell
winget install OpenJS.NodeJS.LTS
```

Закрой и открой терминал заново, затем проверь:

```powershell
node -v   # >= v18
npm -v    # >= 9
```

## 2. Appium Server + UiAutomator2

```powershell
npm install -g appium
appium driver install uiautomator2
```

Проверка — запусти сервер:

```powershell
appium
```

Должен появиться вывод с `Appium REST http interface listener started on 0.0.0.0:4723`. Останови его через `Ctrl+C`, он понадобится позже.

## 3. Android SDK (Command Line Tools)

Скачай архив Command Line Tools с [developer.android.com/studio#command-tools](https://developer.android.com/studio#command-tools) (раздел "Command line tools only").

Распакуй так, чтобы путь был строго:

```
C:\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

Если после распаковки путь выглядит как `cmdline-tools\cmdline-tools\...` — убери лишний уровень вложенности и переименуй папку в `latest`.

## 4. Переменные среды

Выполни в PowerShell (один раз):

```powershell
$SDK = "C:\android-sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $SDK, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SDK, "User")

$oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$oldPath;$SDK\platform-tools;$SDK\emulator;$SDK\cmdline-tools\latest\bin"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")
```

**Закрой ВСЕ окна терминала и открой новое.** Переменные применяются только в новых процессах.

Проверка:

```powershell
sdkmanager --version
```

## 5. Компоненты SDK

```powershell
sdkmanager --licenses
```

Нажимай `y` + Enter на каждый вопрос, пока не появится `All SDK package licenses accepted`.

```powershell
sdkmanager "platform-tools" "emulator" "platforms;android-34" "system-images;android-34;google_apis;x86_64"
```

## 6. Создание эмулятора

```powershell
avdmanager create avd -n "TestAVD" -k "system-images;android-34;google_apis;x86_64" -d "pixel_5"
```

На вопрос `Do you wish to create a custom hardware profile [no]?` — введи `n`.

## 7. Запуск эмулятора

```powershell
emulator -avd TestAVD -no-snapshot-load -gpu swiftshader_indirect
```

Не закрывай это окно. Дожидайся загрузки рабочего стола Android (1–3 минуты).

Проверка что эмулятор подключён:

```powershell
adb devices
```

Ожидаемый вывод:

```
List of devices attached
emulator-5554   device
```

## 8. Установка тестового приложения

```powershell
Invoke-WebRequest -Uri "https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk" -OutFile "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"

adb install -r "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"
```

## Запуск тестов

Три терминала, каждый в своём окне:

**Терминал 1** — Appium сервер:
```powershell
appium
```

**Терминал 2** — эмулятор:
```powershell
emulator -avd TestAVD -no-snapshot-load -gpu swiftshader_indirect
```

**Терминал 3** — тесты:
```powershell
./gradlew uiTest
```

Для запуска в облаке (LambdaTest) вместо локального эмулятора:

```powershell
$env:LT_USERNAME = "your_username"
$env:LT_ACCESS_KEY = "your_access_key"
./gradlew uiTest -Dtest.mode=cloud
```

## Остановка

```powershell
# Остановить эмулятор
adb emu kill

# Или завершить все Java-процессы (Gradle, Appium)
taskkill /F /IM java.exe
```

## CI (GitHub Actions)

UI-тесты запускаются автоматически при push в `main` или через ручной запуск workflow.

Конфигурация в `.github/workflows/ci.yml` использует `android-emulator-runner` для создания эмулятора на CI-сервере:

```yaml
- name: Run E2E Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 34
    arch: x86_64
    profile: pixel_5
    target: google_apis
    emulator-options: >
      -no-window -gpu swiftshader_indirect -no-snapshot
      -noaudio -no-boot-anim -camera-back none
    script: |
      adb wait-for-device shell getprop sys.boot_completed
      ./gradlew clean testWithReport
```

## Troubleshooting

**`adb devices` показывает пустой список** — эмулятор ещё не загрузился, подожди 1–2 минуты. Или проверь что `platform-tools` добавлен в PATH.

**`ANDROID_HOME is not set`** — закрой терминал и открой новый. Переменные среды применяются только к новым процессам.

**Эмулятор падает при старте** — проверь что виртуализация включена: `systeminfo` → строка `Hyper-V Requirements`. Если `No` — включи в BIOS (Intel VT-x / AMD-V).

**`sdkmanager: command not found`** — проверь путь: `C:\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat` должен существовать. Частая ошибка — лишняя вложенность папок после распаковки.