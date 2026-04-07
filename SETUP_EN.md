# Appium + Android Test Setup Guide (Windows)

### links:
```angular2html
info
https://habr.com/ru/companies/otus/articles/682268/?spm=a2ty_o01.29997173.0.0.33175171eQUsEZ
api demos apk
https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk?spm=a2ty_o01.29997173.0.0.33175171eQUsEZ&file=ApiDemos-debug.apk
```
## 📋 Quick Checklist
```angular2html
# Verify prerequisites
node -v          # >= v18
npm -v           # >= 9
java -version    # >= 17

# Start Appium server
appium           # Should listen on port 4723

# Verify emulator
adb devices      # → emulator-5554   device

# Run tests
.\gradlew.bat test
```

# 📦 Installation Steps

### 1. Install Node.js & npm
```angular2html
winget install OpenJS.NodeJS.LTS
```
⚠️ Restart your terminal after installation!
```angular2html
node -v    # ✅ Should be >= v18
npm -v     # ✅ Should be >= 9
```

### 2. Install Appium Server
```angular2html
# Install Appium globally
npm install -g appium

# Install Android driver
appium driver install uiautomator2

# Start the server (keep this window open)
appium

✅ Expected output:

[Appium] Appium REST http interface listener started on http://0.0.0.0:4723
```

### 3. Install Android SDK (Command Line Tools)

Download commandlinetools-win - https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip?spm=a2ty_o01.29997173.0.0.33175171eQUsEZ&file=commandlinetools-win-11076708_latest.zip

Extract to C:\android-sdk\

✅ Verify folder structure:
```angular2html
C:\android-sdk\
└── cmdline-tools\
    └── latest\
        └── bin\
            ├── sdkmanager.bat
            └── avdmanager.bat
```

### 4. Set Environment Variables (PowerShell)

```angular2html
$SDK = "C:\android-sdk"

# Set permanent environment variables
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $SDK, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SDK, "User")

# Add to PATH
$oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
$newPath = "$oldPath;$SDK\platform-tools;$SDK\emulator;$SDK\cmdline-tools\latest\bin"
[Environment]::SetEnvironmentVariable("Path", $newPath, "User")

Write-Host "✅ Done. Close ALL terminal windows and open a new one."
```

### 5. Install SDK Components

```# Accept licenses (press 'y' + Enter for each)
sdkmanager --licenses

# Install required packages
sdkmanager "platform-tools" "emulator" "platforms;android-36" "system-images;android-36;google_apis;x86_64"
```

### 6. Create & Launch Emulator

```# Create Android Virtual Device
avdmanager create avd -n "TestAVD36" -k "system-images;android-36;google_apis;x86_64" -d "pixel_5"
# When asked about custom hardware profile: enter 'n'

# Launch emulator
emulator -avd TestAVD36 -no-snapshot-load -gpu swiftshader_indirect
```

# 📲 Install Test Application
```angular2html
# Download ApiDemos demo app
Invoke-WebRequest -Uri "https://github.com/appium/appium/raw/master/packages/appium/sample-code/apps/ApiDemos-debug.apk" -OutFile "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"

# Install on emulator
adb install -r "$env:USERPROFILE\Desktop\ApiDemos-debug.apk"
✅ Expected output: Performing Streamed Install... Success

```

# 🔄 How It Works: Architecture Flow

```
Your Java code: driver.findElement().click()
          ↓
Appium Java Client (wraps command in JSON)
          ↓
HTTP POST → http://127.0.0.1:4723/wd/hub
          ↓
Appium Server (selects uiautomator2 driver)
          ↓
UiAutomator2 Driver → adb shell commands
          ↓
Emulator / Device (executes physical action)
          ↓
App responds → result travels back up
          ↓
TestNG validates → Gradle prints ✅ PASSED
```

# ✅ Final Verification

```angular2html
# 1. Is Appium server running?
curl http://127.0.0.1:4723/status
# → {"value":{"ready":true,...}}

# 2. Is emulator detected?
adb devices
# → emulator-5554   device

# 3. Is test app installed?
adb shell pm list packages | findstr "io.appium.android.apis"
# → package:io.appium.android.apis

# 4. Do tests compile?
.\gradlew.bat :app:compileTestJava
# → BUILD SUCCESSFUL
```

# 📁 Recommended Project Structure
```angular2html
my-appium-project/
├── app/
│   ├── build.gradle              # Dependencies: appium, testng, allure
│   └── src/test/java/
│       ├── pages/                # Page Objects (LoginPage.java)
│       ├── tests/                # Test classes (LoginTest.java)
│       └── utils/                # DriverFactory, ConfigReader
├── config/
│   └── test-config.json          # Externalized settings (URLs, packages, paths)
├── apps/
│   └── ApiDemos-debug.apk        # Test application
├── build.gradle                  # Root configuration
├── settings.gradle
└── README.md                     # This documentation
```