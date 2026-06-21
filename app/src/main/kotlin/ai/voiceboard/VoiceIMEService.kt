package ai.voiceboard

import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.view.View
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

        val file = audioFile ?: run {
            reset(); return
        }

        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    WhisperApi.transcribe(
                        audioFile = file,
                        apiKey    = BuildConfig.OPENAI_API_KEY,
                        model     = BuildConfig.WHISPER_MODEL,
                    )
                }
            }

            file.delete()

            text.onSuccess { transcript ->
                if (transcript.isNotEmpty()) {
                    // Append a trailing space so the next word doesn't merge
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
