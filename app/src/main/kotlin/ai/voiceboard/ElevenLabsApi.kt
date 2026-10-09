package ai.voiceboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * ElevenLabs Speech-to-Text client — Scribe v2, their latest/most accurate ASR model.
 * https://elevenlabs.io/docs/api-reference/speech-to-text/convert
 */
object ElevenLabsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe [audioFile] (WAV/M4A) using ElevenLabs Speech-to-Text.
     * [modelId] is normally "scribe_v2". Returns the transcribed text, or throws on failure.
     */
    @Throws(IOException::class)
    fun transcribe(audioFile: File, apiKey: String, modelId: String): String {
        val mime = if (audioFile.extension.equals("wav", ignoreCase = true))
            "audio/wav" else "audio/m4a"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mime.toMediaType())
            )
            .addFormDataPart("model_id", modelId)
            .build()

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/speech-to-text")
            .header("xi-api-key", apiKey)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from ElevenLabs API")

            if (!response.isSuccessful) {
                throw IOException("ElevenLabs API error ${response.code}: $responseBody")
            }

            // Top-level response: {"language_code":"en",...,"text":"...","words":[...]}
            return extractJsonText(responseBody)
        }
    }

    /** Minimal top-level "text" field extraction without a JSON library. */
    private fun extractJsonText(json: String): String {
        val key = "\"text\""
        val keyIdx = json.indexOf(key)
        if (keyIdx < 0) throw IOException("No 'text' field in response: $json")
        val colon = json.indexOf(':', keyIdx + key.length)
        val quote1 = json.indexOf('"', colon + 1)
        val quote2 = findStringEnd(json, quote1 + 1)
        return unescape(json.substring(quote1 + 1, quote2))
    }

    private fun findStringEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            if (s[i] == '\\') { i += 2; continue }
            if (s[i] == '"') return i
            i++
        }
        return s.length
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '"'  -> { sb.append('"');  i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n'  -> { sb.append('\n'); i += 2 }
                    'r'  -> { sb.append('\r'); i += 2 }
                    't'  -> { sb.append('\t'); i += 2 }
                    'u'  -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            sb.append(hex.toInt(16).toChar())
                            i += 6
                        } else { sb.append(c); i++ }
                    }
                    else -> { sb.append(s[i + 1]); i += 2 }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
