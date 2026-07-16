package com.sleepalarm

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private enum class PendingAction { SNOOZE, DISMISS }

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FALLBACK_ALERT = "fallback_alert"
    }

    private val autoSnoozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    private var autoSnoozeReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AlarmService.isRinging &&
            !intent.getBooleanExtra(EXTRA_FALLBACK_ALERT, false)
        ) {
            finish()
            return
        }

        showOverLockscreen()

        ContextCompat.registerReceiver(
            this,
            autoSnoozeReceiver,
            IntentFilter(AlarmService.ACTION_AUTO_SNOOZED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        autoSnoozeReceiverRegistered = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {  }
        })

        val settings = AlarmSettings(this)
        val ringingAlarm = AlarmStore(this).get(settings.ringingAlarmId)

        setContent {
            SleepAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NightColors.RingBackground
                ) {
                    AlarmScreen(
                        snoozeMinutes = settings.snoozeMinutes,
                        difficulty = ringingAlarm?.difficulty ?: settings.difficulty,
                        challengeType = ringingAlarm?.challengeType ?: settings.challengeType,
                        label = ringingAlarm?.label.orEmpty(),
                        canSnooze = Challenges.canSnooze(
                            settings.maxSnoozes, settings.snoozeCount
                        ),
                        snoozeCount = settings.snoozeCount,
                        maxSnoozes = settings.maxSnoozes,
                        nextSnoozeMinutes = AlarmScheduler.escalatedSnoozeMinutes(
                            settings.snoozeMinutes,
                            settings.snoozeEscalationMinutes,
                            settings.snoozeCount
                        ),
                        snoozeEscalationOn = settings.snoozeEscalationMinutes > 0,
                        onSnooze = {
                            if (AlarmService.isRinging) {
                                settings.snoozeCount++
                                HistoryStore(this).append(HistoryEvent.Type.SNOOZE)
                                AlarmScheduler(this).snooze(settings.ringingAlarmId)
                            }
                            stopAlarmAndFinish()
                        },
                        onSnoozeUntil = { triggerAtMillis ->
                            if (AlarmService.isRinging) {
                                settings.snoozeCount++
                                HistoryStore(this).append(HistoryEvent.Type.SNOOZE)
                                AlarmScheduler(this).scheduleSnoozeAt(
                                    settings.ringingAlarmId, triggerAtMillis
                                )
                            }
                            stopAlarmAndFinish()
                        },
                        onDismiss = {
                            AlarmScheduler(this).cancelSnooze(settings.ringingAlarmId)
                            settings.snoozeCount = 0
                            val wakeCheckRing = settings.lastRingFromWakeCheck
                            HistoryStore(this).append(HistoryEvent.Type.DISMISS)
                            maybeScheduleWakeCheck(settings)
                            if (!wakeCheckRing) {
                                AlarmService.showDismissedNotification(
                                    this,
                                    settings.ringingAlarmId,
                                    ringingAlarm?.label.orEmpty()
                                )
                            }
                            stopAlarmAndFinish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (autoSnoozeReceiverRegistered) {
            unregisterReceiver(autoSnoozeReceiver)
        }
        super.onDestroy()
    }

    private fun maybeScheduleWakeCheck(settings: AlarmSettings) {
        val minutes = settings.wakeUpCheckMinutes
        if (minutes <= 0 || settings.wakeCheckPending || settings.lastRingFromWakeCheck) return

        settings.wakeCheckPending = true
        try {
            AlarmScheduler(this).scheduleWakeCheck(
                System.currentTimeMillis() + minutes * 60_000L
            )
        } catch (e: Exception) {
            settings.wakeCheckPending = false
            throw e
        }
        try {
            postWakeCheckNotification(minutes)
        } catch (e: Exception) {
            Log.w("AlarmActivity", "Failed to post wake-check notification", e)
        }
    }

    private fun postWakeCheckNotification(minutes: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AlarmReceiver.WAKE_CHECK_CHANNEL_ID,
                getString(R.string.wake_check_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val confirmIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_CONFIRM_AWAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            AlarmReceiver.WAKE_CHECK_NOTIFICATION_ID,
            Notification.Builder(this, AlarmReceiver.WAKE_CHECK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.wake_check_title))
                .setContentText(getString(R.string.wake_check_text, minutes))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_notification),
                        getString(R.string.im_awake),
                        confirmIntent
                    ).build()
                )
                .build()
        )
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguard.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopAlarmAndFinish() {
        startService(
            Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP)
        )
        finish()
    }
}

