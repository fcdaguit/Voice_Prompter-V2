package com.voiceprompter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Prefs(
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val theme: ThemeMode = ThemeMode.DARK,
    val fontSizeSp: Int = 28,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xFF000000,
    val volumeKeyNavigation: Boolean = false,
    val keepScreenOn: Boolean = true,
    val lastScriptId: Long = -1L,
    val lastPosition: Int = 0
)

/**
 * SharedPreferences rather than DataStore: this app stores a dozen scalars, and
 * SharedPreferences costs no extra dependency or APK weight.
 */
class UserPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state.asStateFlow()

    private fun read() = Prefs(
        voiceName = sp.getString(KEY_VOICE, null),
        speechRate = sp.getFloat(KEY_RATE, 1.0f),
        pitch = sp.getFloat(KEY_PITCH, 1.0f),
        theme = runCatching {
            ThemeMode.valueOf(sp.getString(KEY_THEME, ThemeMode.DARK.name)!!)
        }.getOrDefault(ThemeMode.DARK),
        fontSizeSp = sp.getInt(KEY_FONT_SIZE, 28),
        textColor = sp.getLong(KEY_TEXT_COLOR, 0xFFFFFFFF),
        backgroundColor = sp.getLong(KEY_BG_COLOR, 0xFF000000),
        volumeKeyNavigation = sp.getBoolean(KEY_VOLUME_NAV, false),
        keepScreenOn = sp.getBoolean(KEY_KEEP_SCREEN_ON, true),
        lastScriptId = sp.getLong(KEY_LAST_SCRIPT, -1L),
        lastPosition = sp.getInt(KEY_LAST_POSITION, 0)
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _state.value = read()
    }

    fun setVoice(name: String?) = edit { putString(KEY_VOICE, name) }
    fun setSpeechRate(rate: Float) = edit { putFloat(KEY_RATE, rate.coerceIn(0.25f, 3.0f)) }
    fun setPitch(pitch: Float) = edit { putFloat(KEY_PITCH, pitch.coerceIn(0.5f, 2.0f)) }
    fun setTheme(mode: ThemeMode) = edit { putString(KEY_THEME, mode.name) }
    fun setFontSize(sp2: Int) = edit { putInt(KEY_FONT_SIZE, sp2.coerceIn(14, 72)) }
    fun setTextColor(color: Long) = edit { putLong(KEY_TEXT_COLOR, color) }
    fun setBackgroundColor(color: Long) = edit { putLong(KEY_BG_COLOR, color) }
    fun setVolumeKeyNavigation(enabled: Boolean) = edit { putBoolean(KEY_VOLUME_NAV, enabled) }
    fun setKeepScreenOn(enabled: Boolean) = edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }

    fun setLastOpened(scriptId: Long, position: Int) = edit {
        putLong(KEY_LAST_SCRIPT, scriptId)
        putInt(KEY_LAST_POSITION, position)
    }

    private companion object {
        const val KEY_VOICE = "voice"
        const val KEY_RATE = "rate"
        const val KEY_PITCH = "pitch"
        const val KEY_THEME = "theme"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_TEXT_COLOR = "text_color"
        const val KEY_BG_COLOR = "bg_color"
        const val KEY_VOLUME_NAV = "volume_nav"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_LAST_SCRIPT = "last_script"
        const val KEY_LAST_POSITION = "last_position"
    }
}
