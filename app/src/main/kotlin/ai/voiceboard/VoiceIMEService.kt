package ai.voiceboard

import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.File

class VoiceIMEService : InputMethodService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private lateinit var btnRecord: Button
    private lateinit var tvStatus: TextView

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        btnRecord = view.findViewById(R.id.btnRecord)
        tvStatus  = view.findViewById(R.id.tvStatus)

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        // ⌫ Delete: removes selected text, or one character before the cursor
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val ic = currentInputConnection ?: return@setOnClickListener
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                // Replace selection with nothing
                ic.commitText("", 1)
            } else {
                // Standard backspace
                ic.deleteSurroundingText(1, 0)
            }
        }

        // ⌨ Switch: go back to the previously used keyboard
        view.findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+: switch directly to the last-used IME (no picker needed)
                switchToLastInputMethod()
            } else {
                // Older: show the system IME picker
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        recorder?.release()
        recorder = null
    }

    // ── Recording ──────────────────────────────────────────────────────────────

    private fun startRecording() {
        if (!Prefs.hasApiKey(applicationContext)) {
            tvStatus.text = "⚠ No API key — open VoiceBoard app to set it"
            return
        }

        val file = File(cacheDir, "voiceboard_${System.currentTimeMillis()}.m4a")
        audioFile = file

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        ).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        isRecording = true
        btnRecord.text = "⏹  Stop"
        btnRecord.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFFB03030.toInt())
        tvStatus.text = "Recording…"
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.isEnabled = false
        btnRecord.text = "⏳  Transcribing…"
        tvStatus.text = ""

        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {
        } finally {
            recorder = null
        }

        val file = audioFile ?: run { reset(); return }

        scope.launch {
            val apiKey = Prefs.getApiKey(applicationContext)
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    WhisperApi.transcribe(
                        audioFile = file,
                        apiKey    = apiKey,
                        model     = Prefs.getModel(applicationContext),
                    )
                }
            }

            file.delete()

            text.onSuccess { transcript ->
                if (transcript.isNotEmpty()) {
                    currentInputConnection?.commitText("$transcript ", 1)
                    tvStatus.text = "✓ Done"
                } else {
                    tvStatus.text = "Nothing heard — try again"
                }
            }.onFailure { err ->
                tvStatus.text = "Error: ${err.message}"
            }

            reset()
        }
    }

    private fun reset() {
        btnRecord.isEnabled = true
        btnRecord.text = "🎤  Record"
        btnRecord.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF3A3A6E.toInt())
    }
}
