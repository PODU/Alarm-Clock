package com.sleepalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var scheduler: AlarmScheduler
    private lateinit var store: AlarmStore
    private lateinit var settings: AlarmSettings
    private lateinit var historyStore: HistoryStore

    private var alarms by mutableStateOf(listOf<Alarm>())

    private var historyEvents by mutableStateOf(listOf<HistoryEvent>())

    private var alarmSoundUri by mutableStateOf<String?>(null)

    private var batteryExempt by mutableStateOf(true)
    private var batteryPromptDismissed by mutableStateOf(false)

    private var soundPickAlarmId: Int? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        lifecycleScope.launch(Dispatchers.Default) {
            val latestAlarms = store.load()
            val latestEvents = historyStore.events()
            withContext(Dispatchers.Main) {
                if (latestAlarms != alarms) alarms = latestAlarms
                if (latestEvents != historyEvents) historyEvents = latestEvents
            }
        }
    }

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val targetAlarmId = soundPickAlarmId
            soundPickAlarmId = null
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val stored = picked
                ?.takeUnless { it == Settings.System.DEFAULT_ALARM_ALERT_URI }
                ?.toString()
            if (targetAlarmId == null) {
                alarmSoundUri = stored
                settings.alarmSoundUri = stored
            } else {
                store.get(targetAlarmId)?.let { amendAlarm(it.copy(soundUri = stored)) }
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(R.string.notifications_denied_warning),
                    Toast.LENGTH_LONG
                ).show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                }
            }
        }

    private val activityRecognitionPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    contentResolver.openOutputStream(uri)!!.use { stream ->
                        stream.write(buildBackupJson().toString(2).toByteArray())
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            if (result.isSuccess) R.string.backup_saved
                            else R.string.backup_save_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val text = contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().decodeToString() }
                    applyBackupJson(JSONObject(text))
                }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.backup_restored),
                            Toast.LENGTH_SHORT
                        ).show()
                        recreate()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.backup_restore_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        scheduler = AlarmScheduler(this)
        store = AlarmStore(this)
        settings = AlarmSettings(this)
        historyStore = HistoryStore(this)
        alarmSoundUri = settings.alarmSoundUri
        batteryPromptDismissed = settings.batteryPromptShown
        batteryExempt = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)

        store.pruneExpiredOneShots()

        scheduler.rescheduleAll()

        setContent {
            SleepAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NightColors.Background
                ) {
                    SleepAlarmScreen(
                        alarms = alarms,
                        initialSleepNow = settings.sleepNow,
                        initialBedtime = LocalTime.ofSecondOfDay(
                            settings.bedtimeSecondOfDay.toLong()
                        ),
                        initialSleepHours = settings.sleepHours,
                        initialFallAsleepMinutes = settings.fallAsleepMinutes,
                        initialProfile = settings.activeProfile,
                        initialSnoozeMinutes = settings.snoozeMinutes,
                        initialDifficulty = settings.difficulty,
                        initialChallengeType = settings.challengeType,
                        initialWakeUpCheckMinutes = settings.wakeUpCheckMinutes,
                        initialMaxSnoozes = settings.maxSnoozes,
                        initialMaxRingMinutes = settings.maxRingMinutes,
                        initialVibrateEnabled = settings.vibrateEnabled,
                        initialVolumeRampSeconds = settings.volumeRampSeconds,
                        initialSnoozeEscalationMinutes = settings.snoozeEscalationMinutes,
                        initialGentleWakeMinutes = settings.gentleWakeMinutes,
                        initialBedtimeReminder = settings.bedtimeReminderEnabled,
                        alarmSoundUri = alarmSoundUri,
                        historyEvents = historyEvents,
                        batteryExempt = batteryExempt,
                        showBatteryCard = !batteryExempt && !batteryPromptDismissed,
                        onBatteryAllow = ::requestBatteryExemption,
                        onBatteryDismiss = {
                            settings.batteryPromptShown = true
                            batteryPromptDismissed = true
                        },
                        onSetAlarm = ::addAlarm,
                        onSetAlarmAtTime = ::addAlarmAt,
                        onEditAlarmTime = ::editAlarmTime,
                        onToggleAlarm = { alarm, enabled ->
                            updateAlarm(alarm.copy(enabled = enabled))
                        },
                        onRepeatDaysChange = { alarm, days ->
                            updateAlarm(alarm.copy(repeatDays = days))
                        },
                        onLabelChange = { alarm, label ->
                            amendAlarm(alarm.copy(label = label))
                        },
                        onSkipNextChange = ::setSkipNext,
                        onPickAlarmSoundFor = ::launchRingtonePickerFor,
                        onAlarmChallengeChange = { alarm, type ->
                            if (type == ChallengeType.STEPS) ensureStepsPermission()
                            amendAlarm(alarm.copy(challengeType = type))
                        },
                        onAlarmDifficultyChange = { alarm, level ->
                            amendAlarm(alarm.copy(difficulty = level))
                        },
                        onDeleteAlarm = ::deleteAlarm,
                        onCancelAlarm = ::cancelAlarm,
                        onOpenAlarm = {
                            startActivity(Intent(this, AlarmActivity::class.java))
                        },
                        onSleepNowChange = { settings.sleepNow = it },
                        onBedtimeChange = {
                            settings.bedtimeSecondOfDay = it.toSecondOfDay()
                            if (settings.bedtimeReminderEnabled) {
                                rescheduleBedtimeReminder(settings)
                            }
                        },
                        onSleepHoursChange = { settings.sleepHours = it },
                        onFallAsleepChange = { settings.fallAsleepMinutes = it },
                        onProfileChange = { profile ->
                            settings.activeProfile = profile
                            if (settings.bedtimeReminderEnabled) {
                                rescheduleBedtimeReminder(settings)
                            }
                            LocalTime.ofSecondOfDay(
                                settings.bedtimeSecondOfDay.toLong()
                            ) to settings.sleepHours
                        },
                        onSnoozeChange = { settings.snoozeMinutes = it },
                        onDifficultyChange = { settings.difficulty = it },
                        onChallengeTypeChange = {
                            settings.challengeType = it
                            if (it == ChallengeType.STEPS) ensureStepsPermission()
                        },
                        onWakeUpCheckChange = { settings.wakeUpCheckMinutes = it },
                        onMaxSnoozesChange = { settings.maxSnoozes = it },
                        onMaxRingChange = { settings.maxRingMinutes = it },
                        onVibrateChange = { settings.vibrateEnabled = it },
                        onVolumeRampChange = { settings.volumeRampSeconds = it },
                        onSnoozeEscalationChange = { settings.snoozeEscalationMinutes = it },
                        onGentleWakeChange = { minutes ->
                            settings.gentleWakeMinutes = minutes
                            scheduler.rescheduleAll()
                        },
                        onPickAlarmSound = ::launchRingtonePicker,
                        onBedtimeReminderChange = { enabled ->
                            settings.bedtimeReminderEnabled = enabled
                            if (enabled) {
                                rescheduleBedtimeReminder(settings)
                            } else {
                                scheduler.cancelBedtimeReminder()
                            }
                        },
                        onClearHistory = {
                            historyStore.clear()
                            historyEvents = emptyList()
                        },
                        onExport = { exportLauncher.launch("sleep_alarm_backup.json") },
                        onImport = { importLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        alarms = store.load()
        historyEvents = historyStore.events()
        batteryExempt = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
        Prefs.sharedPrefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onPause() {
        Prefs.sharedPrefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun addAlarm(wakeMillis: Long) {
        val local = Instant.ofEpochMilli(wakeMillis).atZone(ZoneId.systemDefault())
        val alarm = Alarm(
            id = store.nextId(),
            triggerAtMillis = wakeMillis,
            hour = local.hour,
            minute = local.minute,
            repeatDays = emptySet(),
            enabled = true
        )
        store.upsert(alarm)
        scheduler.schedule(alarm)
        alarms = store.load()
        QuickAlarmWidgetProvider.updateAll(this)
        Toast.makeText(
            this,
            getString(
                R.string.alarm_set_toast,
                TimeFormats.formatDayClock(this, alarm.triggerAtMillis),
                TimeFormats.formatCountdown(System.currentTimeMillis(), alarm.triggerAtMillis)
            ),
            Toast.LENGTH_LONG
        ).show()
        if (scheduler.canScheduleExact()) {
            warnIfFullScreenIntentBlocked()
        } else {
            warnInexactAlarm()
        }
    }

    private fun addAlarmAt(hour: Int, minute: Int) {
        addAlarm(
            AlarmTimeCalculator.nextTrigger(
                hour, minute, emptySet(), System.currentTimeMillis(), ZoneId.systemDefault()
            )
        )
    }

    private fun editAlarmTime(alarm: Alarm, hour: Int, minute: Int) {
        updateAlarm(
            alarm.copy(
                hour = hour,
                minute = minute,
                triggerAtMillis = AlarmTimeCalculator.nextTrigger(
                    hour, minute, alarm.repeatDays,
                    System.currentTimeMillis(), ZoneId.systemDefault()
                )
            )
        )
    }

    private fun updateAlarm(alarm: Alarm) {
        val updated = if (alarm.enabled) {
            alarm.copy(triggerAtMillis = nextTriggerFor(alarm), skipNext = false)
        } else {
            alarm
        }
        persistAndSchedule(updated)
    }

    private fun amendAlarm(alarm: Alarm) = persistAndSchedule(alarm)

    private fun persistAndSchedule(alarm: Alarm) {
        store.upsert(alarm)
        if (alarm.enabled) scheduler.schedule(alarm) else scheduler.cancel(alarm)
        alarms = store.load()
        QuickAlarmWidgetProvider.updateAll(this)
    }

    private fun setSkipNext(alarm: Alarm, skip: Boolean) {
        if (alarm.repeatDays.isEmpty()) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val trigger = if (skip) {
            AlarmTimeCalculator.nextTriggerSkippingOne(
                alarm.hour, alarm.minute, alarm.repeatDays, now, zone
            )
        } else {
            AlarmTimeCalculator.nextTrigger(
                alarm.hour, alarm.minute, alarm.repeatDays, now, zone
            )
        }
        persistAndSchedule(alarm.copy(triggerAtMillis = trigger, skipNext = skip))
    }

    private fun nextTriggerFor(alarm: Alarm): Long {
        val now = System.currentTimeMillis()
        if (alarm.repeatDays.isEmpty() && alarm.triggerAtMillis > now) {
            return alarm.triggerAtMillis
        }
        return AlarmTimeCalculator.nextTrigger(
            alarm.hour, alarm.minute, alarm.repeatDays, now, ZoneId.systemDefault()
        )
    }

    private fun rescheduleBedtimeReminder(settings: AlarmSettings) {
        scheduler.scheduleBedtimeReminder(
            AlarmTimeCalculator.nextOccurrence(
                settings.bedtimeSecondOfDay,
                System.currentTimeMillis(),
                ZoneId.systemDefault()
            )
        )
    }

    private fun launchRingtonePicker() {
        soundPickAlarmId = null
        launchPicker(alarmSoundUri)
    }

    private fun launchRingtonePickerFor(alarm: Alarm) {
        soundPickAlarmId = alarm.id
        launchPicker(alarm.soundUri ?: alarmSoundUri)
    }

    private fun launchPicker(storedUri: String?) {
        val current = storedUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtonePicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                .putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                    getString(R.string.alarm_sound_label)
                )
        )
    }

    private fun deleteAlarm(alarm: Alarm) {
        scheduler.cancelAllFor(alarm.id)
        store.delete(alarm.id)
        alarms = store.load()
        QuickAlarmWidgetProvider.updateAll(this)
    }

    private fun cancelAlarm(alarm: Alarm) {
        if (alarm.repeatDays.isEmpty()) {
            deleteAlarm(alarm)
        } else {
            updateAlarm(alarm.copy(enabled = false))
        }
    }

    private fun ensureStepsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    private fun requestBatteryExemption() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    private fun warnInexactAlarm() {
        Toast.makeText(
            this,
            getString(R.string.inexact_alarm_warning),
            Toast.LENGTH_LONG
        ).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }

    private fun warnIfFullScreenIntentBlocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.canUseFullScreenIntent()) return
        Toast.makeText(
            this,
            getString(R.string.full_screen_intent_warning),
            Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }


    private fun profileValues(profile: SleepProfile): Pair<Int, Float> {
        val previous = settings.activeProfile
        settings.activeProfile = profile
        val values = settings.bedtimeSecondOfDay to settings.sleepHours
        settings.activeProfile = previous
        return values
    }

    private fun setProfileValues(profile: SleepProfile, bedtimeSecondOfDay: Int, sleepHours: Float) {
        val previous = settings.activeProfile
        settings.activeProfile = profile
        settings.bedtimeSecondOfDay = bedtimeSecondOfDay
        settings.sleepHours = sleepHours
        settings.activeProfile = previous
    }

    private fun buildBackupJson(): JSONObject {
        val (weekdayBedtime, weekdayHours) = profileValues(SleepProfile.WEEKDAY)
        val (weekendBedtime, weekendHours) = profileValues(SleepProfile.WEEKEND)
        return JSONObject()
            .put("version", 2)
            .put("alarms", JSONArray(store.load().map { AlarmStore.encode(it) }))
            .put("history", JSONArray(HistoryStore.encodeJson(historyStore.events())))
            .put(
                "settings",
                JSONObject()
                    .put("snoozeMinutes", settings.snoozeMinutes)
                    .put("difficulty", settings.difficulty.name)
                    .put("challengeType", settings.challengeType.name)
                    .put("bedtimeWeekday", weekdayBedtime)
                    .put("sleepHoursWeekday", weekdayHours.toDouble())
                    .put("bedtimeWeekend", weekendBedtime)
                    .put("sleepHoursWeekend", weekendHours.toDouble())
                    .put("maxSnoozes", settings.maxSnoozes)
                    .put("maxRingMinutes", settings.maxRingMinutes)
                    .put("vibrateEnabled", settings.vibrateEnabled)
                    .put("volumeRampSeconds", settings.volumeRampSeconds)
                    .put("wakeUpCheckMinutes", settings.wakeUpCheckMinutes)
                    .put("snoozeEscalationMinutes", settings.snoozeEscalationMinutes)
                    .put("gentleWakeMinutes", settings.gentleWakeMinutes)
                    .put("fallAsleepMinutes", settings.fallAsleepMinutes)
                    .put("bedtimeReminderEnabled", settings.bedtimeReminderEnabled)
                    .put("alarmSoundUri", settings.alarmSoundUri ?: JSONObject.NULL)
            )
    }

    private fun applyBackupJson(json: JSONObject) {
        val importedAlarms = json.optJSONArray("alarms") ?: JSONArray()
        val decoded = (0 until importedAlarms.length())
            .mapNotNull { AlarmStore.decode(importedAlarms.getString(it)) }
        val s = json.optJSONObject("settings") ?: JSONObject()

        val (weekdayBedtime, weekdayHours) = profileValues(SleepProfile.WEEKDAY)
        val (weekendBedtime, weekendHours) = profileValues(SleepProfile.WEEKEND)
        setProfileValues(
            SleepProfile.WEEKDAY,
            s.optInt("bedtimeWeekday", weekdayBedtime),
            s.optDouble("sleepHoursWeekday", weekdayHours.toDouble()).toFloat()
        )
        setProfileValues(
            SleepProfile.WEEKEND,
            s.optInt("bedtimeWeekend", weekendBedtime),
            s.optDouble("sleepHoursWeekend", weekendHours.toDouble()).toFloat()
        )
        settings.snoozeMinutes = s.optInt("snoozeMinutes", settings.snoozeMinutes)
        settings.difficulty = MathChallenge.Difficulty.entries
            .firstOrNull { it.name == s.optString("difficulty") } ?: settings.difficulty
        settings.challengeType = ChallengeType.entries
            .firstOrNull { it.name == s.optString("challengeType") } ?: settings.challengeType
        settings.maxSnoozes = s.optInt("maxSnoozes", settings.maxSnoozes)
        settings.maxRingMinutes = s.optInt("maxRingMinutes", settings.maxRingMinutes)
        settings.vibrateEnabled = s.optBoolean("vibrateEnabled", settings.vibrateEnabled)
        settings.volumeRampSeconds =
            s.optInt("volumeRampSeconds", settings.volumeRampSeconds)
        settings.wakeUpCheckMinutes =
            s.optInt("wakeUpCheckMinutes", settings.wakeUpCheckMinutes)
        settings.snoozeEscalationMinutes =
            s.optInt("snoozeEscalationMinutes", settings.snoozeEscalationMinutes)
        settings.gentleWakeMinutes =
            s.optInt("gentleWakeMinutes", settings.gentleWakeMinutes)
        settings.fallAsleepMinutes =
            s.optInt("fallAsleepMinutes", settings.fallAsleepMinutes)
        settings.bedtimeReminderEnabled =
            s.optBoolean("bedtimeReminderEnabled", settings.bedtimeReminderEnabled)
        if (s.has("alarmSoundUri")) {
            settings.alarmSoundUri =
                if (s.isNull("alarmSoundUri")) null else s.getString("alarmSoundUri")
        }

        json.optJSONArray("history")?.let { array ->
            HistoryStore.decodeJson(array.toString())?.let(historyStore::replaceAll)
        }

        store.save(decoded)
        scheduler.rescheduleAll()
        if (settings.bedtimeReminderEnabled) {
            rescheduleBedtimeReminder(settings)
        } else {
            scheduler.cancelBedtimeReminder()
        }
        QuickAlarmWidgetProvider.updateAll(this)
    }
}

