package com.sleepalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.sleepalarm.action.START_ALARM"
        const val ACTION_STOP = "com.sleepalarm.action.STOP_ALARM"
        const val ACTION_SNOOZE = "com.sleepalarm.action.SNOOZE_ALARM"
        const val ACTION_AUTO_SNOOZED = "com.sleepalarm.action.ALARM_AUTO_SNOOZED"
        const val ACTION_GENTLE = "com.sleepalarm.action.GENTLE_WAKE"
        const val ACTION_STOP_GENTLE = "com.sleepalarm.action.STOP_GENTLE"
        const val EXTRA_GENTLE_TRIGGER_AT = "gentle_trigger_at"
        const val CHANNEL_ID = "alarm_channel"
        const val GENTLE_CHANNEL_ID = "gentle_wake_channel"
        const val DISMISSED_CHANNEL_ID = "dismissed_channel"
        private const val NOTIFICATION_ID = 42
        private const val GENTLE_NOTIFICATION_ID = 46
        const val DISMISSED_NOTIFICATION_ID = 47
        private const val GENTLE_VOLUME = 0.15f
        private const val GENTLE_MAX_MILLIS = 2 * 60_000L
        private const val DISMISSED_TIMEOUT_MILLIS = 30_000L
        private const val VOLUME_RAMP_STEPS = 20
        private const val MIN_RAMP_VOLUME = 0.1f
        private val VIBRATION_RAMP_AMPLITUDES = intArrayOf(60, 110, 160, 210, 255)

        @Volatile
        var isRinging = false
            private set

        @Volatile
        var autoSnoozeAtElapsed = 0L
            private set

        fun createAlarmChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        fun showDismissedNotification(context: Context, alarmId: Int, label: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    DISMISSED_CHANNEL_ID,
                    context.getString(R.string.dismissed_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
            val undoIntent = PendingIntent.getBroadcast(
                context,
                DISMISSED_NOTIFICATION_ID,
                Intent(context, AlarmReceiver::class.java)
                    .setAction(AlarmReceiver.ACTION_UNDO_DISMISS)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = Notification.Builder(context, DISMISSED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.alarm_dismissed_title))
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setTimeoutAfter(DISMISSED_TIMEOUT_MILLIS)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notification),
                        context.getString(R.string.undo),
                        undoIntent
                    ).build()
                )
            if (label.isNotEmpty()) builder.setContentText(label)
            manager.notify(DISMISSED_NOTIFICATION_ID, builder.build())
        }
    }

    private enum class RingState { IDLE, RINGING, STOPPED }

    private val stateLock = Any()
    private var ringState = RingState.IDLE
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoSnoozeRunnable = Runnable { autoSnooze() }

    private var gentlePlayer: MediaPlayer? = null
    private var gentleActive = false
    private val gentleStopRunnable = Runnable {
        stopGentle()
        if (!isRingActive()) stopSelf()
    }

    private fun claimRingEnd(): Boolean = synchronized(stateLock) {
        if (ringState != RingState.RINGING) return false
        ringState = RingState.STOPPED
        true
    }

    private fun isRingActive(): Boolean =
        synchronized(stateLock) { ringState == RingState.RINGING }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
            }
            ACTION_SNOOZE -> snoozeFromNotification()
            ACTION_GENTLE -> startGentle(intent)
            ACTION_STOP_GENTLE -> {
                stopGentle()
                if (!isRingActive()) stopSelf()
            }
            else -> {
                stopGentle()
                isRinging = true
                startAsForeground()
                startRinging()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopGentle()
        stopRinging()
        super.onDestroy()
    }

    private fun startAsForeground() {
        createAlarmChannel(this)
        startForegroundCompat(NOTIFICATION_ID, buildNotification())
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(id, notification, type)
        } else {
            startForeground(id, notification)
        }
    }

    private fun buildNotification(
        contentText: String = getString(R.string.alarm_notification_text)
    ): Notification {
        val settings = AlarmSettings(this)
        val label = AlarmStore(this).get(settings.ringingAlarmId)?.label.orEmpty()
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                label.ifEmpty { getString(R.string.alarm_notification_title) }
            )
            .setContentText(contentText)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
        if (Challenges.canSnooze(settings.maxSnoozes, settings.snoozeCount)) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.snooze_button, settings.snoozeMinutes),
                    PendingIntent.getService(
                        this,
                        1,
                        Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notification),
                getString(R.string.dismiss),
                fullScreenIntent
            ).build()
        )
        return builder.build()
    }

    private fun startRinging() {
        val settings = AlarmSettings(this)
        val maxRingMillis = settings.maxRingMinutes * 60_000L
        val rampMillis = settings.volumeRampSeconds * 1_000L

        val alreadyRinging = synchronized(stateLock) {
            if (ringState == RingState.RINGING) {
                true
            } else {
                ringState = RingState.RINGING
                false
            }
        }
        if (alreadyRinging) {
            handler.removeCallbacks(autoSnoozeRunnable)
            handler.postDelayed(autoSnoozeRunnable, maxRingMillis)
            autoSnoozeAtElapsed = SystemClock.elapsedRealtime() + maxRingMillis
            return
        }
        isRinging = true
        autoSnoozeAtElapsed = SystemClock.elapsedRealtime() + maxRingMillis

        HistoryStore(this).append(HistoryEvent.Type.RING)

        acquireWakeLock()

        handler.postDelayed(autoSnoozeRunnable, maxRingMillis)

        if (settings.vibrateEnabled) {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            if (rampMillis > 0L && v.hasAmplitudeControl()) {
                startVibrationRamp(v, rampMillis)
            } else {
                v.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)
                )
            }
        }

        requestAudioFocus()

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    maxOf(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2),
                    0
                )
            }
        } catch (_: SecurityException) {
        }

        fun startDefaultSound() {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri == null) {
                notifySoundUnavailable()
                return
            }
            startSound(uri, rampMillis, ::notifySoundUnavailable)
        }

        val ringingAlarm = AlarmStore(this).get(settings.ringingAlarmId)
        val customUri = (ringingAlarm?.soundUri ?: settings.alarmSoundUri)?.let(Uri::parse)
        if (customUri == null) {
            startDefaultSound()
        } else {
            startSound(customUri, rampMillis) { startDefaultSound() }
        }
    }

    private fun startSound(uri: Uri, rampMillis: Long, onFailure: () -> Unit) {
        val player = MediaPlayer()
        val started = try {
            player.apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnErrorListener { mp, _, _ ->
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                        mp.release()
                        onFailure()
                    }
                    true
                }
                setOnPreparedListener { mp ->
                    if (mediaPlayer !== mp) return@setOnPreparedListener
                    try {
                        val initialVolume = if (rampMillis == 0L) {
                            1f
                        } else {
                            maxOf(MIN_RAMP_VOLUME, 1f / VOLUME_RAMP_STEPS)
                        }
                        mp.setVolume(initialVolume, initialVolume)
                        mp.start()
                        if (rampMillis > 0L) startVolumeRamp(mp, rampMillis)
                    } catch (_: Exception) {
                        mediaPlayer = null
                        mp.release()
                        onFailure()
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            player.release()
            null
        }
        mediaPlayer = started
        if (started == null) onFailure()
    }

    private fun notifySoundUnavailable() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.alarm_notification_text_no_sound))
        )
    }

    private fun snoozeFromNotification() {
        if (!claimRingEnd()) return
        val settings = AlarmSettings(this)
        settings.snoozeCount++
        HistoryStore(this).append(HistoryEvent.Type.SNOOZE)
        AlarmScheduler(this).snooze(settings.ringingAlarmId)
        stopRinging()
        sendBroadcast(Intent(ACTION_AUTO_SNOOZED).setPackage(packageName))
        stopSelf()
    }

    private fun autoSnooze() {
        if (!claimRingEnd()) return
        val settings = AlarmSettings(this)
        settings.snoozeCount++
        HistoryStore(this).append(HistoryEvent.Type.AUTO_SNOOZE)
        AlarmScheduler(this).snooze(settings.ringingAlarmId)
        stopRinging()
        sendBroadcast(Intent(ACTION_AUTO_SNOOZED).setPackage(packageName))
        stopSelf()
    }


    private fun startGentle(intent: Intent) {
        if (isRingActive()) {
            startAsForeground()
            return
        }
        createGentleChannel()
        startForegroundCompat(
            GENTLE_NOTIFICATION_ID,
            buildGentleNotification(intent.getLongExtra(EXTRA_GENTLE_TRIGGER_AT, 0L))
        )
        handler.removeCallbacks(gentleStopRunnable)
        handler.postDelayed(gentleStopRunnable, GENTLE_MAX_MILLIS)
        if (gentleActive) return
        gentleActive = true

        val settings = AlarmSettings(this)
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        fun startDefault() {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri != null) startGentleSound(uri) {}
        }
        val customUri = (AlarmStore(this).get(alarmId)?.soundUri ?: settings.alarmSoundUri)
            ?.let(Uri::parse)
        if (customUri == null) startDefault() else startGentleSound(customUri, ::startDefault)
    }

    private fun startGentleSound(uri: Uri, onFailure: () -> Unit) {
        val player = MediaPlayer()
        val started = try {
            player.apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnErrorListener { mp, _, _ ->
                    if (gentlePlayer === mp) {
                        gentlePlayer = null
                        mp.release()
                        onFailure()
                    }
                    true
                }
                setOnPreparedListener { mp ->
                    if (gentlePlayer !== mp || !gentleActive) return@setOnPreparedListener
                    try {
                        mp.setVolume(GENTLE_VOLUME, GENTLE_VOLUME)
                        mp.start()
                    } catch (_: Exception) {
                        gentlePlayer = null
                        mp.release()
                        onFailure()
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            player.release()
            null
        }
        gentlePlayer = started
        if (started == null) onFailure()
    }

    private fun stopGentle() {
        handler.removeCallbacks(gentleStopRunnable)
        if (!gentleActive && gentlePlayer == null) return
        gentleActive = false
        gentlePlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        gentlePlayer = null
        if (!isRingActive()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        getSystemService(NotificationManager::class.java).cancel(GENTLE_NOTIFICATION_ID)
    }

    private fun createGentleChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                GENTLE_CHANNEL_ID,
                getString(R.string.gentle_wake_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildGentleNotification(triggerAtMillis: Long): Notification {
        val builder = Notification.Builder(this, GENTLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.gentle_wake_notification_title))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.gentle_wake_stop),
                    PendingIntent.getService(
                        this,
                        2,
                        Intent(this, AlarmService::class.java).setAction(ACTION_STOP_GENTLE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
        if (triggerAtMillis > 0L) {
            builder.setContentText(
                getString(
                    R.string.gentle_wake_notification_text,
                    TimeFormats.formatClock(this, triggerAtMillis)
                )
            )
        }
        return builder.build()
    }

    private fun startVolumeRamp(player: MediaPlayer, rampMillis: Long) {
        var step = 1
        fun volume() = maxOf(MIN_RAMP_VOLUME, step / VOLUME_RAMP_STEPS.toFloat())
        player.setVolume(volume(), volume())
        val stepMillis = rampMillis / VOLUME_RAMP_STEPS
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mediaPlayer !== player || step >= VOLUME_RAMP_STEPS) return
                step++
                try {
                    player.setVolume(volume(), volume())
                } catch (_: IllegalStateException) {
                    return
                }
                if (step < VOLUME_RAMP_STEPS) {
                    handler.postDelayed(this, stepMillis)
                }
            }
        }, stepMillis)
    }

    private fun startVibrationRamp(v: Vibrator, rampMillis: Long) {
        fun vibrateAt(amplitude: Int) {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 800, 400),
                    intArrayOf(0, amplitude, 0),
                    0
                )
            )
        }

        vibrateAt(VIBRATION_RAMP_AMPLITUDES.first())
        val stepMillis = rampMillis / VIBRATION_RAMP_AMPLITUDES.size
        VIBRATION_RAMP_AMPLITUDES.drop(1).forEachIndexed { index, amplitude ->
            handler.postDelayed(
                {
                    if (isRingActive() && vibrator === v) vibrateAt(amplitude)
                },
                stepMillis * (index + 1)
            )
        }
    }

    private fun stopRinging() {
        synchronized(stateLock) {
            if (ringState == RingState.RINGING) ringState = RingState.STOPPED
        }
        isRinging = false
        autoSnoozeAtElapsed = 0L
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        audioFocusRequest?.let {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepAlarm:ringing")
            .apply { acquire(60 * 60_000L) }
    }
}