@Composable
private fun AlarmScreen(
    snoozeMinutes: Int,
    difficulty: MathChallenge.Difficulty,
    challengeType: ChallengeType,
    label: String,
    canSnooze: Boolean,
    snoozeCount: Int,
    maxSnoozes: Int,
    nextSnoozeMinutes: Int,
    snoozeEscalationOn: Boolean,
    onSnooze: () -> Unit,
    onSnoozeUntil: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingAction by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var snoozeUntilMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var autoSnoozeSeconds by remember { mutableLongStateOf(autoSnoozeSecondsLeft()) }
    val context = LocalContext.current
    val clockFormatter = remember { TimeFormats.clockFormatter(context) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            autoSnoozeSeconds = autoSnoozeSecondsLeft()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightColors.RingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Text(
            text = label.ifEmpty { stringResource(R.string.time_to_wake_up) }.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            color = NightColors.Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .semantics { heading() }
        )

        val action = pendingAction
        if (action == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PulsingRing()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = now.format(clockFormatter),
                        fontSize = 72.sp,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-3).sp,
                        lineHeight = 76.sp,
                        color = NightColors.Text
                    )
                    if (maxSnoozes > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.snoozes_used, snoozeCount, maxSnoozes
                            ),
                            fontSize = 14.sp,
                            color = NightColors.Dim
                        )
                    }
                    if (snoozeEscalationOn && canSnooze) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.next_snooze_length, nextSnoozeMinutes
                            ),
                            fontSize = 14.sp,
                            color = NightColors.Dim
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { pendingAction = PendingAction.SNOOZE },
                    enabled = canSnooze,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NightColors.Amber,
                        contentColor = NightColors.OnAmber,
                        disabledContainerColor = NightColors.Key,
                        disabledContentColor = NightColors.Faint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text(
                        text = if (canSnooze) {
                            stringResource(R.string.ring_snooze_button, snoozeMinutes)
                        } else {
                            stringResource(R.string.no_snoozes_left)
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = { pendingAction = PendingAction.DISMISS },
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, NightColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NightColors.Body
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(stringResource(R.string.ring_dismiss_button), fontSize = 14.5.sp)
                }
                if (canSnooze) {
                    TextButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            stringResource(R.string.snooze_until_button),
                            color = NightColors.Dim,
                            fontSize = 13.sp
                        )
                    }
                }
                AutoSnoozeCountdown(autoSnoozeSeconds)
            }

            if (showTimePicker) {
                SnoozeUntilDialog(
                    onConfirm = { hour, minute ->
                        showTimePicker = false
                        snoozeUntilMillis = AlarmTimeCalculator.nextTrigger(
                            hour,
                            minute,
                            emptySet(),
                            System.currentTimeMillis(),
                            ZoneId.systemDefault()
                        )
                        pendingAction = PendingAction.SNOOZE
                    },
                    onDismissRequest = { showTimePicker = false }
                )
            }
        } else {
            val onPass: () -> Unit = when {
                action != PendingAction.SNOOZE -> onDismiss
                else -> {
                    {
                        val until = snoozeUntilMillis
                        if (until != null) onSnoozeUntil(until) else onSnooze()
                    }
                }
            }
            val onBack: () -> Unit = {
                pendingAction = null
                snoozeUntilMillis = null
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        stringResource(R.string.back),
                        color = NightColors.Dim,
                        fontSize = 14.sp
                    )
                }
                when (challengeType) {
                    ChallengeType.MATH ->
                        MathChallengeContent(action, difficulty, onPass, onBack)
                    ChallengeType.TYPING ->
                        TypingChallengeContent(action, difficulty, onPass, onBack)
                    ChallengeType.MEMORY ->
                        MemoryChallengeContent(action, difficulty, onPass, onBack)
                    ChallengeType.SHAKE ->
                        ShakeChallengeContent(action, difficulty, onPass, onBack)
                    ChallengeType.STEPS ->
                        StepsChallengeContent(action, difficulty, onPass, onBack)
                }
            }
            AutoSnoozeCountdown(
                autoSnoozeSeconds,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

private fun autoSnoozeSecondsLeft(): Long {
    val deadline = AlarmService.autoSnoozeAtElapsed
    if (deadline == 0L) return 0L
    return ((deadline - SystemClock.elapsedRealtime()) / 1_000L).coerceAtLeast(0L)
}

@Composable
private fun AutoSnoozeCountdown(seconds: Long, modifier: Modifier = Modifier) {
    if (seconds <= 0L) return
    Text(
        text = stringResource(
            R.string.auto_snooze_in,
            "%d:%02d".format(seconds / 60, seconds % 60)
        ),
        fontSize = 12.sp,
        color = NightColors.Faint,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PulsingRing() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(260.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .border(2.dp, NightColors.Amber, CircleShape)
    )
}

private fun accentFor(action: PendingAction): Color =
    if (action == PendingAction.DISMISS) NightColors.Lavender else NightColors.Amber

@Composable
private fun ChallengeTitle(action: PendingAction, text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        color = accentFor(action),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
    )
}

@Composable
private fun AnswerSlot(input: String, accent: Color, shakeOffset: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .graphicsLayer { translationX = shakeOffset },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = input.ifEmpty { " " },
                fontSize = 34.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                color = NightColors.Text
            )
            HorizontalDivider(
                thickness = 2.dp,
                color = accent,
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Composable
private fun rememberShakeOffset(trigger: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            listOf(-10f, 10f, -7f, 7f, 0f).forEach { x ->
                offset.animateTo(x, animationSpec = tween(70))
            }
        }
    }
    return offset.value
}