private val LocalTimeSaver = Saver<LocalTime, Int>(
    save = { it.toSecondOfDay() },
    restore = { LocalTime.ofSecondOfDay(it.toLong()) }
)

private const val NO_ALARM_EDIT = 0

private const val MIN_SLEEP_MINUTES = 180
private const val MAX_SLEEP_MINUTES = 720
private const val SLEEP_STEP_MINUTES = 15

private const val CYCLE_MINUTES = 90

private fun sleepOnlyMinutes(totalHours: Float, bufferMinutes: Int): Int {
    val raw = (totalHours * 60).roundToInt() - bufferMinutes
    val snapped =
        (raw + SLEEP_STEP_MINUTES / 2) / SLEEP_STEP_MINUTES * SLEEP_STEP_MINUTES
    return snapped.coerceIn(MIN_SLEEP_MINUTES, MAX_SLEEP_MINUTES)
}

private enum class MainTab { HOME, HISTORY, SETTINGS }

@Composable
private fun SleepAlarmScreen(
    alarms: List<Alarm>,
    initialSleepNow: Boolean,
    initialBedtime: LocalTime,
    initialSleepHours: Float,
    initialFallAsleepMinutes: Int,
    initialProfile: SleepProfile,
    initialSnoozeMinutes: Int,
    initialDifficulty: MathChallenge.Difficulty,
    initialChallengeType: ChallengeType,
    initialWakeUpCheckMinutes: Int,
    initialMaxSnoozes: Int,
    initialMaxRingMinutes: Int,
    initialVibrateEnabled: Boolean,
    initialVolumeRampSeconds: Int,
    initialSnoozeEscalationMinutes: Int,
    initialGentleWakeMinutes: Int,
    initialBedtimeReminder: Boolean,
    alarmSoundUri: String?,
    historyEvents: List<HistoryEvent>,
    batteryExempt: Boolean,
    showBatteryCard: Boolean,
    onBatteryAllow: () -> Unit,
    onBatteryDismiss: () -> Unit,
    onSetAlarm: (Long) -> Unit,
    onSetAlarmAtTime: (Int, Int) -> Unit,
    onEditAlarmTime: (Alarm, Int, Int) -> Unit,
    onToggleAlarm: (Alarm, Boolean) -> Unit,
    onRepeatDaysChange: (Alarm, Set<Int>) -> Unit,
    onLabelChange: (Alarm, String) -> Unit,
    onSkipNextChange: (Alarm, Boolean) -> Unit,
    onPickAlarmSoundFor: (Alarm) -> Unit,
    onAlarmChallengeChange: (Alarm, ChallengeType?) -> Unit,
    onAlarmDifficultyChange: (Alarm, MathChallenge.Difficulty?) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit,
    onCancelAlarm: (Alarm) -> Unit,
    onOpenAlarm: () -> Unit,
    onSleepNowChange: (Boolean) -> Unit,
    onBedtimeChange: (LocalTime) -> Unit,
    onSleepHoursChange: (Float) -> Unit,
    onFallAsleepChange: (Int) -> Unit,
    onProfileChange: (SleepProfile) -> Pair<LocalTime, Float>,
    onSnoozeChange: (Int) -> Unit,
    onDifficultyChange: (MathChallenge.Difficulty) -> Unit,
    onChallengeTypeChange: (ChallengeType) -> Unit,
    onWakeUpCheckChange: (Int) -> Unit,
    onMaxSnoozesChange: (Int) -> Unit,
    onMaxRingChange: (Int) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onVolumeRampChange: (Int) -> Unit,
    onSnoozeEscalationChange: (Int) -> Unit,
    onGentleWakeChange: (Int) -> Unit,
    onPickAlarmSound: () -> Unit,
    onBedtimeReminderChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var sleepNow by rememberSaveable { mutableStateOf(initialSleepNow) }
    var bedtime by rememberSaveable(stateSaver = LocalTimeSaver) {
        mutableStateOf(initialBedtime)
    }
    var fallAsleepMinutes by rememberSaveable {
        mutableIntStateOf(initialFallAsleepMinutes)
    }
    var durationMinutes by rememberSaveable {
        mutableIntStateOf(sleepOnlyMinutes(initialSleepHours, initialFallAsleepMinutes))
    }
    var profile by rememberSaveable { mutableStateOf(initialProfile) }
    var showBedtimePicker by rememberSaveable { mutableStateOf(false) }
    var showDirectAlarmPicker by rememberSaveable { mutableStateOf(false) }
    var timeEditAlarmId by rememberSaveable { mutableIntStateOf(NO_ALARM_EDIT) }
    var labelEditAlarmId by rememberSaveable { mutableIntStateOf(NO_ALARM_EDIT) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var snoozeMinutes by rememberSaveable { mutableIntStateOf(initialSnoozeMinutes) }
    var difficulty by rememberSaveable { mutableStateOf(initialDifficulty) }
    var challengeType by rememberSaveable { mutableStateOf(initialChallengeType) }
    var wakeUpCheckMinutes by rememberSaveable { mutableIntStateOf(initialWakeUpCheckMinutes) }
    var maxSnoozes by rememberSaveable { mutableIntStateOf(initialMaxSnoozes) }
    var maxRingMinutes by rememberSaveable { mutableIntStateOf(initialMaxRingMinutes) }
    var vibrateEnabled by rememberSaveable { mutableStateOf(initialVibrateEnabled) }
    var volumeRampSeconds by rememberSaveable { mutableIntStateOf(initialVolumeRampSeconds) }
    var snoozeEscalationMinutes by rememberSaveable {
        mutableIntStateOf(initialSnoozeEscalationMinutes)
    }
    var gentleWakeMinutes by rememberSaveable { mutableIntStateOf(initialGentleWakeMinutes) }
    var bedtimeReminder by rememberSaveable { mutableStateOf(initialBedtimeReminder) }
    var alarmRinging by remember { mutableStateOf(AlarmService.isRinging) }
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            alarmRinging = AlarmService.isRinging
        }
    }

    val context = LocalContext.current
    val clockFormatter = remember { TimeFormats.clockFormatter(context) }
    val dayClockFormatter = remember { TimeFormats.dayClockFormatter(context) }

    fun wakeMillisAt(nowMillis: Long): Long = WakeTimeCalculator.bedtimeMillis(
        sleepNow, nowMillis, bedtime, ZoneId.systemDefault()
    ) + (durationMinutes + fallAsleepMinutes) * 60_000L

    fun commitDuration() {
        onSleepHoursChange((durationMinutes + fallAsleepMinutes) / 60f)
    }

    fun formatClock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clockFormatter)

    val conflictedAlarmIds = remember(alarms, now / 60_000) {
        conflictingAlarmIds(alarms, now, ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightColors.Background)
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 24 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "tabRise",
            modifier = Modifier.weight(1f)
        ) { currentTab ->
            when (currentTab) {
                MainTab.HOME -> HomeTab(
                    alarms = alarms,
                    now = now,
                    alarmRinging = alarmRinging,
                    showBatteryCard = showBatteryCard,
                    conflictedAlarmIds = conflictedAlarmIds,
                    sleepNow = sleepNow,
                    bedtime = bedtime,
                    profile = profile,
                    durationMinutes = durationMinutes,
                    fallAsleepMinutes = fallAsleepMinutes,
                    formatClock = ::formatClock,
                    clockFormatter = clockFormatter,
                    dayClockFormatter = dayClockFormatter,
                    onOpenAlarm = onOpenAlarm,
                    onBatteryAllow = onBatteryAllow,
                    onBatteryDismiss = onBatteryDismiss,
                    onProfileSelect = { candidate ->
                        if (profile != candidate) {
                            profile = candidate
                            val (newBedtime, newHours) = onProfileChange(candidate)
                            bedtime = newBedtime
                            durationMinutes =
                                sleepOnlyMinutes(newHours, fallAsleepMinutes)
                        }
                    },
                    onSleepNowSelected = {
                        sleepNow = true
                        onSleepNowChange(true)
                    },
                    onPickBedtime = {
                        sleepNow = false
                        onSleepNowChange(false)
                        showBedtimePicker = true
                    },
                    onDurationChange = { durationMinutes = it },
                    onDurationCommit = ::commitDuration,
                    onStartSleeping = {
                        commitDuration()
                        onSetAlarm(wakeMillisAt(System.currentTimeMillis()))
                    },
                    onPickDirectTime = { showDirectAlarmPicker = true },
                    onCancelAlarm = onCancelAlarm,
                    onToggleAlarm = onToggleAlarm,
                    onRepeatDaysChange = onRepeatDaysChange,
                    onDeleteAlarm = onDeleteAlarm,
                    onEditTime = { timeEditAlarmId = it.id },
                    onEditLabel = { labelEditAlarmId = it.id },
                    onSkipNextChange = onSkipNextChange,
                    onPickAlarmSoundFor = onPickAlarmSoundFor,
                    onAlarmChallengeChange = onAlarmChallengeChange,
                    onAlarmDifficultyChange = onAlarmDifficultyChange
                )
                MainTab.HISTORY -> HistoryTab(
                    events = historyEvents,
                    now = now,
                    formatClock = ::formatClock,
                    onClearHistory = onClearHistory
                )
                MainTab.SETTINGS -> SettingsTab(
                    snoozeMinutes = snoozeMinutes,
                    difficulty = difficulty,
                    challengeType = challengeType,
                    wakeUpCheckMinutes = wakeUpCheckMinutes,
                    maxSnoozes = maxSnoozes,
                    maxRingMinutes = maxRingMinutes,
                    vibrateEnabled = vibrateEnabled,
                    volumeRampSeconds = volumeRampSeconds,
                    snoozeEscalationMinutes = snoozeEscalationMinutes,
                    gentleWakeMinutes = gentleWakeMinutes,
                    fallAsleepMinutes = fallAsleepMinutes,
                    bedtimeReminder = bedtimeReminder,
                    bedtimeText = bedtime.format(clockFormatter),
                    alarmSoundUri = alarmSoundUri,
                    batteryExempt = batteryExempt,
                    onSnoozeChange = {
                        snoozeMinutes = it
                        onSnoozeChange(it)
                    },
                    onDifficultyChange = {
                        difficulty = it
                        onDifficultyChange(it)
                    },
                    onChallengeTypeChange = {
                        challengeType = it
                        onChallengeTypeChange(it)
                    },
                    onWakeUpCheckChange = {
                        wakeUpCheckMinutes = it
                        onWakeUpCheckChange(it)
                    },
                    onMaxSnoozesChange = {
                        maxSnoozes = it
                        onMaxSnoozesChange(it)
                    },
                    onMaxRingChange = {
                        maxRingMinutes = it
                        onMaxRingChange(it)
                    },
                    onVibrateChange = {
                        vibrateEnabled = it
                        onVibrateChange(it)
                    },
                    onVolumeRampChange = {
                        volumeRampSeconds = it
                        onVolumeRampChange(it)
                    },
                    onSnoozeEscalationChange = {
                        snoozeEscalationMinutes = it
                        onSnoozeEscalationChange(it)
                    },
                    onGentleWakeChange = {
                        gentleWakeMinutes = it
                        onGentleWakeChange(it)
                    },
                    onFallAsleepChange = {
                        fallAsleepMinutes = it
                        onFallAsleepChange(it)
                    },
                    onPickAlarmSound = onPickAlarmSound,
                    onBedtimeReminderChange = {
                        bedtimeReminder = it
                        onBedtimeReminderChange(it)
                    },
                    onBatteryAllow = onBatteryAllow,
                    onExport = onExport,
                    onImport = onImport
                )
            }
        }
        BottomNav(selected = tab, onSelect = { tab = it })
    }

    if (showBedtimePicker) {
        TimePickerDialog(
            title = stringResource(R.string.bedtime_dialog_title),
            initialHour = bedtime.hour,
            initialMinute = bedtime.minute,
            onConfirm = { hour, minute ->
                bedtime = LocalTime.of(hour, minute)
                onBedtimeChange(bedtime)
                showBedtimePicker = false
            },
            onDismiss = { showBedtimePicker = false }
        )
    }

    if (showDirectAlarmPicker) {
        TimePickerDialog(
            title = stringResource(R.string.alarm_time_dialog_title),
            initialHour = 7,
            initialMinute = 0,
            onConfirm = { hour, minute ->
                onSetAlarmAtTime(hour, minute)
                showDirectAlarmPicker = false
            },
            onDismiss = { showDirectAlarmPicker = false }
        )
    }

    val timeEditAlarm = alarms.firstOrNull { it.id == timeEditAlarmId }
    if (timeEditAlarm != null) {
        TimePickerDialog(
            title = stringResource(R.string.alarm_time_dialog_title),
            initialHour = timeEditAlarm.hour,
            initialMinute = timeEditAlarm.minute,
            onConfirm = { hour, minute ->
                onEditAlarmTime(timeEditAlarm, hour, minute)
                timeEditAlarmId = NO_ALARM_EDIT
            },
            onDismiss = { timeEditAlarmId = NO_ALARM_EDIT }
        )
    }

    val labelEditAlarm = alarms.firstOrNull { it.id == labelEditAlarmId }
    if (labelEditAlarm != null) {
        var labelText by rememberSaveable(labelEditAlarmId) {
            mutableStateOf(labelEditAlarm.label)
        }
        AlertDialog(
            onDismissRequest = { labelEditAlarmId = NO_ALARM_EDIT },
            title = { Text(stringResource(R.string.label_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    placeholder = { Text(stringResource(R.string.label_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLabelChange(labelEditAlarm, labelText.trim())
                    labelEditAlarmId = NO_ALARM_EDIT
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { labelEditAlarmId = NO_ALARM_EDIT }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}


@Composable
private fun NightChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accent: Color = NightColors.Amber
) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (selected) accent else NightColors.Body,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accent else NightColors.BorderStrong,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = NightColors.Amber,
            contentColor = NightColors.OnAmber,
            disabledContainerColor = NightColors.TrackOff,
            disabledContentColor = NightColors.Faint
        ),
        modifier = modifier.height(48.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NightColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NightColors.Text),
        modifier = modifier.height(48.dp)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun BottomNav(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Column {
        HorizontalDivider(color = NightColors.Border, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12121C))
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            listOf(
                MainTab.HOME to R.string.nav_sleep,
                MainTab.HISTORY to R.string.history_section,
                MainTab.SETTINGS to R.string.nav_settings
            ).forEach { (tab, labelRes) ->
                val active = selected == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(
                            if (active) NightColors.AmberFaint else Color.Transparent
                        )
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) NightColors.Amber else NightColors.Dim
                    )
                }
            }
        }
    }
}

