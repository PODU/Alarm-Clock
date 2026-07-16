# Code Walkthrough

A file-by-file tour. All application code lives in one Gradle module, `app`, under the package `com.sleepalarm` — nine production Kotlin files (about 1,100 lines) and two test files.

## Repository layout

```
D:\Code\Alarm
├── build.gradle.kts                 # root: plugin versions (AGP 8.7.3, Kotlin 2.0.21)
├── settings.gradle.kts              # repos (google, mavenCentral), rootProject "SleepAlarm", includes :app
├── gradle.properties                # JVM args, AndroidX flags, non-transitive R class
├── gradle/wrapper/                  # Gradle 8.9 wrapper
├── gradlew / gradlew.bat
├── README.md
└── app
    ├── build.gradle.kts             # module config + dependencies
    └── src
        ├── main
        │   ├── AndroidManifest.xml
        │   ├── java/com/sleepalarm/*.kt
        │   └── res/                 # strings, colors, themes, launcher icons
        └── test/java/com/sleepalarm/*.kt
```

---

## Build and configuration

### `build.gradle.kts` (root)

Declares three plugins with `apply false`: `com.android.application` 8.7.3, `org.jetbrains.kotlin.android` 2.0.21, and `org.jetbrains.kotlin.plugin.compose` 2.0.21 — the Kotlin 2.x Compose compiler plugin that replaces the old `composeOptions` block.

### `app/build.gradle.kts`

`namespace`/`applicationId` are `com.sleepalarm`; `minSdk 26`, `targetSdk 35`, `compileSdk 35`; Java/Kotlin target 17; `compose = true`. A release build type exists but has `isMinifyEnabled = false` — no ProGuard/R8 shrinking is configured. Dependencies: Compose BOM `2024.10.00` (ui, material3, ui-tooling-preview), `activity-compose 1.9.3`, `core-ktx 1.15.0`, `lifecycle-runtime-ktx 2.8.7`, `ui-tooling` (debug only), and `junit 4.13.2` (test only).

### `app/src/main/AndroidManifest.xml`

Declares the permission set — exact alarms split by SDK (`USE_EXACT_ALARM` plus `SCHEDULE_EXACT_ALARM` capped at `maxSdkVersion="32"`), notifications, full-screen intent, wakelock, vibrate, boot, and `FOREGROUND_SERVICE` with the `MEDIA_PLAYBACK` and `SYSTEM_EXEMPTED` type permissions — and five components:

- `MainActivity` — exported launcher.
- `AlarmActivity` — not exported, `excludeFromRecents`, `launchMode="singleInstance"`, `showWhenLocked` + `turnScreenOn` (manifest counterparts to the runtime calls).
- `AlarmReceiver` — not exported, no filter; fired only via `PendingIntent`.
- `BootReceiver` — not exported, filtered on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- `AlarmService` — not exported, `foregroundServiceType="systemExempted|mediaPlayback"`.

### Resources (`app/src/main/res/`)

- `values/strings.xml` — one string, `app_name` = "Sleep Alarm".
- `values/themes.xml` — `Theme.SleepAlarm` extends `Theme.Material.NoActionBar` with a dark `windowBackground` matching Compose's `darkColorScheme()` surface, so neither cold launch nor the lockscreen alarm flashes white.
- `values/colors.xml`, `drawable/ic_launcher*.xml`, `mipmap-anydpi-v26/ic_launcher.xml` — colors and the vector launcher icon (adaptive on API 26+).

---

## Production source (`app/src/main/java/com/sleepalarm/`)

### `WakeTimeCalculator.kt` — time arithmetic

An `object` with zero Android imports (only `java.time` and `kotlin.math`). Two functions:

- `bedtimeMillis(sleepNow: Boolean, nowMillis: Long, bedtime: LocalTime, zone: ZoneId): Long` — returns `nowMillis` for "sleep now"; otherwise the next occurrence of `bedtime` in `zone`: today if it hasn't passed yet, tomorrow otherwise. A bedtime *exactly* equal to now stays today — the check is `isBefore`, not `<=`.
- `wakeMillis(bedtimeMillis: Long, sleepHours: Float): Long` — adds `sleepHours * 3_600_000`, using `roundToLong()` so half-hour steps don't accumulate float error.

