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

    fun deviceId(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var id = p.getString("device", "") ?: ""
        if (id.isBlank()) {
            id = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
            p.edit().putString("device", id).apply()
        }
        return id
    }

    fun clearToken(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY_TOKEN).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
