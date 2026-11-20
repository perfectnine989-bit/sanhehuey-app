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

    fun mode(ctx: Context): Mode =
        if (ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("mode", "smart") == "global")
            Mode.Global else Mode.Smart

    fun saveMode(ctx: Context, mode: Mode) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("mode", if (mode == Mode.Global) "global" else "smart").apply()
    }

    /** Пакеты, которые в «Умном» режиме идут напрямую, мимо туннеля. */
    fun directApps(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet("direct_apps", emptySet())?.toSet() ?: emptySet()

    fun saveDirectApps(ctx: Context, apps: Set<String>) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putStringSet("direct_apps", apps.toSet()).apply()
    }

    /** Ключ, выбранный на этом устройстве (синхронизируется с сервером). */
    fun keyId(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("key_id", "") ?: ""

    fun saveKeyId(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("key_id", id).apply()
    }

    /** Лучшие адреса Cloudflare с прошлого подключения — проверяются первыми. */
    fun cdnNodes(ctx: Context): List<String> =
        (ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("cdn_nodes", "") ?: "")
            .split(",").filter { it.isNotBlank() }

    fun saveCdnNodes(ctx: Context, nodes: List<String>) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("cdn_nodes", nodes.joinToString(",")).apply()
    }

    /** Последний ответ сервера для сборки «команда» — чтобы подключаться, даже если API недоступен. */
    fun teamCdn(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("team_cdn", "") ?: ""

    fun saveTeamCdn(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("team_cdn", json).apply()
    }

    fun clearToken(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY_TOKEN).remove("key_id").apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
