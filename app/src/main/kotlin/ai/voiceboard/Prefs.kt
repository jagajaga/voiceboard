package ai.voiceboard

import android.content.Context

object Prefs {
    private const val FILE = "voiceboard_prefs"
    private const val KEY_API_KEY = "openai_api_key"
    private const val KEY_MODEL   = "whisper_model"
    private const val DEFAULT_MODEL = "gpt-4o-transcribe"

    fun getApiKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(ctx: Context, key: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key.trim()).apply()

    fun getModel(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun hasApiKey(ctx: Context): Boolean = getApiKey(ctx).isNotEmpty()
}
