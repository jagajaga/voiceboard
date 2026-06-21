package ai.voiceboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

object WhisperApi {

    private val client = OkHttpClient()

    /**
     * Transcribe [audioFile] (M4A/AAC) using the OpenAI Whisper-compatible endpoint.
     * Returns the transcribed text, or throws on failure.
     */
    @Throws(IOException::class)
    fun transcribe(audioFile: File, apiKey: String, model: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType())
            )
            .addFormDataPart("model", model)
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from Whisper API")

            if (!response.isSuccessful) {
                throw IOException("Whisper API error ${response.code}: $responseBody")
            }

            // Response: {"text":"..."}  — parse without a JSON library
            return extractJsonText(responseBody)
        }
    }

    /** Minimal JSON extraction for {"text":"..."} */
    private fun extractJsonText(json: String): String {
        val key = "\"text\""
        val keyIdx = json.indexOf(key)
        if (keyIdx < 0) throw IOException("No 'text' field in response: $json")
        val colon = json.indexOf(':', keyIdx + key.length)
        val quote1 = json.indexOf('"', colon + 1)
        val quote2 = json.indexOf('"', quote1 + 1)
        return json.substring(quote1 + 1, quote2)
    }
}
