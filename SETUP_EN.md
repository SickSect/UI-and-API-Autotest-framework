# Mobile Testing Environment Setup (Windows)

Step-by-step guide to set up the environment for running mobile UI tests on an Android emulator.

## Requirements

- Windows 10/11
- 8+ GB RAM (emulator needs at least 2 GB)
- Virtualization enabled (Hyper-V or Intel HAXM)

## 1. Node.js

Appium is a Node.js application, so Node goes first.

```powershell
winget install OpenJS.NodeJS.LTS
```

Close and reopen the terminal, then verify:

```powershell
node -v   # >= v18
npm -v    # >= 9
```

## 2. Appium Server + UiAutomator2

```powershell
npm install -g appium
appium driver install uiautomator2
```

Verify by starting the server:

```powershell
appium
```

You should see `Appium REST http interface listener started on 0.0.0.0:4723`. Stop it with `Ctrl+C` — we'll need it later.

## 3. Android SDK (Command Line Tools)

Download the Command Line Tools archive from [developer.android.com/studio#command-tools](https://developer.android.com/studio#command-tools) ("Command line tools only" section).

Extract so that the path is exactly:

```
C:\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

If after extraction the path looks like `cmdline-tools\cmdline-tools\...` — remove the extra nesting level and rename the folder to `latest`.

## 4. Environment Variables

Run in PowerShell (once):

```powershell
$SDK = "C:\android-sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $SDK, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SDK, "User")

$oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$oldPath;$SDK\platform-tools;$SDK\emulator;$SDK\cmdline-tools\latest\bin"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")
```

**Close ALL terminal windows and open a new one.** Environment variables only apply to new processes.

Verify:

```powershell
sdkmanager --version
```

## 5. SDK Components

```powershell
sdkmanager --licenses
```

Press `y` + Enter for each prompt until `All SDK package licenses accepted` appears.

```powershell
sdkmanager "platform-tools" "emulator" "platforms;android-34" "system-images;android-34;google_apis;x86_64"
```

## 6. Create Emulator

```powershell
avdmanager create avd -n "TestAVD" -k "system-images;android-34;google_apis;x86_64" -d "pixel_5"
```

When asked `Do you wish to create a custom hardware profile [no]?` — type `n`.

## 7. Start Emulator

```powershell
emulator -avd TestAVD -no-snapshot-load -gpu swiftshader_indirect
```

Keep this window open. Wait for the Android home screen to load (1–3 minutes).

Verify the emulator is connected:

```powershell
adb devices
```

Expected output:

```
List of devices attached
emulator-5554   device
```

## 8. Install Test Application

```powershell
Invoke-WebRequest -Uri "https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk" -OutFile "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"

adb install -r "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"
```

## Running Tests

Three terminals, each in its own window:

**Terminal 1** — Appium server:
```powershell
appium
```

**Terminal 2** — emulator:
```powershell
emulator -avd TestAVD -no-snapshot-load -gpu swiftshader_indirect
```

**Terminal 3** — tests:
```powershell
./gradlew uiTest
```

To run in the cloud (LambdaTest) instead of a local emulator:

```powershell
$env:LT_USERNAME = "your_username"
$env:LT_ACCESS_KEY = "your_access_key"
./gradlew uiTest -Dtest.mode=cloud
```

## Stopping

```powershell
# Stop emulator
adb emu kill

# Or kill all Java processes (Gradle, Appium)
taskkill /F /IM java.exe
```

## CI (GitHub Actions)

UI tests run automatically on push to `main` or via manual workflow dispatch.

The configuration in `.github/workflows/ci.yml` uses `android-emulator-runner` to create an emulator on the CI server:

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

**`adb devices` shows empty list** — the emulator hasn't finished booting yet, wait 1–2 minutes. Or check that `platform-tools` is added to PATH.

**`ANDROID_HOME is not set`** — close the terminal and open a new one. Environment variables only apply to new processes.

**Emulator crashes on start** — check that virtualization is enabled: `systeminfo` → `Hyper-V Requirements` line. If `No` — enable it in BIOS (Intel VT-x / AMD-V).

**`sdkmanager: command not found`** — verify the path: `C:\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat` must exist. Common mistake — extra folder nesting after extraction.