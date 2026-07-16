# Use Cases

Where Sleep Alarm is useful today, what shipping it seriously would involve, and what a commercial version would take. Claims about "today" describe the actual code — a local-only, single-alarm, no-network Android app. Anything under "would need building" doesn't exist in the codebase yet.

## Part A — Production use cases

### A1. A reliability alarm for heavy sleepers

The core consumer use case, and the current code fully serves it. A user who sleeps through or reflexively dismisses normal alarms gets a duration-first mental model, cognitive gating (regenerating math problems on wrong answers), and a ring path engineered around Android's hardest reliability problems: Doze exemption via `setAlarmClock`, foreground-service ringing, full-screen intent over the lockscreen, reboot recovery, and vibration-first fallback so the alarm can never fail silently.

### A2. Shift workers and irregular schedules

The "sleeping right now, wake me in N hours" model fits people whose sleep is anchored to when work ends, not to a fixed clock time — nurses, drivers, on-call engineers sleeping between pages. Setting "8 hours from now" at 03:40 takes no arithmetic. The next-occurrence bedtime rollover (choosing a 23:00 bedtime at 23:45 means tomorrow) is handled and unit-tested.

### A3. Reference implementation for Android teams

Probably the strongest organizational use case as-is. The repo is a compact (~1,100 lines, zero third-party dependencies) and current (targetSdk 35, Kotlin 2.0, Compose Material 3) demonstration of a famously fiddly Android stack:

- The exact-alarm permission split (`USE_EXACT_ALARM` on API 33+ vs `SCHEDULE_EXACT_ALARM` capped at 32) with a graceful `setWindow` fallback.
- Foreground-service type selection by SDK level (`systemExempted` on 34+, `mediaPlayback` on 29–33), matching the manifest declaration.
- Full-screen-intent handling, including the Android 14 `canUseFullScreenIntent()` revocation check.
- The receiver-to-service wakelock handoff (a timed lock in `onReceive`, because service start is only enqueued there).
- Notification-permission UX including the permanent-denial → Settings deep-link path.
- Pure-logic extraction (`WakeTimeCalculator`, `MathChallenge`) for JVM-only unit testing.

Any team building something alarm-, reminder-, or medication-adjacent can lift these patterns directly.

### A4. A base for compliance-critical wake/attention apps

The ring path — guaranteed audible/tactile alert, proof of cognition before dismissal, auto-snooze instead of infinite ringing — generalizes beyond wake-ups: medication reminders, insulin or feeding timers, on-call acknowledgment. The math gate is essentially a cognition attestation. To be clear about the current limits: there's one alarm, no event logging, and no escalation to another person. Those would all be new code.

### What shipping this broadly involves

**Distribution.** There's no signing config in the build (`isMinifyEnabled = false`, no `signingConfigs`). Play distribution needs a release keystore, ideally R8 enabled, and Play's declared-permission review for `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` — both are policy-gated to genuinely alarm-like apps, which this one plainly is.

**OEM fragmentation.** The code already defends against aggressive power management (the wakelock handoff, `setAlarmClock`), but vendors like Xiaomi and Huawei layer proprietary app-killers on top. Supporting those in production means device-specific whitelisting guidance and telemetry to know when alarms failed. Today there is no telemetry of any kind — which is also a privacy selling point.

**Reliability posture.** The failure-degradation ladder (exact → windowed; sound → vibration; unanswered → auto-snooze) is solid production thinking. What's missing for production *confidence* is instrumented tests on real API levels and a crash reporter.

**Scaling.** Not applicable in the server sense — the app is fully client-side. Scale here means device-matrix breadth, not throughput.

**Privacy and compliance.** A very good starting point: no network permission, no accounts, no analytics, a handful of locally stored preferences. The privacy policy would be nearly one sentence. `allowBackup="true"` means prefs ride along in device backups.

## Part B — SaaS potential

### What exists vs. what a SaaS needs

Nothing in this codebase is a service. There's no server, no API, no account system, no sync, no billing surface. The realistic path is app-first monetization with an optional cloud layer, not converting this code into a hosted product. That said, the concept has real commercial analogs — Alarmy is a top-grossing app built on exactly this "prove you're awake" premise — so the niche itself is validated.

