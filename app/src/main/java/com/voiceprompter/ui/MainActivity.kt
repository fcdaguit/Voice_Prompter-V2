package com.voiceprompter.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voiceprompter.VoicePrompterApp
import com.voiceprompter.tts.PlaybackService
import com.voiceprompter.ui.theme.VoicePrompterTheme

/**
 * Four screens and no deep links, so navigation is a sealed class and a single
 * back stack list. Pulling in navigation-compose would add weight for nothing.
 */
sealed interface Screen {
    data object List : Screen
    data class Editor(val scriptId: Long) : Screen
    data class Prompter(val scriptId: Long) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as VoicePrompterApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    /**
     * Volume-key section navigation, when the user has opted in.
     *
     * Note this only works while the activity has focus. Volume keys are not
     * deliverable to a backgrounded app, so with the screen off, navigation has
     * to come from headset media buttons via the MediaSession instead.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val prefs = container.userPrefs.state.value
        val playback = container.playbackController.state.value
        if (prefs.volumeKeyNavigation && playback.hasScript) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    PlaybackService.send(this, PlaybackService.ACTION_PREVIOUS)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    PlaybackService.send(this, PlaybackService.ACTION_NEXT)
                    return true
                }
            }
        }
        // Bluetooth presentation remotes usually emit these as keyboard events.
        if (playback.hasScript) {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    PlaybackService.send(this, PlaybackService.ACTION_NEXT)
                    return true
                }
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    PlaybackService.send(this, PlaybackService.ACTION_PREVIOUS)
                    return true
                }
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    PlaybackService.send(this, PlaybackService.ACTION_TOGGLE)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun AppRoot() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val prefs by settingsViewModel.preferences.collectAsState()

    var screen by rememberSaveable(
        stateSaver = ScreenSaver
    ) { mutableStateOf<Screen>(Screen.List) }

    val back = remember { mutableListOf<Screen>() }

    fun go(target: Screen) {
        back += screen
        screen = target
    }

    fun pop() {
        screen = back.removeLastOrNull() ?: Screen.List
    }

    VoicePrompterTheme(mode = prefs.theme) {
        when (val current = screen) {
            is Screen.List -> ScriptListScreen(
                onOpen = { go(Screen.Editor(it)) },
                onSettings = { go(Screen.Settings) }
            )

            is Screen.Editor -> EditorScreen(
                scriptId = current.scriptId,
                onBack = ::pop,
                onPlay = { go(Screen.Prompter(current.scriptId)) }
            )

            is Screen.Prompter -> PrompterScreen(
                scriptId = current.scriptId,
                onBack = ::pop
            )

            is Screen.Settings -> SettingsScreen(onBack = ::pop)
        }
    }
}

/** Minimal saver so the current screen survives rotation and process death. */
private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, List<Any>>(
    save = {
        when (it) {
            is Screen.List -> listOf("list")
            is Screen.Settings -> listOf("settings")
            is Screen.Editor -> listOf("editor", it.scriptId)
            is Screen.Prompter -> listOf("prompter", it.scriptId)
        }
    },
    restore = {
        when (it.firstOrNull()) {
            "editor" -> Screen.Editor(it[1] as Long)
            "prompter" -> Screen.Prompter(it[1] as Long)
            "settings" -> Screen.Settings
            else -> Screen.List
        }
    }
)
