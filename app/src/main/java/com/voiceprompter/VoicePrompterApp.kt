package com.voiceprompter

import android.app.Application
import android.content.Context
import com.voiceprompter.data.AppDatabase
import com.voiceprompter.data.DocumentImporter
import com.voiceprompter.data.ScriptRepository
import com.voiceprompter.data.UserPrefs
import com.voiceprompter.tts.PlaybackController

/**
 * Manual dependency container instead of Hilt.
 *
 * Four singletons do not justify an annotation processor, and skipping KSP for DI
 * keeps build times and method count down.
 */
class AppContainer(context: Context) {
    private val database by lazy { AppDatabase.build(context) }
    val scriptRepository by lazy { ScriptRepository(database.scriptDao()) }
    val userPrefs by lazy { UserPrefs(context) }
    val documentImporter by lazy { DocumentImporter(context) }
    val playbackController by lazy { PlaybackController() }
}

class VoicePrompterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
