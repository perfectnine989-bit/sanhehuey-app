package online.sanhehuey.app

import android.content.Context

/**
 * «Умный» режим для AmneziaWG: в туннель идёт всё, кроме российских сетей.
 * Список в assets/smart_routes.txt — это мир минус Россия (по адресам).
 */
object Routes {
    private var cached: String? = null

    private fun list(ctx: Context): String {
        cached?.let { return it }
        val text = ctx.assets.open("smart_routes.txt").bufferedReader().use { it.readText() }
        val joined = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        cached = joined
        return joined
    }

    fun apply(ctx: Context, config: String, mode: Mode): String {
        if (mode != Mode.Smart) return config
        val routes = runCatching { list(ctx) }.getOrNull()
        if (routes.isNullOrBlank()) return config
        var replaced = false
        val lines = config.lines().map { line ->
            if (line.trim().startsWith("AllowedIPs", ignoreCase = true)) {
                replaced = true
                val v6 = if (line.contains("::/0")) ", ::/0" else ""
                "AllowedIPs = $routes$v6"
            } else {
                line
            }
        }
        return if (replaced) lines.joinToString("\n") else config
    }
}