### Target customers

1. **Consumers (B2C)** — heavy sleepers, students, shift workers. The existing app maps 1:1 onto this segment.
2. **Digital-health / sleep-coaching programs (B2B2C)** — clinics and corporate-wellness vendors that want adherence-verified wake schedules as part of CBT-I or fatigue-management programs. Needs data export and program-admin views, all new.
3. **Duty-of-care / on-call organizations (B2B)** — transport, healthcare, field ops that need *acknowledged* wake-ups with an audit trail and escalation ("if not dismissed in 15 minutes, alert the supervisor"). The math gate becomes an accountability primitive. This is entirely new backend territory, but the on-device ring path is the hard part and it already works.

### Product shapes and pricing

- **Freemium app** (closest to today): free single alarm; a subscription (~$2–4/mo, or a one-time unlock) for premium features that would need building — multiple alarms, alternative challenges (shake/photo/steps), custom sounds, wake-up statistics. No servers required; billing via Play Billing.
- **"Wake-up cloud" subscription**: account plus encrypted settings/history sync, cross-device, weekly sleep-consistency reports, an optional "accountability partner" notification when you oversleep. Per-user monthly pricing; the backend stays thin (auth, a small per-user datastore, push).
- **B2B adherence API**: per-seat pricing; the app posts signed wake/dismiss/snooze events to a tenant's webhook, with a dashboard and escalation rules. Priced per active user per month, as is standard in workforce-safety SaaS.

### Multi-tenancy

Today's storage is one local `SharedPreferences` file — there is no tenancy concept at all. A SaaS layer would need tenant-scoped user identity (OIDC), row-level tenant isolation in the event store, tenant-configurable policy pushed to devices (mandated difficulty or snooze caps for a fatigue-management program, say), and per-tenant data-retention and export controls. On the device side, MDM deployment would need a managed-config surface (Android Enterprise `RestrictionsManager`). None of this exists.

### What would need hardening

| Area | Today | Needed for SaaS |
|---|---|---|
| Auth | None (no accounts) | OIDC/OAuth sign-in, token storage, device binding |
| Network | No `INTERNET` permission; zero network code | API client, retry/offline queue for events, certificate pinning |
| Data | A few prefs values, local only | Event log (alarm set/fired/snoozed/dismissed, failed attempts — `wrongAttempts` is already counted in the UI but discarded), sync, a server datastore |
| Isolation | N/A (single user, single device) | Tenant isolation, per-tenant keys, RBAC for dashboards |
| Billing | None | Play Billing (consumer) and/or Stripe plus an entitlement service (B2B) |
| Compliance | Trivially private | Privacy policy, GDPR/CCPA (sleep data is sensitive-adjacent behavioral data), SOC 2 for B2B buyers, care with health positioning (avoid clinical claims or face regulatory scope) |
| Reliability evidence | Solid design, JVM unit tests only | An instrumented test matrix across API 26–35 and major OEMs, crash/ANR reporting, alarm-delivery telemetry — the core B2B promise is "it rang and was acknowledged," and that has to be measurable |
| Anti-circumvention | Math gate only; force-stop or uninstall defeats it | For accountability use cases: detect and report non-delivery (a server-side timeout triggers escalation), which conveniently doesn't require preventing circumvention on-device |
| Platform breadth | Android only | iOS is materially harder — there's no true third-party equivalent of a full-screen alarm activity, and critical alerts need an entitlement. A real roadmap constraint for B2B deals |

### Bottom line

As shipped, Sleep Alarm is a well-engineered, privacy-clean, single-purpose consumer utility and a good reference codebase. Its commercial ceiling as-is is app-store freemium. The defensible SaaS angle — verified wake-up acknowledgment with escalation, sold to organizations — builds directly on the codebase's real strength (a ring path that survives Doze, reboots, broken ringtones, and groggy users), but requires an entire cloud product around it: auth, events, tenancy, billing, compliance.