Fully covered by `WakeTimeCalculatorTest`.

### `MathChallenge.kt` — problem generation

`data class MathProblem(question: String, answer: Int)` plus `object MathChallenge` with `enum Difficulty { EASY, MEDIUM, HARD }` and two factories:

- `forSnooze(difficulty = MEDIUM)` — addition, `"$a + $b = ?"`, both operands from a per-difficulty range (EASY `2..19`, MEDIUM `12..89`, HARD `25..199`).
- `forDismiss(difficulty = MEDIUM)` — `"$a × $b + $c = ?"` with per-difficulty `Triple`s of ranges (the table is in DESIGN.md).

Fully covered by `MathChallengeTest` — format, answer correctness, and answer bounds, 500 samples per case.

### `AlarmSettings.kt` — persisted user options

A thin wrapper over the `SharedPreferences` file `sleep_alarm_prefs`. The constant `PREFS_NAME` is shared with `AlarmScheduler` so the whole app uses one prefs file. Two `var` properties backed by prefs:

- `snoozeMinutes: Int` — default `DEFAULT_SNOOZE_MINUTES = 5`; the UI choices come from `SNOOZE_CHOICES = listOf(5, 10, 15)`.
- `difficulty: MathChallenge.Difficulty` — stored as the enum *name*; the getter matches with `firstOrNull` and falls back to `MEDIUM` on unknown values instead of throwing.

### `AlarmScheduler.kt` — scheduling, snooze, persistence

Constructed per call site with a `Context`. Key members:

- `canScheduleExact()` — always true below API 31, otherwise `alarmManager.canScheduleExactAlarms()`.
- `schedule(triggerAtMillis): Boolean` — returns `false` without the exact-alarm permission; otherwise `setAlarmClock(AlarmClockInfo(trigger, showIntent), alarmPendingIntent())` and persists `wake_time_millis`. The show intent opens `MainActivity` from the system alarm indicator.
- `scheduleWithFallback(triggerAtMillis)` — tries `schedule`; on refusal falls back to `setWindow(RTC_WAKEUP, trigger, 10 * 60_000L, ...)`. Snoozes and boot-reschedules get delayed at worst, never dropped. Still persists the wake time.
- `snooze()` — reads `AlarmSettings.snoozeMinutes` fresh and calls `scheduleWithFallback(now + minutes)`.
- `cancel()` / `clearStored()` — `cancel` removes the AlarmManager registration *and* clears prefs; `clearStored` only clears prefs, and is what dismiss uses (the alarm has already fired, so there's nothing to cancel).
- `scheduledWakeTime(): Long?` — the stored value if `> 0`, else `null`.
- `alarmPendingIntent()` — the single broadcast `PendingIntent` identity: request code `1001`, target `AlarmReceiver`, `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`. Because the identity is the same everywhere, re-scheduling replaces and cancel finds it.

### `MainActivity.kt` — setup screen and permissions

A `ComponentActivity` hosting the `SleepAlarmScreen` composable.

At the activity level:

- `notificationPermission`, an `ActivityResultContracts.RequestPermission` launcher: on denial it toasts an explanation, and if rationale is unavailable on API 33+ ("don't ask again"), it deep-links to `ACTION_APP_NOTIFICATION_SETTINGS`.
- `onCreate` requests `POST_NOTIFICATIONS` only when `savedInstanceState == null` — a fresh launch — so rotations don't bounce a permanently-denied user to Settings over and over.
- `activeWakeTime` is a `mutableStateOf<Long?>` refreshed in `onResume()` from `scheduler.scheduledWakeTime()`, so a snooze or dismissal that happened over in `AlarmActivity` shows up when the user comes back.
- The `onSetAlarm` callback: if `scheduler.schedule(wakeMillis)` succeeds, update the card and run `warnIfFullScreenIntentBlocked()`; if it fails, `requestExactAlarmPermission()` toasts and opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 31+).
- `warnIfFullScreenIntentBlocked()` (API 34+ only) checks `NotificationManager.canUseFullScreenIntent()`; when blocked, it toasts and opens `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` for this package.

