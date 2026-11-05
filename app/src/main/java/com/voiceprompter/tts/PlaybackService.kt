package com.voiceprompter.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState as SystemPlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voiceprompter.R
import com.voiceprompter.VoicePrompterApp
import com.voiceprompter.data.ScriptRepository
import com.voiceprompter.data.UserPrefs
import com.voiceprompter.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the TTS engine for the life of a playback session.
 *
 * Running as a foreground service with `mediaPlayback` type is what lets speech
 * continue with the screen off or another app in front. The MediaSession is what
 * lets Bluetooth headset media buttons reach us while the app is not focused.
 */
class PlaybackService : Service() {

    private lateinit var controller: PlaybackController
    private lateinit var repository: ScriptRepository
    private lateinit var prefs: UserPrefs
    private var tts: TtsEngine? = null
    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var pausedForFocusLoss = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as VoicePrompterApp).container
        controller = container.playbackController
        repository = container.scriptRepository
        prefs = container.userPrefs
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createChannel()
        setUpMediaSession()
        setUpTts()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE -> if (controller.state.value.isPlaying) pause() else play()
            ACTION_NEXT -> skip(+1)
            ACTION_PREVIOUS -> skip(-1)
            ACTION_SEEK -> {
                val index = intent.getIntExtra(EXTRA_INDEX, 0)
                controller.setIndex(index)
                if (controller.state.value.isPlaying) speakCurrent() else updateNotification()
            }
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    // ---- setup -------------------------------------------------------------

    private fun setUpTts() {
        tts = TtsEngine(this).apply {
            onReady = {
                val p = prefs.state.value
                applySettings(p.voiceName, p.speechRate, p.pitch)
            }
            onSentenceStarted = { index ->
                controller.setIndex(index)
                updateNotification()
            }
            onSentenceFinished = { index ->
                // Advance only if we are still playing and still on that sentence.
                val state = controller.state.value
                if (state.isPlaying && state.index == index) {
                    if (index >= state.sentences.lastIndex) {
                        finishPlayback()
                    } else {
                        controller.setIndex(index + 1)
                        speakCurrent()
                    }
                }
            }
            onError = { message ->
                controller.setError(message)
                stopPlayback()
            }
        }
    }

    private fun setUpMediaSession() {
        mediaSession = MediaSession(this, "VoicePrompter").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onStop() {
                    stopPlayback()
                    stopSelf()
                }
                override fun onSkipToNext() = skip(+1)
                override fun onSkipToPrevious() = skip(-1)
            })
            isActive = true
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_playback_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_playback_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    // ---- transport ---------------------------------------------------------

    private fun play() {
        val state = controller.state.value
        if (!state.hasScript) return
        if (!requestAudioFocus()) {
            controller.setError("Another app is using the audio output")
            return
        }
        controller.setPlaying(true)
        startForegroundCompat()
        speakCurrent()
    }

    private fun pause() {
        tts?.stop()
        controller.setPlaying(false)
        abandonAudioFocus()
        persistPosition()
        updateNotification()
    }

    private fun skip(delta: Int) {
        val state = controller.state.value
        if (!state.hasScript) return
        controller.setIndex(state.index + delta)
        if (controller.state.value.isPlaying) speakCurrent() else updateNotification()
    }

    private fun speakCurrent() {
        val state = controller.state.value
        val sentence = state.currentSentence ?: return finishPlayback()
        tts?.speak(sentence, state.index)
        updateNotification()
    }

    private fun finishPlayback() {
        controller.setPlaying(false)
        abandonAudioFocus()
        persistPosition()
        updateNotification()
    }

    private fun stopPlayback() {
        tts?.stop()
        controller.setPlaying(false)
        abandonAudioFocus()
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun persistPosition() {
        val state = controller.state.value
        if (state.scriptId <= 0) return
        prefs.setLastOpened(state.scriptId, state.index)
        scope.launch { repository.savePosition(state.scriptId, state.index) }
    }

    // ---- audio focus -------------------------------------------------------

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        stopPlayback()
                        stopSelf()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        if (controller.state.value.isPlaying) {
                            pausedForFocusLoss = true
                            pause()
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (pausedForFocusLoss) {
                            pausedForFocusLoss = false
                            play()
                        }
                    }
                }
            }
            .build()

        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ---- notification ------------------------------------------------------

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val state = controller.state.value
        mediaSession?.setPlaybackState(
            SystemPlaybackState.Builder()
                .setActions(
                    SystemPlaybackState.ACTION_PLAY or
                        SystemPlaybackState.ACTION_PAUSE or
                        SystemPlaybackState.ACTION_PLAY_PAUSE or
                        SystemPlaybackState.ACTION_SKIP_TO_NEXT or
                        SystemPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        SystemPlaybackState.ACTION_STOP
                )
                .setState(
                    if (state.isPlaying) SystemPlaybackState.STATE_PLAYING
                    else SystemPlaybackState.STATE_PAUSED,
                    SystemPlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = controller.state.value

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progress = if (state.sentences.isEmpty()) {
            ""
        } else {
            "Line ${state.index + 1} of ${state.sentences.size}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prompter)
            .setContentTitle(state.title.ifBlank { getString(R.string.app_name) })
            .setContentText(progress)
            .setContentIntent(contentIntent)
            .setOngoing(state.isPlaying)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_notif_previous, "Previous", pendingAction(ACTION_PREVIOUS))
            .addAction(
                if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (state.isPlaying) "Pause" else "Play",
                pendingAction(if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(R.drawable.ic_notif_next, "Next", pendingAction(ACTION_NEXT))
            .addAction(R.drawable.ic_notif_stop, "Stop", pendingAction(ACTION_STOP))
            .build()
    }

    private fun pendingAction(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    override fun onDestroy() {
        stopPlayback()
        mediaSession?.release()
        mediaSession = null
        tts?.shutdown()
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.voiceprompter.PLAY"
        const val ACTION_PAUSE = "com.voiceprompter.PAUSE"
        const val ACTION_TOGGLE = "com.voiceprompter.TOGGLE"
        const val ACTION_NEXT = "com.voiceprompter.NEXT"
        const val ACTION_PREVIOUS = "com.voiceprompter.PREVIOUS"
        const val ACTION_STOP = "com.voiceprompter.STOP"
        const val ACTION_SEEK = "com.voiceprompter.SEEK"
        const val EXTRA_INDEX = "index"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1

        fun send(context: Context, action: String, index: Int? = null) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(action)
                .apply { index?.let { putExtra(EXTRA_INDEX, it) } }
            context.startForegroundService(intent)
        }
    }
}
