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

    /** Сети «мир минус Россия» построчно — для CDN-режима (маршруты VpnService). */
    fun cidrs(ctx: Context): List<String> =
        ctx.assets.open("smart_routes.txt").bufferedReader().use { it.readText() }
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Приложения, которые идут мимо туннеля (ExcludedApplications в секции [Interface]).
     * Если строка уже есть в конфиге — дополняем её, а не дублируем.
     */
    private fun excludeApps(config: String, apps: Set<String>): String {
        if (apps.isEmpty()) return config
        val lines = config.lines().toMutableList()
        val existing = lines.indexOfFirst { it.trim().startsWith("ExcludedApplications", ignoreCase = true) }
        if (existing >= 0) {
            val old = lines[existing].substringAfter("=").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            lines[existing] = "ExcludedApplications = " + (old + apps).distinct().joinToString(", ")
            return lines.joinToString("\n")
        }
        val iface = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (iface < 0) return config
        lines.add(iface + 1, "ExcludedApplications = " + apps.sorted().joinToString(", "))
        return lines.joinToString("\n")
    }

    fun apply(ctx: Context, config: String, mode: Mode): String {
        if (mode != Mode.Smart) return config
        val withApps = excludeApps(config, Store.directApps(ctx))
        return applyRoutes(ctx, withApps)
    }

    private fun applyRoutes(ctx: Context, config: String): String {
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