@Composable
private fun TabHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            fontSize = 21.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            color = NightColors.Text,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, fontSize = 13.sp, color = NightColors.Dim)
        }
    }
}


@Composable
private fun HomeTab(
    alarms: List<Alarm>,
    now: Long,
    alarmRinging: Boolean,
    showBatteryCard: Boolean,
    conflictedAlarmIds: Set<Int>,
    sleepNow: Boolean,
    bedtime: LocalTime,
    profile: SleepProfile,
    durationMinutes: Int,
    fallAsleepMinutes: Int,
    formatClock: (Long) -> String,
    clockFormatter: DateTimeFormatter,
    dayClockFormatter: DateTimeFormatter,
    onOpenAlarm: () -> Unit,
    onBatteryAllow: () -> Unit,
    onBatteryDismiss: () -> Unit,
    onProfileSelect: (SleepProfile) -> Unit,
    onSleepNowSelected: () -> Unit,
    onPickBedtime: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onDurationCommit: () -> Unit,
    onStartSleeping: () -> Unit,
    onPickDirectTime: () -> Unit,
    onCancelAlarm: (Alarm) -> Unit,
    onToggleAlarm: (Alarm, Boolean) -> Unit,
    onRepeatDaysChange: (Alarm, Set<Int>) -> Unit,
    onDeleteAlarm: (Alarm) -> Unit,
    onEditTime: (Alarm) -> Unit,
    onEditLabel: (Alarm) -> Unit,
    onSkipNextChange: (Alarm, Boolean) -> Unit,
    onPickAlarmSoundFor: (Alarm) -> Unit,
    onAlarmChallengeChange: (Alarm, ChallengeType?) -> Unit,
    onAlarmDifficultyChange: (Alarm, MathChallenge.Difficulty?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TabHeader(
            title = stringResource(R.string.app_name),
            trailing = stringResource(R.string.now_time, formatClock(now))
        )

        if (alarmRinging) {
            NightCard(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.alarm_ringing),
                    style = MaterialTheme.typography.titleLarge,
                    color = NightColors.Amber
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = stringResource(R.string.open_alarm_screen),
                    onClick = onOpenAlarm,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showBatteryCard) {
            NightCard(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.battery_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NightColors.Text
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.battery_card_text),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = NightColors.Body
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        text = stringResource(R.string.battery_allow),
                        onClick = onBatteryAllow
                    )
                    OutlinePillButton(
                        text = stringResource(R.string.battery_dismiss),
                        onClick = onBatteryDismiss
                    )
                }
            }
        }

        val nextPair = alarms
            .mapNotNull { alarm -> nextFireMillis(alarm, now)?.let { alarm to it } }
            .minByOrNull { it.second }
        if (nextPair == null) {
            SleepSetupSection(
                sleepNow = sleepNow,
                bedtime = bedtime,
                profile = profile,
                durationMinutes = durationMinutes,
                fallAsleepMinutes = fallAsleepMinutes,
                now = now,
                formatClock = formatClock,
                clockFormatter = clockFormatter,
                onProfileSelect = onProfileSelect,
                onSleepNowSelected = onSleepNowSelected,
                onPickBedtime = onPickBedtime,
                onDurationChange = onDurationChange,
                onDurationCommit = onDurationCommit,
                onStartSleeping = onStartSleeping,
                onPickDirectTime = onPickDirectTime
            )
        } else {
            val (nextAlarm, nextFire) = nextPair
            ArmedSection(
                alarm = nextAlarm,
                fireMillis = nextFire,
                now = now,
                fallAsleepMinutes = fallAsleepMinutes,
                formatClock = formatClock,
                onEditTime = { onEditTime(nextAlarm) },
                onCancel = { onCancelAlarm(nextAlarm) }
            )
        }

        val otherAlarms = alarms.filterNot { it.id == nextPair?.first?.id }
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            otherAlarms.forEach { alarm ->
                key(alarm.id) {
                    AlarmCard(
                        alarm = alarm,
                        nowMillis = now,
                        conflicted = alarm.id in conflictedAlarmIds,
                        clockFormatter = clockFormatter,
                        dayClockFormatter = dayClockFormatter,
                        onToggle = { onToggleAlarm(alarm, it) },
                        onRepeatDaysChange = { onRepeatDaysChange(alarm, it) },
                        onDelete = { onDeleteAlarm(alarm) },
                        onEditTime = { onEditTime(alarm) },
                        onEditLabel = { onEditLabel(alarm) },
                        onSkipNextChange = { onSkipNextChange(alarm, it) },
                        onPickSound = { onPickAlarmSoundFor(alarm) },
                        onChallengeChange = { onAlarmChallengeChange(alarm, it) },
                        onDifficultyChange = { onAlarmDifficultyChange(alarm, it) }
                    )
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ArmedSection(
    alarm: Alarm,
    fireMillis: Long,
    now: Long,
    fallAsleepMinutes: Int,
    formatClock: (Long) -> String,
    onEditTime: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center
    ) {
        HomePulsingRing()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.alarm_set_for).uppercase(),
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = NightColors.Dim
            )
            Text(
                formatClock(fireMillis),
                fontSize = 60.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
                lineHeight = 64.sp,
                color = NightColors.Text,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onEditTime)
            )
            val sleepMinutes =
                ((fireMillis - now) / 60_000L).toInt() - fallAsleepMinutes
            if (sleepMinutes >= 90) {
                Text(
                    stringResource(
                        R.string.armed_detail,
                        formatDuration(sleepMinutes),
                        (sleepMinutes + 45) / 90,
                        fallAsleepMinutes
                    ),
                    fontSize = 13.5.sp,
                    color = NightColors.Body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, start = 30.dp, end = 30.dp)
                )
            }
            Text(
                buildString {
                    if (alarm.label.isNotEmpty()) {
                        append(alarm.label)
                        append(" · ")
                    }
                    append(
                        stringResource(
                            R.string.rings_in,
                            TimeFormats.formatCountdown(now, fireMillis)
                        )
                    )
                },
                fontSize = 13.sp,
                color = NightColors.Dim,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, NightColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NightColors.Body),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(46.dp)
    ) {
        Text(stringResource(R.string.cancel_alarm), fontSize = 14.sp)
    }
}

