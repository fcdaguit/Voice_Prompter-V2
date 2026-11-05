package com.voiceprompter.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Thin wrapper over the platform [TextToSpeech] engine.
 *
 * Each sentence is queued as its own utterance with the sentence index as the
 * utterance id, so [onSentenceFinished] gives us accurate progress for
 * highlighting without relying on timing estimates.
 */
class TtsEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<() -> Unit>()

    var onReady: (() -> Unit)? = null
    var onSentenceStarted: ((Int) -> Unit)? = null
    var onSentenceFinished: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setOnUtteranceProgressListener(listener)
                pending.forEach { it() }
                pending.clear()
                onReady?.invoke()
            } else {
                onError?.invoke(
                    "No text-to-speech engine is available. Install one from Play Store, " +
                        "or enable it in Settings > Accessibility > Text-to-speech."
                )
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.toIntOrNull()?.let { onSentenceStarted?.invoke(it) }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.toIntOrNull()?.let { onSentenceFinished?.invoke(it) }
        }

        // Abstract in the platform class, so it must be overridden even though
        // the two-argument form below is the one modern engines call.
        override fun onError(utteranceId: String?) {
            this@TtsEngine.onError?.invoke("Speech failed")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            this@TtsEngine.onError?.invoke("Speech failed (code $errorCode)")
        }
    }

    private fun whenReady(block: () -> Unit) {
        if (ready) block() else pending += block
    }

    fun availableVoices(): List<Voice> = runCatching {
        tts?.voices
            ?.filter { !it.isNetworkConnectionRequired && !it.features.orEmpty().contains("notInstalled") }
            ?.sortedBy { it.locale.displayName + it.name }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun applySettings(voiceName: String?, rate: Float, pitch: Float) = whenReady {
        val engine = tts ?: return@whenReady
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)

        val voice = voiceName?.let { name -> engine.voices?.firstOrNull { it.name == name } }
        if (voice != null) {
            engine.voice = voice
        } else {
            runCatching { engine.language = Locale.getDefault() }
        }
    }

    /** Speaks [text], flushing anything already queued. */
    fun speak(text: String, utteranceId: Int) = whenReady {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId.toString())
    }

    /** Speaks a short sample so the user can audition a voice before choosing it. */
    fun preview(voiceName: String?, rate: Float, pitch: Float, sample: String) {
        applySettings(voiceName, rate, pitch)
        whenReady {
            tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