`SleepAlarmScreen`'s state: `sleepNow` (Boolean, default true), `bedtime` (default 23:00), `sleepHours` (Float, default 8f; slider `1f..12f` with `steps = 21`, which gives the 0.5-hour increments), `showTimePicker`, `now` (ticked every second by a `LaunchedEffect` for the live preview), `snoozeMinutes`, `difficulty`. Helpers: `wakeMillisAt(nowMillis)` composes the two `WakeTimeCalculator` calls, and `formatMillis` uses the pattern `"EEE HH:mm"`. It renders the active-alarm card (only when the stored time is in the future) with **Cancel alarm**, the bedtime `FilterChip`s ("Right now" / "Pick a time", which becomes "At HH:mm"), the duration slider with the live "You would wake up at" preview, the **Set alarm** button, the snooze-length chips, and the difficulty chips. The time picker is a Material 3 `TimePicker` inside an `AlertDialog`, 24-hour mode.

### `AlarmReceiver.kt` — the alarm broadcast entry point

A single `onReceive`: acquire a `PARTIAL_WAKE_LOCK` named `"SleepAlarm:receiver"` with a 10-second timeout, then `ContextCompat.startForegroundService` with `ACTION_START`. The lock is intentionally *not* released in `onReceive` (annotated `@SuppressLint("Wakelock")` with a comment): the service start is only enqueued at that point, and on aggressive OEMs the device could sleep before `AlarmService` reaches `startForeground` and takes its own lock.

### `AlarmService.kt` — the ringer

Constants: `ACTION_START`, `ACTION_STOP`, `ACTION_AUTO_SNOOZED`; channel `alarm_channel`; notification ID `42`; `MAX_RING_MILLIS = 10 min`; `VOLUME_RAMP_MILLIS = 60 s` over `VOLUME_RAMP_STEPS = 20`.

- `onStartCommand` — `ACTION_STOP` means `stopRinging(); stopSelf()`; anything else means `startAsForeground(); startRinging()`. Returns `START_NOT_STICKY`, since a system-restarted service with no intent shouldn't spontaneously start ringing.
- `startAsForeground()` — creates the channel (HIGH importance; channel sound and vibration disabled because the service produces both itself; `setBypassDnd(true)`), then calls `startForeground` with the SDK-appropriate type: `SYSTEM_EXEMPTED` on API 34+, `MEDIA_PLAYBACK` on 29–33, untyped below that.
- `buildNotification(contentText)` — `CATEGORY_ALARM`, ongoing, full-screen intent *and* content intent both pointing at `AlarmActivity`. The text is a parameter so `notifySoundUnavailable()` can re-post the same notification explaining vibration-only mode.
- `startRinging()` — guarded by the explicit `ringing` flag (not by `mediaPlayer != null`, which stays null in the fallback). The order matters: acquire the wakelock → arm the 10-minute `autoSnooze` timer → start vibration (waveform `[0, 800, 400]` repeating; `VibratorManager.defaultVibrator` on API 31+, the legacy `VIBRATOR_SERVICE` below) → resolve the ringtone URI (`TYPE_ALARM`, falling back to `TYPE_RINGTONE`, then vibration-only) → configure `MediaPlayer` (`USAGE_ALARM`/`CONTENT_TYPE_SONIFICATION`, looping) inside try/catch → `startVolumeRamp`.
- `startVolumeRamp(player)` — steps volume from 1/20 to 20/20 over 60 seconds via a self-reposting `Runnable` on the main-looper `Handler`. It bails if `mediaPlayer !== player` (a new or stopped session) and swallows `IllegalStateException` from a released player.
- `autoSnooze()` — `AlarmScheduler(this).snooze()`, stop ringing, broadcast `ACTION_AUTO_SNOOZED` scoped with `setPackage(packageName)`, `stopSelf()`.
- `stopRinging()` — idempotent teardown: clear the flag, `removeCallbacksAndMessages(null)` (kills both timers), stop/release the `MediaPlayer` (guarding `stop()`), cancel vibration, release the wakelock if held. Also called from `onDestroy`.
- `acquireWakeLock()` — a `PARTIAL_WAKE_LOCK` named `"SleepAlarm:ringing"` with a 1-hour safety cap.