@Composable
private fun HomePulsingRing() {
    val transition = rememberInfiniteTransition(label = "homePulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homePulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homePulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .border(2.dp, NightColors.Amber, CircleShape)
    )
}

@Composable
private fun SleepSetupSection(
    sleepNow: Boolean,
    bedtime: LocalTime,
    profile: SleepProfile,
    durationMinutes: Int,
    fallAsleepMinutes: Int,
    now: Long,
    formatClock: (Long) -> String,
    clockFormatter: DateTimeFormatter,
    onProfileSelect: (SleepProfile) -> Unit,
    onSleepNowSelected: () -> Unit,
    onPickBedtime: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onDurationCommit: () -> Unit,
    onStartSleeping: () -> Unit,
    onPickDirectTime: () -> Unit
) {
    val fullCycles = durationMinutes / CYCLE_MINUTES
    val remainder = durationMinutes % CYCLE_MINUTES
    val aligned = remainder == 0
    val wakeMillis = WakeTimeCalculator.bedtimeMillis(
        sleepNow, now, bedtime, ZoneId.systemDefault()
    ) + (durationMinutes + fallAsleepMinutes) * 60_000L

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SleepProfile.entries.forEach { candidate ->
            NightChip(
                selected = profile == candidate,
                onClick = { onProfileSelect(candidate) },
                label = stringResource(
                    when (candidate) {
                        SleepProfile.WEEKDAY -> R.string.profile_weekday
                        SleepProfile.WEEKEND -> R.string.profile_weekend
                    }
                )
            )
        }
        NightChip(
            selected = sleepNow,
            onClick = onSleepNowSelected,
            label = stringResource(R.string.sleep_right_now)
        )
        NightChip(
            selected = !sleepNow,
            onClick = onPickBedtime,
            label = if (sleepNow) stringResource(R.string.pick_a_time)
            else stringResource(R.string.at_time, bedtime.format(clockFormatter))
        )
    }

    Text(
        stringResource(R.string.sleep_duration_question).uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        color = NightColors.Dim,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    )
    Text(
        formatDuration(durationMinutes),
        fontSize = 72.sp,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
        lineHeight = 76.sp,
        textAlign = TextAlign.Center,
        color = NightColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    )
    Text(
        text = if (aligned) {
            stringResource(R.string.cycle_hint_aligned, fullCycles)
        } else {
            val nearest = ((durationMinutes + CYCLE_MINUTES / 2) / CYCLE_MINUTES)
                .coerceAtLeast(MIN_SLEEP_MINUTES / CYCLE_MINUTES) * CYCLE_MINUTES
            stringResource(
                R.string.cycle_hint_misaligned,
                formatDuration(nearest.coerceAtMost(MAX_SLEEP_MINUTES))
            )
        },
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        color = if (aligned) NightColors.Amber else NightColors.Error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 26.dp, end = 26.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CycleBlock(weight = fallAsleepMinutes / 90f, color = Color(0x1FFFFFFF))
        repeat(fullCycles) {
            CycleBlock(weight = 1f, color = NightColors.Amber)
        }
        if (remainder > 0) {
            CycleBlock(
                weight = remainder / 90f,
                color = NightColors.Amber.copy(alpha = 0.3f)
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 4.dp)
    ) {
        Text(
            stringResource(R.string.cycle_axis_asleep),
            fontSize = 11.sp,
            color = NightColors.Faint,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.cycle_axis_cycles),
            fontSize = 11.sp,
            color = NightColors.Faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.cycle_axis_wake),
            fontSize = 11.sp,
            color = NightColors.Faint,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }

    Slider(
        value = durationMinutes.toFloat(),
        onValueChange = { onDurationChange(it.roundToInt()) },
        onValueChangeFinished = onDurationCommit,
        valueRange = MIN_SLEEP_MINUTES.toFloat()..MAX_SLEEP_MINUTES.toFloat(),
        steps = (MAX_SLEEP_MINUTES - MIN_SLEEP_MINUTES) / SLEEP_STEP_MINUTES - 1,
        modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
    ) {
        Text(
            stringResource(R.string.duration_h, MIN_SLEEP_MINUTES / 60),
            fontSize = 11.sp,
            color = NightColors.Faint,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.duration_h, MAX_SLEEP_MINUTES / 60),
            fontSize = 11.sp,
            color = NightColors.Faint
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        listOf(4, 5, 6).forEach { cycles ->
            NightChip(
                selected = durationMinutes == cycles * CYCLE_MINUTES,
                onClick = {
                    onDurationChange(cycles * CYCLE_MINUTES)
                    onDurationCommit()
                },
                label = stringResource(
                    R.string.cycle_chip, cycles, formatDuration(cycles * CYCLE_MINUTES)
                )
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 4.dp)
    ) {
        Text(
            stringResource(R.string.buffer_to_fall_asleep, fallAsleepMinutes),
            fontSize = 13.sp,
            color = NightColors.Dim,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.wake_at, formatClock(wakeMillis)),
            fontSize = 13.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            color = NightColors.Amber
        )
    }

    Button(
        onClick = onStartSleeping,
        shape = RoundedCornerShape(29.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NightColors.Amber,
            contentColor = NightColors.OnAmber
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp)
            .height(58.dp)
    ) {
        Text(
            stringResource(R.string.start_sleeping, formatClock(wakeMillis)),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onPickDirectTime) {
            Text(stringResource(R.string.alarm_at_time), color = NightColors.Dim)
        }
    }
}


