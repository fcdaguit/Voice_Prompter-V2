package com.voiceprompter.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val scriptId: Long = -1L,
    val title: String = "",
    val sentences: List<String> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    /** True between service start and TTS reporting itself ready. */
    val isPreparing: Boolean = false,
    val error: String? = null
) {
    val hasScript: Boolean get() = sentences.isNotEmpty()
    val currentSentence: String? get() = sentences.getOrNull(index)
    val atEnd: Boolean get() = sentences.isNotEmpty() && index >= sentences.lastIndex
}

/**
 * Single source of truth for what is being spoken. The service writes to it, the
 * UI observes it. A plain singleton rather than a bound service connection: the
 * UI never needs to call into the service synchronously, only to send intents.
 */
class PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun load(scriptId: Long, title: String, sentences: List<String>, startIndex: Int) {
        _state.value = PlaybackState(
            scriptId = scriptId,
            title = title,
            sentences = sentences,
            index = startIndex.coerceIn(0, maxOf(0, sentences.lastIndex)),
            isPlaying = false
        )
    }

    fun setIndex(index: Int) {
        val sentences = _state.value.sentences
        if (sentences.isEmpty()) return
        _state.value = _state.value.copy(index = index.coerceIn(0, sentences.lastIndex))
    }

    fun setPlaying(playing: Boolean) {
        _state.value = _state.value.copy(isPlaying = playing, isPreparing = false)
    }

    fun setPreparing(preparing: Boolean) {
        _state.value = _state.value.copy(isPreparing = preparing)
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(error = message, isPlaying = false, isPreparing = false)
    }

    fun clear() {
        _state.value = PlaybackState()
    }
}
