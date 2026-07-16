# Sleep Alarm — Documentation

Docs for the Sleep Alarm Android app that lives at the repo root.

## What's in here

- [ARCHITECTURE.md](ARCHITECTURE.md) — how the pieces fit together, the process/threading model, and why it's structured this way
- [DESIGN.md](DESIGN.md) — design decisions, data model, intent contracts, the alarm state machine, error handling, security notes
- [CODE_WALKTHROUGH.md](CODE_WALKTHROUGH.md) — a file-by-file tour of the source
- [SETUP.md](SETUP.md) — getting a build environment working, building, running tests
- [USAGE.md](USAGE.md) — user guide for both screens, plus troubleshooting
- [USE_CASES.md](USE_CASES.md) — where this is actually useful, and what a commercial version would take

## The short version

Sleep Alarm is a native Android alarm clock built around two ideas.

First, you don't pick a wake-up time. You tell the app when you're going to sleep — either "right now" or a chosen bedtime — and how many hours you want (1 to 12, in half-hour steps). It works out the wake time and schedules an exact alarm through `AlarmManager.setAlarmClock`.

Second, turning the alarm off requires math. When it fires, a full-screen activity appears over the lockscreen, and both buttons are gated behind arithmetic: snooze gets a simple addition, dismiss gets a harder `a × b + c` problem. Get one wrong and you're given a *different* problem, so you can't brute-force a single sum while half asleep.

It's meant for heavy sleepers who silence normal alarms on reflex, and for anyone who thinks in sleep duration ("I want 7.5 hours") rather than clock time. It also happens to be a decent reference codebase if you're wrestling with the modern Android alarm stack — exact alarms, foreground services, full-screen intents, Doze, and the Android 13/14 permission maze — because it does all of that with zero third-party dependencies.

## Features

- Sleep "right now", or pick a bedtime with a Material 3 time picker (rolls to tomorrow if the time already passed today)
- Duration slider from 1 to 12 hours in 0.5-hour steps, with a live wake-time preview that ticks every second
- Card showing the active alarm's wake time, with a cancel button
- Snooze length (5/10/15 min) and math difficulty (Easy/Medium/Hard), saved in `SharedPreferences`
- Exact scheduling via `setAlarmClock`, which survives Doze; if the exact-alarm permission is missing when a snooze or boot-reschedule happens, it falls back to `setWindow` with a ~10-minute window rather than dropping the alarm
- Full-screen alarm UI over the lockscreen, screen forced on
- Alarm sound looped on the alarm audio stream (`USAGE_ALARM`) from a foreground service, ramping from quiet to full volume over 60 seconds; vibration-only if no ringtone exists
- Vibration starts *before* audio, so a broken ringtone can never make the alarm fully silent
- Auto-snooze after 10 unanswered minutes, instead of ringing until the battery dies; a broadcast closes the alarm screen if it's still up
- Back button does nothing while ringing — math is the only way out
- Survives reboots and app updates (a `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver re-schedules from persisted state)
- Walks the user through the permission gauntlet: notification permission on Android 13+, exact-alarm settings on 12/12L, full-screen-intent settings on 14+

## Tech snapshot

| | |
|---|---|
| Language / UI | Kotlin 2.0.21, Jetpack Compose (Material 3) |
| SDK levels | minSdk 26 (Android 8.0), targetSdk/compileSdk 35 |
| Build | Gradle 8.9 wrapper, AGP 8.7.3, JDK 17 |
| Dependencies | AndroidX only (Compose BOM 2024.10.00, activity-compose, core-ktx, lifecycle-runtime-ktx); JUnit 4 for tests. Nothing third-party. |
| Package | `com.sleepalarm` |
| Tests | JVM unit tests for `WakeTimeCalculator` and `MathChallenge` |