private const val HISTORY_LIST_SIZE = 20

@Composable
private fun HistoryTab(
    events: List<HistoryEvent>,
    now: Long,
    formatClock: (Long) -> String,
    onClearHistory: () -> Unit
) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val summary = remember(events) {
        HistoryStats.summarize(events, System.currentTimeMillis())
    }
    val sessions = remember(events) { HistoryStats.sessions(events) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TabHeader(title = stringResource(R.string.history_title))

        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                value = if (sessions.isEmpty()) {
                    stringResource(R.string.history_no_value)
                } else {
                    String.format(
                        Locale.ROOT, "%.1f",
                        sessions.sumOf { it.snoozes }.toFloat() / sessions.size
                    )
                },
                caption = stringResource(R.string.stat_avg_snoozes),
                accent = NightColors.Amber,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = if (sessions.isEmpty()) {
                    stringResource(R.string.history_no_value)
                } else {
                    "${100 * sessions.count { it.snoozes == 0 } / sessions.size}%"
                },
                caption = stringResource(R.string.stat_first_try),
                accent = NightColors.Lavender,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = summary.noSnoozeStreak.toString(),
                caption = stringResource(R.string.stat_streak),
                accent = NightColors.Text,
                modifier = Modifier.weight(1f)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                stringResource(R.string.history_rings_week, summary.ringsLastWeek),
                fontSize = 12.5.sp,
                color = NightColors.Dim
            )
            Text(
                stringResource(
                    R.string.history_wake_consistency,
                    summary.wakeConsistencyMillis?.let(HistoryStats::formatMinutesSeconds)
                        ?: stringResource(R.string.history_no_value)
                ),
                fontSize = 12.5.sp,
                color = NightColors.Dim
            )
        }

        val visible = remember(events) {
            HistoryStats.sessions(events).takeLast(HISTORY_LIST_SIZE).asReversed()
        }
        val zone = ZoneId.systemDefault()
        val today = remember(now / 60_000) {
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        }
        val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    fontSize = 13.sp,
                    color = NightColors.Faint,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            visible.forEach { session ->
                val date = Instant.ofEpochMilli(session.wakeMillis)
                    .atZone(zone).toLocalDate()
                NightCard(cornerRadius = 14, contentPadding = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (date == today) {
                                stringResource(R.string.history_day_today)
                            } else {
                                date.format(dayFormatter)
                            },
                            fontSize = 12.sp,
                            color = NightColors.Dim,
                            modifier = Modifier.width(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                formatClock(session.wakeMillis),
                                fontSize = 17.sp,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Medium,
                                color = NightColors.Text
                            )
                            Text(
                                when {
                                    session.snoozes == 0 ->
                                        stringResource(R.string.history_detail_first_try)
                                    session.autoSnoozes > 0 ->
                                        stringResource(
                                            R.string.history_detail_snoozes_auto,
                                            session.snoozes, session.autoSnoozes
                                        )
                                    session.snoozes == 1 ->
                                        stringResource(R.string.history_detail_one_snooze)
                                    else ->
                                        stringResource(
                                            R.string.history_detail_snoozes,
                                            session.snoozes
                                        )
                                },
                                fontSize = 12.sp,
                                color = NightColors.Dim
                            )
                        }
                        SessionBadge(session)
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            TextButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Text(stringResource(R.string.history_clear), color = NightColors.Dim)
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_dialog_title)) },
            text = { Text(stringResource(R.string.history_clear_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    NightCard(modifier = modifier, cornerRadius = 14, contentPadding = 12) {
        Text(
            value,
            fontSize = 22.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(caption, fontSize = 11.5.sp, color = NightColors.Dim)
    }
}

@Composable
private fun SessionBadge(session: HistoryStats.WakeSession) {
    val (bg, fg) = when {
        session.autoSnoozes > 0 -> Color(0x26FF7878) to NightColors.Error
        session.snoozes == 0 -> NightColors.LavenderFaint to NightColors.Lavender
        else -> NightColors.AmberFaint to NightColors.Amber
    }
    Text(
        when {
            session.autoSnoozes > 0 -> stringResource(R.string.badge_auto)
            session.snoozes == 0 -> stringResource(R.string.badge_clean)
            else -> stringResource(R.string.badge_snoozes, session.snoozes)
        },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}


@Composable
private fun SettingsTab(
    snoozeMinutes: Int,
    difficulty: MathChallenge.Difficulty,
    challengeType: ChallengeType,
    wakeUpCheckMinutes: Int,
    maxSnoozes: Int,
    maxRingMinutes: Int,
    vibrateEnabled: Boolean,
    volumeRampSeconds: Int,
    snoozeEscalationMinutes: Int,
    gentleWakeMinutes: Int,
    fallAsleepMinutes: Int,
    bedtimeReminder: Boolean,
    bedtimeText: String,
    alarmSoundUri: String?,
    batteryExempt: Boolean,
    onSnoozeChange: (Int) -> Unit,
    onDifficultyChange: (MathChallenge.Difficulty) -> Unit,
    onChallengeTypeChange: (ChallengeType) -> Unit,
    onWakeUpCheckChange: (Int) -> Unit,
    onMaxSnoozesChange: (Int) -> Unit,
    onMaxRingChange: (Int) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onVolumeRampChange: (Int) -> Unit,
    onSnoozeEscalationChange: (Int) -> Unit,
    onGentleWakeChange: (Int) -> Unit,
    onFallAsleepChange: (Int) -> Unit,
    onPickAlarmSound: () -> Unit,
    onBedtimeReminderChange: (Boolean) -> Unit,
    onBatteryAllow: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TabHeader(title = stringResource(R.string.nav_settings))

        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NightCard {
                SettingChipRow(
                    title = stringResource(R.string.challenge_type),
                    options = ChallengeType.entries,
                    selected = challengeType,
                    onSelect = onChallengeTypeChange,
                    optionLabel = { type -> stringResource(challengeLabelRes(type)) }
                )
                Spacer(Modifier.height(14.dp))
                SettingChipRow(
                    title = stringResource(R.string.math_difficulty),
                    options = MathChallenge.Difficulty.entries,
                    selected = difficulty,
                    onSelect = onDifficultyChange,
                    optionLabel = { level -> stringResource(difficultyLabelRes(level)) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (difficulty) {
                            MathChallenge.Difficulty.EASY -> R.string.difficulty_sample_easy
                            MathChallenge.Difficulty.MEDIUM ->
                                R.string.difficulty_sample_medium
                            MathChallenge.Difficulty.HARD -> R.string.difficulty_sample_hard
                        }
                    ),
                    fontSize = 12.sp,
                    color = NightColors.Dim
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.snooze_asymmetry_note),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = NightColors.Faint
                )
            }

            NightCard {
                SettingChipRow(
                    title = stringResource(R.string.snooze_length),
                    options = AlarmSettings.SNOOZE_CHOICES,
                    selected = snoozeMinutes,
                    onSelect = onSnoozeChange,
                    optionLabel = { minutes ->
                        stringResource(R.string.minutes_choice, minutes)
                    }
                )
                Spacer(Modifier.height(14.dp))
                SettingChipRow(
                    title = stringResource(R.string.snooze_limit_section),
                    options = AlarmSettings.MAX_SNOOZE_CHOICES,
                    selected = maxSnoozes,
                    onSelect = onMaxSnoozesChange,
                    optionLabel = { limit ->
                        if (limit == 0) stringResource(R.string.snooze_limit_unlimited)
                        else stringResource(R.string.snooze_limit_choice, limit)
                    }
                )
                Spacer(Modifier.height(14.dp))
                SettingChipRow(
                    title = stringResource(R.string.snooze_escalation_section),
                    options = AlarmSettings.SNOOZE_ESCALATION_CHOICES,
                    selected = snoozeEscalationMinutes,
                    onSelect = onSnoozeEscalationChange,
                    optionLabel = { minutes ->
                        if (minutes == 0) stringResource(R.string.option_off)
                        else stringResource(R.string.snooze_escalation_choice, minutes)
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.snooze_escalation_description),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = NightColors.Faint
                )
            }

            NightCard {
                SettingChipRow(
                    title = stringResource(R.string.fall_asleep_buffer),
                    options = AlarmSettings.FALL_ASLEEP_CHOICES,
                    selected = fallAsleepMinutes,
                    onSelect = onFallAsleepChange,
                    optionLabel = { minutes ->
                        stringResource(R.string.buffer_plus_min, minutes)
                    }
                )
            }

            NightCard {
                SettingChipRow(
                    title = stringResource(R.string.wake_up_check_section),
                    options = AlarmSettings.WAKE_UP_CHECK_CHOICES,
                    selected = wakeUpCheckMinutes,
                    onSelect = onWakeUpCheckChange,
                    optionLabel = { minutes ->
                        if (minutes == 0) stringResource(R.string.wake_up_check_off)
                        else stringResource(R.string.minutes_choice, minutes)
                    }
                )
            }

            NightCard {
                Text(
                    stringResource(R.string.alarm_behavior_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = NightColors.Text
                )
                Spacer(Modifier.height(12.dp))
                SettingChipRow(
                    title = stringResource(R.string.ring_duration_label),
                    small = true,
                    options = AlarmSettings.MAX_RING_CHOICES,
                    selected = maxRingMinutes,
                    onSelect = onMaxRingChange,
                    optionLabel = { minutes ->
                        stringResource(R.string.minutes_choice, minutes)
                    }
                )
                Spacer(Modifier.height(12.dp))
                SettingChipRow(
                    title = stringResource(R.string.vibration_label),
                    small = true,
                    options = listOf(true, false),
                    selected = vibrateEnabled,
                    onSelect = onVibrateChange,
                    optionLabel = { on ->
                        stringResource(if (on) R.string.vibration_on else R.string.vibration_off)
                    }
                )
                Spacer(Modifier.height(12.dp))
                SettingChipRow(
                    title = stringResource(R.string.volume_ramp_label),
                    small = true,
                    options = AlarmSettings.VOLUME_RAMP_CHOICES,
                    selected = volumeRampSeconds,
                    onSelect = onVolumeRampChange,
                    optionLabel = { seconds ->
                        if (seconds == 0) stringResource(R.string.volume_ramp_instant)
                        else stringResource(R.string.seconds_choice, seconds)
                    }
                )
                Spacer(Modifier.height(12.dp))
                SettingChipRow(
                    title = stringResource(R.string.gentle_wake_label),
                    small = true,
                    options = AlarmSettings.GENTLE_WAKE_CHOICES,
                    selected = gentleWakeMinutes,
                    onSelect = onGentleWakeChange,
                    optionLabel = { minutes ->
                        if (minutes == 0) stringResource(R.string.option_off)
                        else stringResource(R.string.gentle_wake_choice, minutes)
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.gentle_wake_description),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = NightColors.Faint
                )
                Spacer(Modifier.height(12.dp))
                val defaultSoundTitle = stringResource(R.string.alarm_sound_default)
                val alarmSoundTitle = remember(alarmSoundUri, defaultSoundTitle) {
                    alarmSoundUri?.let { stored ->
                        runCatching {
                            RingtoneManager.getRingtone(context, Uri.parse(stored))
                                ?.getTitle(context)
                        }.getOrNull()
                    } ?: defaultSoundTitle
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.alarm_sound_label),
                            fontSize = 14.sp,
                            color = NightColors.Text
                        )
                        Text(alarmSoundTitle, fontSize = 12.sp, color = NightColors.Dim)
                    }
                    TextButton(onClick = onPickAlarmSound) {
                        Text(
                            stringResource(R.string.alarm_sound_change),
                            color = NightColors.Amber
                        )
                    }
                }
            }

            NightCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.bedtime_reminder_section),
                            fontSize = 14.sp,
                            color = NightColors.Text
                        )
                        Text(
                            stringResource(R.string.bedtime_reminder_daily_at, bedtimeText),
                            fontSize = 12.sp,
                            color = NightColors.Dim
                        )
                    }
                    Switch(checked = bedtimeReminder, onCheckedChange = onBedtimeReminderChange)
                }
            }

            NightCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.battery_status_label),
                            fontSize = 14.sp,
                            color = NightColors.Text
                        )
                        Text(
                            stringResource(
                                if (batteryExempt) R.string.battery_status_exempt
                                else R.string.battery_status_restricted
                            ),
                            fontSize = 12.sp,
                            color = if (batteryExempt) NightColors.Dim else NightColors.Error
                        )
                    }
                    if (!batteryExempt) {
                        TextButton(onClick = onBatteryAllow) {
                            Text(
                                stringResource(R.string.battery_allow),
                                color = NightColors.Amber
                            )
                        }
                    }
                }
            }

            NightCard(background = Color(0x14A99BDD)) {
                Text(
                    stringResource(R.string.built_to_fire_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NightColors.Lavender
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.built_to_fire_text),
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = NightColors.Body
                )
            }

            NightCard {
                Text(
                    stringResource(R.string.backup_section),
                    fontSize = 14.sp,
                    color = NightColors.Text
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onExport) {
                        Text(stringResource(R.string.export_backup), color = NightColors.Amber)
                    }
                    TextButton(onClick = onImport) {
                        Text(stringResource(R.string.import_backup), color = NightColors.Amber)
                    }
                }
            }

            Text(
                stringResource(R.string.privacy_footer),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = NightColors.Faint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.CycleBlock(weight: Float, color: Color) {
    Box(
        modifier = Modifier
            .weight(weight.coerceAtLeast(0.01f))
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
    )
}

@Composable
private fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) stringResource(R.string.duration_h, hours)
    else stringResource(R.string.duration_hm, hours, minutes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun <T> SettingChipRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    small: Boolean = false
) {
    Text(
        title,
        fontSize = if (small) 13.sp else 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = NightColors.Text
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            NightChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = optionLabel(option)
            )
        }
    }
}

