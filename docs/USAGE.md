# Usage Guide

Sleep Alarm has exactly two screens: the setup screen (`MainActivity`) and the wake-up screen (`AlarmActivity`, which you only ever see while an alarm is ringing). This covers every control on both, the permission prompts you might hit, and what to do when something looks wrong.

## First launch

On Android 13+ the app immediately asks for notification permission. Grant it — without notifications, neither the foreground-service notification nor the full-screen wake-up screen can appear. If you deny it permanently, the app explains the consequence in a toast and opens the system notification settings for Sleep Alarm so you can flip it on manually.

## The setup screen

One scrollable dark-theme column, top to bottom:

### Active-alarm card

Only shown when a future alarm exists: "**Alarm set for**" with the wake time formatted like `Sat 07:00` (day of week plus 24-hour time) and a **Cancel alarm** button. Cancelling removes the alarm from the system and from storage, and the card goes away. The card also updates correctly after you snooze from the wake-up screen and come back — the activity re-reads the stored wake time on every resume.

### "When are you going to sleep?"

Two chips:

- **Right now** (the default) — bedtime is this moment, and the previewed wake time ticks forward each second with the clock.
- **Pick a time** — opens a 24-hour Material time picker titled "Bedtime". After you confirm, the chip label changes to **At HH:mm** (say, "At 23:00"). If that time already passed today, the app treats it as tomorrow's occurrence.

### "How long do you want to sleep?"

A large readout (e.g. `8.0 hours`) and a slider from 1 to 12 hours in half-hour steps. Default is 8.

### "You would wake up at"

A live preview of bedtime + duration, in the same `Sat 07:00` format, updated every second.

### Set alarm

A full-width button. Pressing it recomputes the wake time at the instant of the tap, schedules an exact alarm (`AlarmManager.setAlarmClock`), and shows the active-alarm card. On Android 14+ it also checks whether full-screen notifications are allowed; if not, you get a toast ("Allow full-screen notifications so the alarm can wake the screen.") and the relevant settings page opens.

If exact alarms aren't permitted (possible on Android 12/12L), nothing gets scheduled. Instead you get "Allow exact alarms in settings, then set the alarm again." and the exact-alarm settings screen opens. Enable it, come back, press **Set alarm** again.

Note that setting a new alarm replaces any existing one — the app manages a single alarm.

### Snooze length

Chips: 5 / 10 / 15 minutes (default 5). Saved immediately; applies to the next snooze, including auto-snooze.

### Math difficulty

Chips: Easy / Medium / Hard (default Medium). Saved immediately. This controls the number ranges you'll face on the wake-up screen:

| Difficulty | Snooze problem | Dismiss problem |
|---|---|---|
| Easy | `a + b`, operands 2–19 (e.g. `7 + 12 = ?`) | `a × b + c`, e.g. `9 × 3 + 14 = ?` |
| Medium | operands 12–89 (e.g. `47 + 68 = ?`) | e.g. `23 × 7 + 45 = ?` |
| Hard | operands 25–199 (e.g. `152 + 87 = ?`) | e.g. `48 × 11 + 260 = ?` |

## When the alarm fires

At the scheduled time, the phone wakes up (this works from Doze — `setAlarmClock` is exempt). A foreground notification appears ("**Wake up!** — Solve the math problem to stop the alarm."), vibration starts, and the system alarm sound begins looping — quiet at first, ramping to full volume over 60 seconds. Sound plays on the alarm volume stream and cuts through Do Not Disturb.

The wake-up screen launches over the lockscreen and the display turns on and stays on. If full-screen launch is blocked on your device, tap the notification — it opens the same screen.

The screen shows a large live clock (`HH:mm`), "Time to wake up!", and two buttons: **Snooze (N min)** — N being your configured snooze length — and **Dismiss**. Tapping either replaces the buttons with a math challenge:

- A header: "Solve to snooze" or "Solve to dismiss".
- The problem in large text, e.g. `47 + 68 = ?` for snooze or `23 × 7 + 45 = ?` for dismiss.
- A numeric **Answer** field that only accepts digits.
- **Back**, which abandons the challenge and returns to the two buttons (the alarm keeps ringing).
- **Submit**. Correct on snooze: the alarm goes quiet and fires again after your snooze length. Correct on dismiss: the alarm stops and the stored alarm is cleared. Wrong: a red line appears — "Wrong! New problem. (N failed)" — and you get a fresh problem, so you can't guess your way through one fixed sum.

While it rings, the system back button/gesture does nothing — math is the only in-app way out — and the alarm screen never shows up in the recents list.

### Auto-snooze

If nobody solves anything for 10 minutes, the alarm snoozes itself using your configured snooze length, goes quiet, and the wake-up screen closes on its own. It rings again after the snooze interval. This is what stops an unattended phone from ringing until the battery dies.

### No alarm sound?

If the device has no usable alarm ringtone (and no fallback ringtone either), the alarm still fires with vibration only, and the notification text changes to "Alarm sound unavailable — vibrating only. Solve the math problem to stop." Fresh emulator images hit this a lot.

## Reboots and app updates

If the phone reboots or the app updates while an alarm is pending, the alarm is re-scheduled automatically at boot/update from stored state. If the wake time already passed while the phone was off, the stale alarm is discarded — it won't fire late out of nowhere.

## Troubleshooting

| Problem | What's going on |
|---|---|
| Alarm never rang | Check in order: (1) notification permission granted; (2) on Android 12/12L, "Alarms & reminders" (exact alarm) allowed — the app prompts for this when you set the alarm; (3) the app wasn't force-stopped from Settings afterwards (force-stop cancels AlarmManager alarms for any app); (4) some OEM "battery savers" kill apps aggressively — whitelist Sleep Alarm. |
| Alarm rang but no math screen appeared | Full-screen notifications are blocked (Android 14+). Tap the ringing notification to open the wake-up screen, then allow full-screen notifications for Sleep Alarm — the app deep-links you there whenever you set an alarm while it's blocked. |
| Alarm is quiet at first | By design — volume ramps from low to full over the first 60 seconds. |
| Alarm only vibrates | No default alarm sound on the device (the notification says so). Set one in the system Sound settings. |
| Alarm rings during Do Not Disturb | Intentional: the channel bypasses DND and audio uses the alarm stream. Lower the *alarm* volume if you want it quieter — the app can't ring louder than whatever alarm volume you've set. |
| Snoozed alarm fired a few minutes late | If exact-alarm permission got revoked *after* setup, snoozes and post-reboot re-schedules fall back to a windowed alarm that can be up to ~10 minutes late (rather than not firing at all). Re-grant "Alarms & reminders". |
| "Wrong! New problem." keeps coming | Every wrong answer generates a new problem on purpose. If Medium/Hard is too much when groggy, lower the difficulty on the setup screen before your next alarm. |
| I want two alarms | Not supported — a new alarm replaces the current one. |
| Can I stop it without math? | In the app, no — the two math challenges and the 10-minute auto-snooze are the only paths. OS-level escape hatches (force-stop, power off) are outside the app's control. |
