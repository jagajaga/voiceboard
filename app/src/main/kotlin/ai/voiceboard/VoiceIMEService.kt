package ai.voiceboard

import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.File

class VoiceIMEService : InputMethodService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Recording state ────────────────────────────────────────────────────────
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    private enum class Mode { IDLE, RECORDING_DICTATE, RECORDING_REPHRASE }
    private var mode = Mode.IDLE

    // Selected text captured when Rephrase is tapped
    private var pendingSelectedText: String = ""

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var btnRecord:   Button
    private lateinit var btnRephrase: Button
    private lateinit var tvStatus:    TextView

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        btnRecord   = view.findViewById(R.id.btnRecord)
        btnRephrase = view.findViewById(R.id.btnRephrase)
        tvStatus    = view.findViewById(R.id.tvStatus)

        btnRecord.setOnClickListener {
            when (mode) {
                Mode.IDLE             -> startRecording(Mode.RECORDING_DICTATE)
                Mode.RECORDING_DICTATE -> stopRecording()
                Mode.RECORDING_REPHRASE -> { /* ignore taps on record while rephrasing */ }
            }
        }

        btnRephrase.setOnClickListener {
            when (mode) {
                Mode.IDLE -> {
                    val selected = currentInputConnection?.getSelectedText(0)?.toString()
                    if (selected.isNullOrEmpty()) {
                        tvStatus.text = "Select some text first"
                        return@setOnClickListener
                    }
                    pendingSelectedText = selected
                    startRecording(Mode.RECORDING_REPHRASE)
                }
                Mode.RECORDING_REPHRASE -> stopRecording()
                else -> { /* ignore */ }
            }
        }

        // ⌫ Delete: removes selected text, or one character before the cursor
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val ic = currentInputConnection ?: return@setOnClickListener
            if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1)
            else ic.deleteSurroundingText(1, 0)
        }

        // ⌨ Switch keyboard
        view.findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }

        return view
    }

    /** Called by Android whenever the selection/cursor changes — show/hide Rephrase. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!::btnRephrase.isInitialized) return
        val hasSelection = newSelEnd > newSelStart
        btnRephrase.visibility = if (hasSelection && mode == Mode.IDLE) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        recorder?.release()
        recorder = null
    }

    // ── Recording ──────────────────────────────────────────────────────────────

    private fun startRecording(target: Mode) {
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

        mode = target
        btnRephrase.visibility = View.GONE

        when (target) {
            Mode.RECORDING_DICTATE -> {
                btnRecord.text = "⏹  Stop"
                btnRecord.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF5C2A2A.toInt())
                tvStatus.text = "Recording…"
            }
            Mode.RECORDING_REPHRASE -> {
                btnRephrase.visibility = View.VISIBLE
                btnRephrase.text = "⏹  Stop instruction"
                btnRephrase.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(0xFF5C2A2A.toInt())
                tvStatus.text = "Say how to rephrase…"
            }
            else -> {}
        }
    }

    private fun stopRecording() {
        val capturedMode = mode
        mode = Mode.IDLE

        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {
        } finally {
            recorder = null
        }

        val file = audioFile ?: run { resetUI(); return }
        audioFile = null

        when (capturedMode) {
            Mode.RECORDING_DICTATE  -> processDictation(file)
            Mode.RECORDING_REPHRASE -> processRephrase(file, pendingSelectedText)
            else -> { file.delete(); resetUI() }
        }
    }

    // ── Dictation ─────────────────────────────────────────────────────────────

    private fun processDictation(file: File) {
        btnRecord.isEnabled = false
        btnRecord.text = "⏳  Transcribing…"
        tvStatus.text = ""

        scope.launch {
            val apiKey = Prefs.getApiKey(applicationContext)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    WhisperApi.transcribe(file, apiKey, Prefs.getModel(applicationContext))
                }
            }
            file.delete()

            result.onSuccess { text ->
                if (text.isNotEmpty()) {
                    currentInputConnection?.commitText("$text ", 1)
                    tvStatus.text = "✓ Done"
                } else {
                    tvStatus.text = "Nothing heard — try again"
                }
            }.onFailure {
                tvStatus.text = "Error: ${it.message}"
            }

            resetUI()
        }
    }

    // ── Rephrase ──────────────────────────────────────────────────────────────

    private fun processRephrase(file: File, selectedText: String) {
        btnRephrase.visibility = View.VISIBLE
        btnRephrase.isEnabled  = false
        btnRephrase.text = "⏳  Rephrasing…"
        tvStatus.text = ""

        scope.launch {
            val apiKey = Prefs.getApiKey(applicationContext)

            // Step 1: transcribe the voice instruction
            val instruction = withContext(Dispatchers.IO) {
                runCatching {
                    WhisperApi.transcribe(file, apiKey, Prefs.getModel(applicationContext))
                }
            }
            file.delete()

            if (instruction.isFailure || instruction.getOrDefault("").isEmpty()) {
                tvStatus.text = "Couldn't hear the instruction — try again"
                resetUI(); return@launch
            }

            tvStatus.text = "\"${instruction.getOrDefault("")}\" …"

            // Step 2: rephrase with GPT-4o-mini
            val rephrased = withContext(Dispatchers.IO) {
                runCatching {
                    ChatApi.rephrase(selectedText, instruction.getOrDefault(""), apiKey)
                }
            }

            rephrased.onSuccess { text ->
                if (text.isNotEmpty()) {
                    currentInputConnection?.commitText(text, 1)
                    tvStatus.text = "✓ Rephrased"
                } else {
                    tvStatus.text = "Empty result — try again"
                }
            }.onFailure {
                tvStatus.text = "Error: ${it.message}"
            }

            resetUI()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resetUI() {
        btnRecord.isEnabled = true
        btnRecord.text = "🎤  Record"
        btnRecord.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF3A4438.toInt())

        btnRephrase.isEnabled = true
        btnRephrase.text = "✏️  Rephrase selection"
        btnRephrase.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF2A3828.toInt())
        // Rephrase visibility is controlled by onUpdateSelection
        btnRephrase.visibility = View.GONE
    }
}