@Composable
private fun Keypad(
    accent: Color,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789").forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowKeys.forEach { key ->
                    KeypadKey(
                        label = key.toString(),
                        background = NightColors.Key,
                        contentColor = NightColors.Text,
                        onClick = { onDigit(key) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeypadKey(
                label = "⌫",
                background = NightColors.Key,
                contentColor = NightColors.Text,
                contentDescription = stringResource(R.string.keypad_delete),
                onClick = onDelete
            )
            KeypadKey(
                label = "0",
                background = NightColors.Key,
                contentColor = NightColors.Text,
                onClick = { onDigit('0') }
            )
            KeypadKey(
                label = stringResource(R.string.ok),
                background = accent,
                contentColor = NightColors.OnAmber,
                contentDescription = stringResource(R.string.keypad_submit),
                onClick = onSubmit
            )
        }
    }
}

@Composable
private fun RowScope.KeypadKey(
    label: String,
    background: Color,
    contentColor: Color,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 24.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun BackSubmitRow(onBack: () -> Unit, onSubmit: () -> Unit) {
    Row {
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, NightColors.BorderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NightColors.Body)
        ) {
            Text(stringResource(R.string.back))
        }
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = onSubmit,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = NightColors.Amber,
                contentColor = NightColors.OnAmber
            )
        ) {
            Text(stringResource(R.string.submit))
        }
    }
}

