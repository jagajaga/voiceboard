package ai.voiceboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object ChatApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Rephrase [originalText] according to [instruction] using gpt-5.5.
     * Returns only the rephrased text, no extra explanation.
     */
    @Throws(IOException::class)
    fun rephrase(originalText: String, instruction: String, apiKey: String): String {
        val system = "You are a text editor. Rephrase the text the user provides according to their instructions. Return ONLY the rephrased text — no quotes, no explanation, nothing else."
        val user   = "Original text:\n${originalText}\n\nInstruction: ${instruction}"
        return chatCompletion(system, user, apiKey)
    }

    /**
     * Clean up a raw speech-to-text dictation transcript so it reads like text the speaker
     * typed themselves, in whatever language it is. Fixed system prompt (not user-editable) so
     * dictation cleanup can't be hijacked by spoken "instructions" — unlike [rephrase], which is
     * deliberately instruction-driven.
     */
    @Throws(IOException::class)
    fun cleanDictation(rawText: String, apiKey: String): String {
        val system = """
            You clean up raw speech-to-text dictation so it reads like text the speaker typed
            themselves — in whatever language it's in. Never translate or switch language, no
            matter what language appears.

            Rules:
            - Remove filler words and disfluencies (um, uh, er, like, you know, I mean, kind of,
              "so yeah" — and their equivalents in any language).
            - Resolve false starts and self-corrections: keep only the final, intended version of
              each thought; drop the abandoned fragment entirely.
            - Collapse stutter-repeated words or phrases into one.
            - Fix punctuation, capitalization, and sentence breaks so it reads naturally.
            - Preserve every fact, name, number, and opinion exactly as spoken. Never add, remove,
              reinterpret, or summarize content. Keep the speaker's own wording — this is light
              cleanup, not a rewrite.
            - Output ONLY the cleaned text. No quotes, no notes, no explanation.
        """.trimIndent()
        return chatCompletion(system, rawText, apiKey)
    }

    /** Shared chat-completions call: system + user message in, raw content string out. */
    @Throws(IOException::class)
    private fun chatCompletion(system: String, user: String, apiKey: String): String {
        val json = buildJsonRequest("gpt-5.5", system, user)

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

    // ── JSON helpers ───────────────────────────────────────────────────────────

    /** Build a minimal chat-completions JSON payload with proper escaping. */
    private fun buildJsonRequest(model: String, system: String, user: String): String {
        return """{"model":${js(model)},"messages":[{"role":"system","content":${js(system)}},{"role":"user","content":${js(user)}}]}"""
    }

    /** JSON-encode a string value (including surrounding quotes). */
    private fun js(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"'       -> sb.append("\\\"")
                '\\'      -> sb.append("\\\\")
                '\n'      -> sb.append("\\n")
                '\r'      -> sb.append("\\r")
                '\t'      -> sb.append("\\t")
                '\b'      -> sb.append("\\b")
                '\u000C'  -> sb.append("\\f")
                else      -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    /** Minimal extraction of choices[0].message.content from ChatGPT JSON. */
    private fun extractContent(json: String): String {
        val marker = "\"content\":"
        val idx = json.indexOf(marker)
        if (idx < 0) throw IOException("No content field in response: $json")
        val afterColon = json.substring(idx + marker.length).trimStart()
        if (afterColon.startsWith("null")) throw IOException("Null content in response")
        val q1 = afterColon.indexOf('"')
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