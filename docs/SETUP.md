# Setup Guide

From a clean machine to building, testing, and running Sleep Alarm. Windows instructions come first since that's where the project lives; macOS/Linux differences are noted where they exist.

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 (or newer LTS) | `app/build.gradle.kts` targets 17; AGP 8.x needs JDK 17+ |
| Android SDK | Platform 35, recent build-tools | `compileSdk = 35`, `targetSdk = 35` |
| Gradle | nothing to install | the wrapper pins Gradle 8.9 |
| Android Studio | optional, but the easy route | any recent version with AGP 8.7.x support (Ladybug or newer); bundles JDK, SDK manager, emulator |
| Device/emulator | Android 8.0 (API 26) or newer | `minSdk = 26` |

That's it. No third-party dependencies, no NDK, no Firebase, no keys or secrets, no backend.

## 2. Install steps (Windows)

### Option A — Android Studio (recommended)

1. Install Android Studio from <https://developer.android.com/studio>. The default install includes a suitable JDK and the SDK manager.
2. **Open** → select `D:\Code\Alarm` (the folder containing `settings.gradle.kts`).
3. Let Gradle sync. Studio will offer to install anything missing (Platform 35, build-tools) — accept.
4. Run (Shift+F10) with a device or emulator selected.

### Option B — command line only

1. Install JDK 17, e.g. Temurin:
   ```powershell
   winget install EclipseAdoptium.Temurin.17.JDK
   ```
   Set `JAVA_HOME` (adjust for your installed version):
   ```powershell
   [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot", "User")
   ```
   Open a new terminal and check that `java -version` reports 17.x.

2. Get the Android command-line tools: download the "Command line tools only" zip from <https://developer.android.com/studio#command-line-tools-only> and extract it so you end up with `C:\Android\cmdline-tools\latest\bin\sdkmanager.bat`.

3. Install the SDK packages:
   ```powershell
   C:\Android\cmdline-tools\latest\bin\sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```
   Accept licenses when prompted (or run `sdkmanager.bat --licenses`).

4. Tell the build where the SDK is. Either set the environment variable:
   ```powershell
   [Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Android", "User")
   ```
   or create `D:\Code\Alarm\local.properties` containing:
   ```properties
   sdk.dir=C\:\\Android
   ```
   (Backslashes need escaping; forward slashes also work.) `local.properties` is git-ignored and machine-specific — Android Studio writes it for you if you use Studio.

### macOS / Linux

- Use `./gradlew` instead of `gradlew.bat`; you may need `chmod +x gradlew` once.
- JDK 17: `brew install --cask temurin@17` on macOS, `sudo apt install openjdk-17-jdk` on Debian/Ubuntu.
- Default SDK locations are `~/Library/Android/sdk` (macOS) and `~/Android/Sdk` (Linux); point `ANDROID_HOME` there.
- `local.properties` needs no escaping on these platforms: `sdk.dir=/Users/you/Library/Android/sdk`.

## 3. Environment and config

You need `JAVA_HOME` pointing at a JDK 17+ (for CLI builds), and either `ANDROID_HOME` or a `local.properties` with `sdk.dir` — one of the two must identify the SDK.

There is no `.env`, no API keys, and no signing config: release builds come out unsigned, debug builds use the auto-generated debug keystore. `gradle.properties` is already in the repo (`-Xmx2048m`, UTF-8, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`) and needs no edits.

## 4. Building

From `D:\Code\Alarm`:

```powershell
# Debug APK
.\gradlew.bat assembleDebug
# → app\build\outputs\apk\debug\app-debug.apk

# Release APK (unsigned; minification is disabled in this project)
.\gradlew.bat assembleRelease

# Install straight onto a connected device/emulator (USB debugging enabled)
.\gradlew.bat installDebug

# Or manually:
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The first build downloads Gradle 8.9 and the AndroidX artifacts, so it needs network access and a few minutes. After that, builds are incremental.

## 5. Tests

The unit tests are pure JVM (JUnit 4) — no device or emulator needed:

```powershell
.\gradlew.bat test                      # all variants
.\gradlew.bat :app:testDebugUnitTest    # just debug unit tests
```

Two suites: `WakeTimeCalculatorTest` (bedtime/wake-time arithmetic, including DST spring-forward and every slider step — 10 tests) and `MathChallengeTest` (problem format, correctness, bounds, difficulty ordering — 7 tests). HTML reports land in `app\build\reports\tests\`.

There are no instrumented (`androidTest`) tests.

## 6. Running the app

- **Emulator**: Device Manager → create a device with a Google APIs image, API 26–35. Pick 34 or 35 if you want to exercise the modern permission paths.
- **Physical device**: enable Developer Options → USB debugging, connect, `.\gradlew.bat installDebug`, launch "Sleep Alarm".
- On first launch on Android 13+ the app asks for notification permission. Grant it, or the wake-up screen can't appear.
- To test the alarm quickly: "Right now" plus the 1-hour minimum on the slider is as short as it gets — there's no debug shortcut for a shorter alarm in the code.

## 7. Common setup failures

| Symptom | Cause | Fix |
|---|---|---|
| `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path...` | Neither `ANDROID_HOME` nor `local.properties` is set | Create `local.properties` with `sdk.dir` (sections 2–3) |
| `Unsupported class file major version` / `Android Gradle plugin requires Java 17 to run` | Gradle running on JDK 8/11 | Point `JAVA_HOME` at JDK 17; in Studio: Settings → Build Tools → Gradle → Gradle JDK → 17 |
| `Failed to install the following SDK components: platforms;android-35` | Platform 35 missing, licenses unaccepted | `sdkmanager --licenses`, then rebuild with network |
| Build hangs or fails downloading `gradle-8.9-bin.zip` | Proxy/firewall | Configure a proxy in `%USERPROFILE%\.gradle\gradle.properties` (`systemProp.https.proxyHost=...`) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on `installDebug` | Previous install signed with a different debug keystore | `adb uninstall com.sleepalarm`, then reinstall |
| App runs but no wake-up screen when the alarm fires (Android 14+) | Full-screen-intent permission revoked | The app detects this after **Set alarm** and deep-links you to the setting; enable "full-screen notifications" for Sleep Alarm |
| Toast "Allow exact alarms in settings, then set the alarm again." (Android 12/12L) | `SCHEDULE_EXACT_ALARM` not granted | The app opens the exact-alarm settings screen; enable it and press **Set alarm** again |
| Emulator alarm is silent | Emulator images often ship without a default alarm ringtone | Expected, not a bug — the app falls back to vibration-only and says so in the notification. Set a default alarm sound in the emulator's Settings to hear audio |
| `gradlew: Permission denied` (macOS/Linux) | Wrapper script not executable | `chmod +x gradlew` |
