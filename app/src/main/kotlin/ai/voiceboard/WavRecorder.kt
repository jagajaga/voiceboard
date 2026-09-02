package ai.voiceboard

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Records raw PCM16 mono audio via [AudioRecord] and writes it directly to a WAV file.
 *
 * Why not MediaRecorder: MediaRecorder's AAC encoder pipeline buffers audio internally and
 * flushes asynchronously. Calling stop() right as the user finishes speaking (which is exactly
 * when they tap "stop") can drop the last ~0.3-1s of audio that hadn't been flushed to the
 * container yet — a known Android race. AudioRecord gives us the raw PCM read loop directly: the
 * last successful read() before we stop the loop is guaranteed to already be written to the file,
 * so there is no async encoder queue to race against.
 *
 * [onAmplitude] is invoked from the background recording thread with a normalized 0..1 sample
 * roughly every read-buffer cycle (~30-60ms) — safe to feed straight into [WaveformView].
 */
class WavRecorder(
    private val sampleRate: Int = 16_000,
    private val onAmplitude: (Float) -> Unit
) {
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        .coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    private var pcmFile: File? = null
    @Volatile private var totalPcmBytes: Long = 0

    /** Starts recording into a fresh file under [cacheDir] and returns that file immediately. */
    fun start(cacheDir: File): File {
        val file = File(cacheDir, "voiceboard_${System.currentTimeMillis()}.wav")
        pcmFile = file
        totalPcmBytes = 0

        // Reserve the 44-byte WAV header; patched with real sizes once we know the final length.
        RandomAccessFile(file, "rw").use { it.setLength(44) }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, minBufferSize * 2
        )
        record.startRecording()
        audioRecord = record
        isRecording = true

        recordingThread = Thread {
            val buffer = ShortArray(minBufferSize / 2)
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(44)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        reportAmplitude(buffer, read)

                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val v = buffer[i].toInt()
                            bytes[i * 2] = (v and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                        }
                        raf.write(bytes)
                        totalPcmBytes += bytes.size
                    }
                }
            }
        }.apply { start() }

        return file
    }

    private fun reportAmplitude(buffer: ShortArray, read: Int) {
        var sum = 0.0
        for (i in 0 until read) sum += (buffer[i] * buffer[i]).toDouble()
        val rms = sqrt(sum / read)
        // 6000 ≈ a normal speaking-volume RMS ceiling on int16 PCM; sqrt boosts quiet passages
        // so the waveform stays lively instead of flatlining during soft speech.
        val normalized = (rms / 6000.0).coerceIn(0.0, 1.0)
        onAmplitude(sqrt(normalized).toFloat())
    }

    /** Stops recording, patches the WAV header with final sizes, and returns the finished file. */
    fun stop(): File? {
        isRecording = false
        try {
            recordingThread?.join(500)
        } catch (_: InterruptedException) {
        }
        recordingThread = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null

        val file = pcmFile ?: return null
        writeWavHeader(file, totalPcmBytes, sampleRate)
        return file
    }

    private fun writeWavHeader(file: File, pcmBytes: Long, sampleRate: Int) {
        val byteRate = sampleRate * 2 // mono, 16-bit
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + pcmBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM chunk size
        header.putShort(1) // PCM format
        header.putShort(1) // mono channel
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2) // block align (channels * bitsPerSample / 8)
        header.putShort(16) // bits per sample
        header.put("data".toByteArray())
        header.putInt(pcmBytes.toInt())

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}
