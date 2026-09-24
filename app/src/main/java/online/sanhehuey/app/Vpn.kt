package online.sanhehuey.app

import android.content.Context
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

class AppTunnel(private val tunnelName: String) : Tunnel {
    var state: Tunnel.State = Tunnel.State.DOWN

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        state = newState
    }

    override fun isIpv4ResolutionPreferred(): Boolean = true

    override fun isMetered(): Boolean = false
}

object Vpn {
    private var backend: GoBackend? = null
    private val tunnel = AppTunnel("sanhehuey")

    private fun backend(ctx: Context): GoBackend {
        if (backend == null) backend = GoBackend(ctx.applicationContext)
        return backend!!
    }

    fun isUp(ctx: Context): Boolean = tunnel.state == Tunnel.State.UP

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
