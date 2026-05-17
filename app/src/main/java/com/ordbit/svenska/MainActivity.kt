package com.ordbit.svenska

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.ordbit.svenska.ui.OrdbitApp
import java.util.Locale

/**
 * Single-activity app. Hosts the Compose UI, an on-device TextToSpeech engine
 * for Swedish pronunciation, and the file pickers used to export / import
 * progress. Everything is on-device — no network, no account.
 */
class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Set just before a picker is launched, then consumed by its callback.
    private var pendingExportText: String? = null
    private var onImportLoaded: ((String) -> Unit)? = null

    // Picker: create a new document, then write the backup text into it.
    private val createBackup =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            val text = pendingExportText
            pendingExportText = null
            if (uri != null && text != null) {
                val ok = writeTextToUri(uri, text)
                toast(if (ok) "Framsteg sparade" else "Kunde inte spara filen")
            }
        }

    // Picker: open an existing document, then read the backup text from it.
    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val cb = onImportLoaded
            onImportLoaded = null
            if (uri != null && cb != null) {
                val text = readTextFromUri(uri)
                if (text != null) cb(text)
                else toast("Kunde inte l\u00e4sa filen")
            }
        }

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
                },
                onExport = { text ->
                    pendingExportText = text
                    createBackup.launch("ordbit-framsteg.json")
                },
                onImport = { loaded ->
                    onImportLoaded = loaded
                    openBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            )
        }
    }

    private fun writeTextToUri(uri: Uri, text: String): Boolean = try {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun readTextFromUri(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
