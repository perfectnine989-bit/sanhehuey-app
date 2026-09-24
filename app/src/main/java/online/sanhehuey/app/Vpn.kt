package online.sanhehuey.app

import android.content.Context
import org.amnezia.awg.android.backend.GoBackend
import org.amnezia.awg.android.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

class AppTunnel(private val name: String) : Tunnel {
    var state: Tunnel.State = Tunnel.State.DOWN
    override fun getName(): String = name
    override fun onStateChange(newState: Tunnel.State) { state = newState }
}

object Vpn {
    private var backend: GoBackend? = null
    private val tunnel = AppTunnel("sanhehuey")

    private fun backend(ctx: Context): GoBackend {
        if (backend == null) backend = GoBackend(ctx.applicationContext)
        return backend!!
    }

    fun isUp(ctx: Context): Boolean =
        try {
            backend(ctx).getState(tunnel) == Tunnel.State.UP
        } catch (e: Exception) { false }

    fun connect(ctx: Context, configText: String) {
        val cfg = Config.parse(
            ByteArrayInputStream(configText.toByteArray())
        )
        backend(ctx).setState(tunnel, Tunnel.State.UP, cfg)
    }

    fun disconnect(ctx: Context) {
        backend(ctx).setState(tunnel, Tunnel.State.DOWN, null)
    }
}