@Composable
private fun MathChallengeContent(
    action: PendingAction,
    difficulty: MathChallenge.Difficulty,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    fun newProblem(): MathProblem = if (action == PendingAction.SNOOZE) {
        MathChallenge.forSnooze(difficulty)
    } else {
        MathChallenge.forDismiss(difficulty)
    }

    var problem by rememberSaveable { mutableStateOf(newProblem()) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrongAttempts by rememberSaveable { mutableIntStateOf(0) }
    val accent = accentFor(action)
    val shakeOffset = rememberShakeOffset(wrongAttempts)

    fun submit() {
        if (input.trim().toIntOrNull() == problem.answer) {
            onPass()
        } else {
            wrongAttempts++
            problem = newProblem()
            input = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChallengeTitle(
            action = action,
            text = if (action == PendingAction.SNOOZE) {
                stringResource(R.string.solve_to_snooze)
            } else {
                stringResource(R.string.solve_to_dismiss)
            }
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = problem.question,
            fontSize = 44.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = NightColors.Text,
            modifier = Modifier.graphicsLayer { translationX = shakeOffset }
        )
        Spacer(Modifier.height(14.dp))
        AnswerSlot(input = input, accent = accent, shakeOffset = shakeOffset)
        if (wrongAttempts > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.wrong_answer, wrongAttempts),
                fontSize = 13.sp,
                color = NightColors.Error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.height(24.dp))
        Keypad(
            accent = accent,
            onDigit = { digit -> if (input.length < 5) input += digit },
            onDelete = { input = input.dropLast(1) },
            onSubmit = ::submit
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TypingChallengeContent(
    action: PendingAction,
    difficulty: MathChallenge.Difficulty,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    val tierRes = if (action == PendingAction.SNOOZE) {
        R.array.typing_phrases_easy
    } else {
        when (difficulty) {
            MathChallenge.Difficulty.EASY -> R.array.typing_phrases_easy
            MathChallenge.Difficulty.MEDIUM -> R.array.typing_phrases_medium
            MathChallenge.Difficulty.HARD -> R.array.typing_phrases_hard
        }
    }
    val phrases = stringArrayResource(tierRes)
    val phrase = rememberSaveable { Challenges.pickPhrase(phrases.toList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrongAttempts by rememberSaveable { mutableIntStateOf(0) }

    fun submit() {
        if (Challenges.typingMatches(phrase, input)) {
            onPass()
        } else {
            wrongAttempts++
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChallengeTitle(
            action = action,
            text = if (action == PendingAction.SNOOZE) {
                stringResource(R.string.type_to_snooze)
            } else {
                stringResource(R.string.type_to_dismiss)
            }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = phrase,
            fontSize = 24.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = NightColors.Text
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.phrase_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (wrongAttempts > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.typing_mismatch, wrongAttempts),
                fontSize = 13.sp,
                color = NightColors.Error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.height(16.dp))
        BackSubmitRow(onBack = onBack, onSubmit = ::submit)
    }
}

@Composable
private fun MemoryChallengeContent(
    action: PendingAction,
    difficulty: MathChallenge.Difficulty,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    var sequence by rememberSaveable { mutableStateOf(Challenges.memorySequence(difficulty)) }
    var showing by rememberSaveable { mutableStateOf(true) }
    var revealDeadline by rememberSaveable {
        mutableLongStateOf(
            SystemClock.elapsedRealtime() + Challenges.memoryShowMillis(difficulty)
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    var wrongAttempts by rememberSaveable { mutableIntStateOf(0) }
    val accent = accentFor(action)
    val shakeOffset = rememberShakeOffset(wrongAttempts)

    LaunchedEffect(sequence) {
        if (showing) {
            delay((revealDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            showing = false
        }
    }

    fun submit() {
        if (input == sequence) {
            onPass()
        } else {
            wrongAttempts++
            sequence = Challenges.memorySequence(difficulty)
            revealDeadline =
                SystemClock.elapsedRealtime() + Challenges.memoryShowMillis(difficulty)
            showing = true
            input = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChallengeTitle(
            action = action,
            text = if (showing) {
                stringResource(R.string.memorize_prompt)
            } else if (action == PendingAction.SNOOZE) {
                stringResource(R.string.enter_sequence_to_snooze)
            } else {
                stringResource(R.string.enter_sequence_to_dismiss)
            }
        )
        Spacer(Modifier.height(16.dp))
        if (showing) {
            Text(
                text = sequence,
                fontSize = 44.sp,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                color = NightColors.Text,
                modifier = Modifier.semantics {
                    contentDescription = sequence.toCharArray().joinToString(" ")
                }
            )
        } else {
            AnswerSlot(input = input, accent = accent, shakeOffset = shakeOffset)
            if (wrongAttempts > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.wrong_sequence, wrongAttempts),
                    fontSize = 13.sp,
                    color = NightColors.Error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Spacer(Modifier.height(24.dp))
            Keypad(
                accent = accent,
                onDigit = { digit ->
                    if (input.length < sequence.length) input += digit
                },
                onDelete = { input = input.dropLast(1) },
                onSubmit = ::submit
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShakeChallengeContent(
    action: PendingAction,
    difficulty: MathChallenge.Difficulty,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val accelerometer = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    if (accelerometer == null) {
        MathChallengeContent(action, difficulty, onPass, onBack)
        return
    }

    val required = if (action == PendingAction.SNOOZE) {
        Challenges.shakesForSnooze(difficulty)
    } else {
        Challenges.shakesForDismiss(difficulty)
    }
    var shakes by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(shakes) {
        if (shakes >= required) onPass()
    }

    DisposableEffect(Unit) {
        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val magnitude = sqrt(x * x + y * y + z * z)
                val elapsed = SystemClock.elapsedRealtime()
                if (Challenges.isShake(magnitude) &&
                    elapsed - lastShakeAt >= Challenges.SHAKE_DEBOUNCE_MILLIS
                ) {
                    lastShakeAt = elapsed
                    shakes++
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(
            listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
        )
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChallengeTitle(
            action = action,
            text = if (action == PendingAction.SNOOZE) {
                stringResource(R.string.shake_to_snooze)
            } else {
                stringResource(R.string.shake_to_dismiss)
            }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                R.string.shake_progress, shakes.coerceAtMost(required), required
            ),
            fontSize = 44.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = accentFor(action),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun StepsChallengeContent(
    action: PendingAction,
    difficulty: MathChallenge.Difficulty,
    onPass: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val stepDetector = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }
    val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
        PackageManager.PERMISSION_GRANTED

    if (stepDetector == null || permissionMissing) {
        MathChallengeContent(action, difficulty, onPass, onBack)
        return
    }

    val required = if (action == PendingAction.SNOOZE) {
        Challenges.stepsForSnooze(difficulty)
    } else {
        Challenges.stepsForDismiss(difficulty)
    }
    var steps by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(steps) {
        if (steps >= required) onPass()
    }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                steps++
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(
            listener, stepDetector, SensorManager.SENSOR_DELAY_UI
        )
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChallengeTitle(
            action = action,
            text = if (action == PendingAction.SNOOZE) {
                stringResource(R.string.walk_to_snooze)
            } else {
                stringResource(R.string.walk_to_dismiss)
            }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                R.string.steps_progress, steps.coerceAtMost(required), required
            ),
            fontSize = 44.sp,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = accentFor(action),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeUntilDialog(
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { LocalTime.now() }
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.snooze_until_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
