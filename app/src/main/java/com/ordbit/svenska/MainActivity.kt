package com.ordbit.svenska

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ordbit.svenska.ui.OrdbitApp
import java.util.Locale

/**
 * Single-activity app. Hosts the Compose UI and an on-device
 * TextToSpeech engine for Swedish pronunciation.
 */
class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the bundled dictionary from assets/vocab.json (no network).
        Vocab.load(applicationContext)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("sv", "SE"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        setContent {
            OrdbitApp(
                speak = { text ->
                    if (ttsReady) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ordbit-utterance")
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