private fun challengeLabelRes(type: ChallengeType): Int = when (type) {
    ChallengeType.MATH -> R.string.challenge_math
    ChallengeType.TYPING -> R.string.challenge_typing
    ChallengeType.MEMORY -> R.string.challenge_memory
    ChallengeType.SHAKE -> R.string.challenge_shake
    ChallengeType.STEPS -> R.string.challenge_steps
}

private fun difficultyLabelRes(level: MathChallenge.Difficulty): Int = when (level) {
    MathChallenge.Difficulty.EASY -> R.string.difficulty_easy
    MathChallenge.Difficulty.MEDIUM -> R.string.difficulty_medium
    MathChallenge.Difficulty.HARD -> R.string.difficulty_hard
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    nowMillis: Long,
    conflicted: Boolean,
    clockFormatter: DateTimeFormatter,
    dayClockFormatter: DateTimeFormatter,
    onToggle: (Boolean) -> Unit,
    onRepeatDaysChange: (Set<Int>) -> Unit,
    onDelete: () -> Unit,
    onEditTime: () -> Unit,
    onEditLabel: () -> Unit,
    onSkipNextChange: (Boolean) -> Unit,
    onPickSound: () -> Unit,
    onChallengeChange: (ChallengeType?) -> Unit,
    onDifficultyChange: (MathChallenge.Difficulty?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    NightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEditTime)
            ) {
                Text(
                    LocalTime.of(alarm.hour, alarm.minute).format(clockFormatter),
                    fontSize = 28.sp,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.enabled) NightColors.Text else NightColors.Disabled
                )
                val nextFire = nextFireMillis(alarm, nowMillis)
                val subline = buildString {
                    if (alarm.label.isNotEmpty()) {
                        append(alarm.label)
                        append(" · ")
                    }
                    if (nextFire != null) {
                        append(
                            stringResource(
                                R.string.rings_in,
                                TimeFormats.formatCountdown(nowMillis, nextFire)
                            )
                        )
                    } else {
                        append(stringResource(R.string.option_off))
                    }
                }
                Text(subline, fontSize = 12.5.sp, color = NightColors.Dim)
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.hide_alarm_options
                        else R.string.show_alarm_options
                    ),
                    tint = NightColors.Dim
                )
            }
        }
        if (conflicted) {
            Text(
                stringResource(R.string.alarm_conflict_warning),
                fontSize = 12.sp,
                color = NightColors.Error
            )
        }
        if (alarm.enabled && alarm.repeatDays.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.next_occurrence,
                        Instant.ofEpochMilli(alarm.triggerAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(dayClockFormatter)
                    ),
                    fontSize = 12.sp,
                    color = NightColors.Dim,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSkipNextChange(!alarm.skipNext) }) {
                    Text(
                        stringResource(
                            if (alarm.skipNext) R.string.undo_skip
                            else R.string.skip_next
                        ),
                        color = NightColors.Amber,
                        fontSize = 12.sp
                    )
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day.value in alarm.repeatDays
                    NightChip(
                        selected = selected,
                        onClick = {
                            onRepeatDaysChange(
                                if (selected) alarm.repeatDays - day.value
                                else alarm.repeatDays + day.value
                            )
                        },
                        label = stringResource(dayLetterRes(day))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    alarm.label.ifEmpty { stringResource(R.string.no_label) },
                    fontSize = 13.sp,
                    color = NightColors.Body,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditLabel) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_label),
                        tint = NightColors.Dim
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_alarm),
                        tint = NightColors.Dim
                    )
                }
            }
            val context = LocalContext.current
            val followGlobalTitle = stringResource(R.string.option_default)
            val soundTitle = remember(alarm.soundUri, followGlobalTitle) {
                alarm.soundUri?.let { stored ->
                    runCatching {
                        RingtoneManager.getRingtone(context, Uri.parse(stored))
                            ?.getTitle(context)
                    }.getOrNull()
                } ?: followGlobalTitle
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.alarm_sound_label),
                        fontSize = 13.sp,
                        color = NightColors.Body
                    )
                    Text(soundTitle, fontSize = 12.sp, color = NightColors.Dim)
                }
                TextButton(onClick = onPickSound) {
                    Text(
                        stringResource(R.string.alarm_sound_change),
                        color = NightColors.Amber
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingChipRow(
                title = stringResource(R.string.challenge_type),
                small = true,
                options = listOf<ChallengeType?>(null) + ChallengeType.entries,
                selected = alarm.challengeType,
                onSelect = onChallengeChange,
                optionLabel = { type ->
                    if (type == null) stringResource(R.string.option_default)
                    else stringResource(challengeLabelRes(type))
                }
            )
            Spacer(Modifier.height(8.dp))
            SettingChipRow(
                title = stringResource(R.string.math_difficulty),
                small = true,
                options = listOf<MathChallenge.Difficulty?>(null) +
                    MathChallenge.Difficulty.entries,
                selected = alarm.difficulty,
                onSelect = onDifficultyChange,
                optionLabel = { level ->
                    if (level == null) stringResource(R.string.option_default)
                    else stringResource(difficultyLabelRes(level))
                }
            )
        }
    }
}

