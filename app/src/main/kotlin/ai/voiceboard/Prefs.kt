package ai.voiceboard

import android.content.Context

object Prefs {
    private const val FILE = "voiceboard_prefs"
    private const val KEY_API_KEY = "openai_api_key"
    private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    private const val KEY_MODEL   = "whisper_model"
    private const val KEY_AUTO_NEWLINE = "auto_newline"
    private const val DEFAULT_MODEL = "scribe_v2"

    /** OpenAI key — still used for Rephrase (gpt-5.5). */
    fun getApiKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(ctx: Context, key: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key.trim()).apply()

    /** ElevenLabs key — used for dictation (Scribe v2 speech-to-text). */
    fun getElevenLabsApiKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_ELEVENLABS_API_KEY, "") ?: ""

    fun setElevenLabsApiKey(ctx: Context, key: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_ELEVENLABS_API_KEY, key.trim()).apply()

    fun getModel(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun hasApiKey(ctx: Context): Boolean = getApiKey(ctx).isNotEmpty()

    fun hasElevenLabsApiKey(ctx: Context): Boolean = getElevenLabsApiKey(ctx).isNotEmpty()

    fun getAutoNewline(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_NEWLINE, false)

    fun setAutoNewline(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_NEWLINE, enabled).apply()
}
