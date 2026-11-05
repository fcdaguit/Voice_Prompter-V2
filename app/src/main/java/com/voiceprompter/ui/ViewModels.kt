package com.voiceprompter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voiceprompter.VoicePrompterApp
import com.voiceprompter.data.DocumentImporter
import com.voiceprompter.data.Script
import com.voiceprompter.data.ThemeMode
import com.voiceprompter.tts.PlaybackController
import com.voiceprompter.tts.Sentences
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScriptListViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as VoicePrompterApp).container
    private val repository = container.scriptRepository
    private val importer: DocumentImporter = container.documentImporter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val scripts: StateFlow<List<Script>> = _query
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repository.create())
    }

    fun duplicate(id: Long) = viewModelScope.launch { repository.duplicate(id) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun import(uri: android.net.Uri, onImported: (Long) -> Unit) = viewModelScope.launch {
        when (val result = importer.import(uri)) {
            is DocumentImporter.Result.Success -> {
                val id = repository.create(result.title, result.body)
                onImported(id)
            }
            is DocumentImporter.Result.Failure -> _message.value = result.reason
        }
    }
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as VoicePrompterApp).container.scriptRepository

    private val _script = MutableStateFlow<Script?>(null)
    val script: StateFlow<Script?> = _script.asStateFlow()

    private var loadedId: Long = -1L

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch { _script.value = repository.get(id) }
    }

    /** Auto-save: every edit is written through, debounced by the caller's typing. */
    fun update(title: String, body: String) {
        val current = _script.value ?: return
        _script.value = current.copy(title = title, body = body)
        viewModelScope.launch { repository.save(current.id, title, body) }
    }
}

class PrompterViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as VoicePrompterApp).container
    private val repository = container.scriptRepository
    private val prefs = container.userPrefs
    val controller: PlaybackController = container.playbackController

    val playback = controller.state
    val preferences = prefs.state

    private var preparedId: Long = -1L

    /** Splits the script and hands it to the shared controller, resuming where we left off. */
    fun prepare(scriptId: Long) {
        if (preparedId == scriptId) return
        preparedId = scriptId
        viewModelScope.launch {
            val script = repository.get(scriptId) ?: return@launch
            val sentences = Sentences.split(script.body)
            controller.load(scriptId, script.title, sentences, script.lastPosition)
        }
    }

    fun release() {
        preparedId = -1L
    }
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as VoicePrompterApp).container
    private val prefs = container.userPrefs

    val preferences = prefs.state

    fun setVoice(name: String?) = prefs.setVoice(name)
    fun setRate(rate: Float) = prefs.setSpeechRate(rate)
    fun setPitch(pitch: Float) = prefs.setPitch(pitch)
    fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)
    fun setFontSize(size: Int) = prefs.setFontSize(size)
    fun setTextColor(color: Long) = prefs.setTextColor(color)
    fun setBackgroundColor(color: Long) = prefs.setBackgroundColor(color)
    fun setVolumeKeyNavigation(enabled: Boolean) = prefs.setVolumeKeyNavigation(enabled)
    fun setKeepScreenOn(enabled: Boolean) = prefs.setKeepScreenOn(enabled)
}