private fun nextFireMillis(
    alarm: Alarm,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long? = when {
    !alarm.enabled -> null
    alarm.triggerAtMillis > nowMillis -> alarm.triggerAtMillis
    alarm.repeatDays.isEmpty() -> null
    else -> AlarmTimeCalculator.nextTrigger(
        alarm.hour, alarm.minute, alarm.repeatDays, nowMillis, zone
    )
}

private fun conflictingAlarmIds(
    alarms: List<Alarm>,
    nowMillis: Long,
    zone: ZoneId
): Set<Int> = alarms
    .mapNotNull { alarm ->
        nextFireMillis(alarm, nowMillis, zone)?.let { fire -> alarm.id to fire / 60_000 }
    }
    .groupBy({ it.second }, { it.first })
    .values
    .filter { it.size > 1 }
    .flatten()
    .toSet()

private fun dayLetterRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.day_letter_monday
    DayOfWeek.TUESDAY -> R.string.day_letter_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_letter_wednesday
    DayOfWeek.THURSDAY -> R.string.day_letter_thursday
    DayOfWeek.FRIDAY -> R.string.day_letter_friday
    DayOfWeek.SATURDAY -> R.string.day_letter_saturday
    DayOfWeek.SUNDAY -> R.string.day_letter_sunday
}
