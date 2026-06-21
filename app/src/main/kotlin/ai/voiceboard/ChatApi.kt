package ai.voiceboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object ChatApi {

    private val client = OkHttpClient()

    /**
     * Rephrase [originalText] according to [instruction] using GPT-4o-mini.
     * Returns only the rephrased text, no extra explanation.
     */
    @Throws(IOException::class)
    fun rephrase(originalText: String, instruction: String, apiKey: String): String {
        val json = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                {
                  "role": "system",
                  "content": "You are a text editor. Rephrase the text the user provides according to their instructions. Return ONLY the rephrased text — no quotes, no explanation, nothing else."
                },
                {
                  "role": "user",
                  "content": "Original text:\n${originalText.replace("\"", "\\\"")}\n\nInstruction: ${instruction.replace("\"", "\\\"")}"
                }
              ],
              "temperature": 0.7
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IOException("Empty response from ChatGPT")
            if (!response.isSuccessful)
                throw IOException("ChatGPT error ${response.code}: $body")
            return extractContent(body)
        }
    }

    /** Minimal extraction of choices[0].message.content from ChatGPT JSON */
    private fun extractContent(json: String): String {
        val marker = "\"content\":"
        val idx = json.indexOf(marker)
        if (idx < 0) throw IOException("No content field in response: $json")
        val afterColon = json.substring(idx + marker.length).trimStart()
        if (afterColon.startsWith("null")) throw IOException("Null content in response")
        val q1 = afterColon.indexOf('"')
        // walk forward respecting escape sequences
        var i = q1 + 1
        val sb = StringBuilder()
        while (i < afterColon.length) {
            val c = afterColon[i]
            if (c == '\\' && i + 1 < afterColon.length) {
                when (afterColon[i + 1]) {
                    '"'  -> { sb.append('"');  i += 2 }
                    'n'  -> { sb.append('\n'); i += 2 }
                    'r'  -> { sb.append('\r'); i += 2 }
                    't'  -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(afterColon[i + 1]); i += 2 }
                }
            } else if (c == '"') {
                break
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString().trim()
    }
}