### `AlarmActivity.kt` — the math-gated wake-up screen

A private `enum PendingAction { SNOOZE, DISMISS }` tracks which challenge is active.

At the activity level:

- `showOverLockscreen()` — on API 27+: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `KeyguardManager.requestDismissKeyguard`; below that, the deprecated window flags. `FLAG_KEEP_SCREEN_ON` is always added.
- `autoSnoozeReceiver` — a runtime receiver for `ACTION_AUTO_SNOOZED` (registered `RECEIVER_NOT_EXPORTED`, unregistered in `onDestroy`) that just calls `finish()`, so the screen doesn't linger over the lockscreen after the service went quiet on its own.
- System back is disabled via an always-enabled `OnBackPressedCallback` with an empty handler.
- The callbacks wired into the composable: `onSnooze` does `AlarmScheduler(this).snooze()` then `stopAlarmAndFinish()`; `onDismiss` does `AlarmScheduler(this).clearStored()` then `stopAlarmAndFinish()`. `stopAlarmAndFinish()` sends `ACTION_STOP` to the service and finishes.

The `AlarmScreen` composable: a live `HH:mm` clock (per-second `LaunchedEffect`), "Time to wake up!", then either the two entry buttons — **Snooze (N min)** (outlined) and **Dismiss** (filled) — or, once `beginChallenge(action)` has run, the active problem: a prompt ("Solve to snooze/dismiss"), the question at 40 sp, a digits-only `OutlinedTextField` (input filtered with `it.filter { ch -> ch.isDigit() }`, numeric keyboard), a red "Wrong! New problem. (N failed)" line after failures, and **Back** / **Submit**. Back abandons the challenge and returns to the buttons — the alarm keeps ringing. `submit()` parses with `toIntOrNull()`; correct invokes the pending callback, wrong increments `wrongAttempts`, generates a fresh problem, and clears the input.

### `BootReceiver.kt` — reboot/update recovery

Ignores anything other than `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`. Reads `scheduledWakeTime()`: if it's in the future, `scheduleWithFallback(wakeTime)` re-arms the alarm (the fallback path, because on API 31–32 the exact-alarm grant may not be restored yet at boot); if it's in the past, `clearStored()` removes the stale record.

---

## Tests (`app/src/test/java/com/sleepalarm/`)

### `WakeTimeCalculatorTest.kt`

Seven JUnit 4 tests, all in UTC: sleep-now returns now regardless of bedtime; a bedtime later today stays today; a bedtime already passed rolls to tomorrow; an after-midnight bedtime chosen late at night lands on the next calendar date; a bedtime exactly equal to now stays today; `wakeMillis` for whole hours; `wakeMillis` for half-hour steps.

### `MathChallengeTest.kt`

Five property-style tests, 500 iterations each: snooze and dismiss questions match their expected regexes (`(\d+) \+ (\d+) = \?` and `(\d+) × (\d+) \+ (\d+) = \?`) and the stated `answer` equals the recomputed value; snooze and dismiss answers stay within per-difficulty bounds derived from the operand ranges; the no-argument overloads behave like MEDIUM.

Run with `gradlew.bat test` (see SETUP.md).

---

## One alarm, end to end

1. `MainActivity` → `WakeTimeCalculator.bedtimeMillis` + `wakeMillis` → `AlarmScheduler.schedule` → `AlarmManager.setAlarmClock` plus a prefs write.
2. At wake time: `AlarmManager` → `AlarmReceiver.onReceive` (wakelock) → `AlarmService` (`ACTION_START`) → foreground + vibration + ramped alarm sound + full-screen notification.
3. The system launches `AlarmActivity` over the lockscreen → the user picks Snooze or Dismiss → `MathChallenge.forSnooze`/`forDismiss` → a correct answer → `AlarmScheduler.snooze()` or `clearStored()` → `ACTION_STOP` to the service → everything tears down.
4. If the phone reboots somewhere in between: `BootReceiver` → `AlarmScheduler.scheduleWithFallback` from the persisted wake time.
