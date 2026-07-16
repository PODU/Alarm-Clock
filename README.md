<!-- Improved compatibility of back to top link: See: https://github.com/othneildrew/Best-README-Template/pull/73 -->
<a id="readme-top"></a>



<!-- PROJECT SHIELDS -->
<!--
*** I'm using markdown "reference style" links for readability.
*** Reference links are enclosed in brackets [ ] instead of parentheses ( ).
*** See the bottom of this document for the declaration of the reference variables
*** for contributors-url, forks-url, etc. This is an optional, concise syntax you may use.
*** https://www.markdownguide.org/basic-syntax/#reference-style-links
-->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]



<!-- PROJECT LOGO -->
<br />
<div align="center">
  <h3 align="center">Sleep Alarm</h3>

  <p align="center">
    A duration-first Android alarm clock that makes you solve a challenge before you can dismiss it.
    <br />
    <a href="docs/DESIGN.md"><strong>Explore the docs »</strong></a>
    <br />
    <br />
    <a href="docs/SETUP.md">Setup Guide</a>
    &middot;
    <a href="https://github.com/github_username/repo_name/issues/new?labels=bug">Report Bug</a>
    &middot;
    <a href="https://github.com/github_username/repo_name/issues/new?labels=enhancement">Request Feature</a>
  </p>
</div>



<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#features">Features</a></li>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#architecture">Architecture</a></li>
    <li><a href="#running-tests">Running Tests</a></li>
    <li><a href="#roadmap">Roadmap</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>



<!-- ABOUT THE PROJECT -->
## About The Project

Sleep Alarm flips the usual alarm-clock flow: instead of picking a wake time, you pick **bedtime + how long you want to sleep**, and the app computes the wake time for you — including a configurable fall-asleep buffer and 90-minute sleep-cycle shortcuts. When the alarm rings, snoozing is easy but **dismissing requires passing a wake-up challenge** (mental math by default), so a half-asleep tap can't silence it.

Key design principles (see [docs/DESIGN.md](docs/DESIGN.md) for the full rationale):

* **Asymmetric gating** — snooze is one easy addition problem; dismiss is a harder mixed problem. Fully waking up is the state you must prove. Wrong answers regenerate the problem, so you can't iterate guesses.
* **Never silent** — every ring-path failure degrades to something perceptible: vibration starts before media setup, missing ringtones fall back through alternatives, missing exact-alarm permission falls back to a windowed alarm, and unanswered alarms auto-snooze after 10 minutes instead of draining the battery.
* **Privacy by default** — no `INTERNET` permission, no third-party dependencies, no analytics. All data (settings, alarms, history) stays in local preferences.

### Features

* **Duration-first setup** — 3h–12h slider in 15-minute steps, live wake-time preview, whole-sleep-cycle shortcut chips, weekday/weekend bedtime profiles
* **Five challenge types** — math (three difficulties), typing a phrase, memorizing a digit sequence, shaking the phone, or walking steps; configurable globally and per alarm
* **Multiple alarms** — one-shot and repeating (per-weekday) alarms with per-alarm label, sound, challenge, and difficulty overrides
* **Quick alarm from anywhere** — home-screen widget and Quick Settings tile arm a one-shot alarm at *now + saved sleep duration* with one tap, without opening the app
* **Wake-up history** — per-session log with snooze counts and CLEAN/×n/AUTO badges, plus average-snoozes, first-try-%, and streak stats
* **Robust lifecycle** — survives reboots, app updates, and timezone changes; state is repaired on boot and app open; included in cloud backup/device transfer
* **Always-dark night theme** — Jetpack Compose UI with a warm near-black palette and bundled Space Grotesk font; an alarm app is used in the dark
* **Localized** — English, German, Spanish, French, and Hindi

<p align="right">(<a href="#readme-top">back to top</a>)</p>



### Built With

* [![Kotlin][Kotlin-badge]][Kotlin-url]
* [![Jetpack Compose][Compose-badge]][Compose-url] (Material 3, Compose BOM 2024.10.00)
* [![Android][Android-badge]][Android-url] (minSdk 26, targetSdk 35)
* [![Gradle][Gradle-badge]][Gradle-url] (wrapper-pinned 8.9, Kotlin DSL)

No third-party dependencies beyond AndroidX — no Firebase, no NDK, no backend, no API keys.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- GETTING STARTED -->
## Getting Started

Full instructions (including command-line-only setup and troubleshooting) are in [docs/SETUP.md](docs/SETUP.md).

### Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 (or newer LTS) |
| Android SDK | Platform 35 + recent build-tools |
| Gradle | None to install — wrapper pins 8.9 |
| Device/emulator | Android 8.0 (API 26) or newer |

Android Studio (Ladybug or newer) is optional but bundles all of the above.

### Installation

1. Clone the repo
   ```sh
   git clone https://github.com/github_username/repo_name.git
   ```
2. Open the project folder in Android Studio and let Gradle sync (it will offer to install missing SDK pieces), **or** build from the command line:
   ```sh
   # Windows
   .\gradlew.bat assembleDebug
   # macOS / Linux
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
3. Install onto a connected device or emulator:
   ```sh
   .\gradlew.bat installDebug
   # or: adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- USAGE EXAMPLES -->
