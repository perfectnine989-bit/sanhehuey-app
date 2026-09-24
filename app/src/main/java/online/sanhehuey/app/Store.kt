package online.sanhehuey.app

import android.content.Context

object Store {
    private const val PREF = "sanhehuey"
    private const val KEY_TOKEN = "token"

    fun token(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "") ?: ""

    fun saveToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