## Usage

1. **Set an alarm** — on the Sleep tab, choose how long you want to sleep (or use a sleep-cycle chip) and tap **Start sleeping — wake at X**. The home screen switches to a calm armed state showing the pending wake time.
2. **Grant permissions when prompted** — notification permission on Android 13+, exact-alarm access on Android 12/12L, and full-screen-notification access on Android 14+. The app detects each missing permission and deep-links you to the right settings screen.
3. **When it rings** — a full-screen wake-up UI appears with a pulsing ring, an amber **Snooze** pill (easy challenge), an outline **Dismiss** button (hard challenge), and a live auto-snooze countdown.
4. **Quick alarm** — add the home-screen widget or the Quick Settings tile; one tap arms an alarm at *now + your saved sleep duration*.
5. **Review your mornings** — the History tab shows one row per wake-up session with snooze details and streak/first-try stats; the Settings tab configures challenge type, difficulty, snooze length, fall-asleep buffer, max snoozes, volume ramp, bedtime reminder, and more.

_For design details and the alarm lifecycle state machine, see [docs/DESIGN.md](docs/DESIGN.md)._

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- ARCHITECTURE -->
## Architecture

Single-module Android app, package `com.sleepalarm`:

| Component | Role |
|---|---|
| `MainActivity` | Compose UI — Sleep / History / Settings tabs |
| `AlarmScheduler` | Registers alarms with `AlarmManager` (`setAlarmClock`, windowed fallback), reschedules on boot/update/timezone change |
| `AlarmReceiver` → `AlarmService` | Broadcast wakes a foreground service that rings and vibrates independently of any UI |
| `AlarmActivity` | Full-screen ringing UI with the challenge gate |
| `AlarmStore` / `HistoryStore` / `AlarmSettings` / `Prefs` | Persistence in one `SharedPreferences` file (backed up and repaired on restore) |
| `WakeTimeCalculator` / `MathChallenge` / `Challenges` / `QuickAlarm` | Pure JVM logic, unit-tested without a device |
| `QuickAlarmWidgetProvider` / `QuickAlarmTileService` | One-tap quick alarm entry points |
| `BootReceiver` / `SystemEventsReceiver` | Re-arm alarms after reboot, app update, time or timezone change |

All components except the launcher activity, the widget provider, and the tile service are `exported="false"`; all `PendingIntent`s are `FLAG_IMMUTABLE`; broadcasts are package-scoped.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- RUNNING TESTS -->
## Running Tests

Unit tests are pure-JVM (JUnit 4) — no device or emulator needed:

```sh
.\gradlew.bat test                      # all variants
.\gradlew.bat :app:testDebugUnitTest    # just debug unit tests
```

Suites cover wake-time arithmetic (including DST transitions), math-problem generation and difficulty ordering, challenge logic, alarm scheduling, and quick-alarm selection. HTML reports land in `app/build/reports/tests/`. There are no instrumented tests.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- ROADMAP -->
## Roadmap

- [x] Duration-first alarm setup with sleep-cycle shortcuts
- [x] Multiple alarms with per-alarm overrides
- [x] Five wake-up challenge types
- [x] Quick-alarm widget and Quick Settings tile
- [x] Wake-up history and streak stats
- [x] Localization (de, es, fr, hi)
- [ ] Instrumented UI tests
- [ ] Signed release build / store distribution

See the [open issues](https://github.com/github_username/repo_name/issues) for a full list of proposed features (and known issues).

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- CONTRIBUTING -->
## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open an issue with the tag "enhancement".
Don't forget to give the project a star! Thanks again!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- LICENSE -->
## License

No license file is currently included in this repository. All rights reserved unless a license is added.

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- CONTACT -->
## Contact

Aditya Poduval - podu2997@gmail.com

Project Link: [https://github.com/github_username/repo_name](https://github.com/github_username/repo_name)

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- ACKNOWLEDGMENTS -->
## Acknowledgments

* [Best-README-Template](https://github.com/othneildrew/Best-README-Template)
* [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) (bundled font)
* [Android Developers — Alarms & Reminders](https://developer.android.com/develop/background-work/services/alarms)
* [Jetpack Compose](https://developer.android.com/jetpack/compose)

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/github_username/repo_name.svg?style=for-the-badge
[contributors-url]: https://github.com/github_username/repo_name/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/github_username/repo_name.svg?style=for-the-badge
[forks-url]: https://github.com/github_username/repo_name/network/members
[stars-shield]: https://img.shields.io/github/stars/github_username/repo_name.svg?style=for-the-badge
[stars-url]: https://github.com/github_username/repo_name/stargazers
[issues-shield]: https://img.shields.io/github/issues/github_username/repo_name.svg?style=for-the-badge
[issues-url]: https://github.com/github_username/repo_name/issues
[Kotlin-badge]: https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white
[Kotlin-url]: https://kotlinlang.org/
[Compose-badge]: https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white
[Compose-url]: https://developer.android.com/jetpack/compose
[Android-badge]: https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white
[Android-url]: https://developer.android.com/
[Gradle-badge]: https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white
[Gradle-url]: https://gradle.org/
