package online.sanhehuey.app

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.sanhehuey.app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var pendingConfig: String? = null
    private var state: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLogin.setOnClickListener { doLogin() }
        b.btnConnect.setOnClickListener { toggle() }

        b.loginHint.text = "Вход по аккаунту.\nКлючи подтянутся сами — ничего импортировать не нужно."
        if (Store.token(this).isBlank()) showLogin() else loadState()
    }

    private fun showLogin() {
        b.loginBox.visibility = android.view.View.VISIBLE
        b.mainBox.visibility = android.view.View.GONE
    }

    private fun showMain() {
        b.loginBox.visibility = android.view.View.GONE
        b.mainBox.visibility = android.view.View.VISIBLE
    }

    private fun doLogin() = lifecycleScope.launch {
        b.loginHint.text = "Открываем Telegram…"
        val res = withContext(Dispatchers.IO) {
            runCatching { Api.authStart() }.getOrNull()
        } ?: return@launch err("Нет связи с сервером")

        val code = res.optString("code")
        val url = res.optString("bot_url")
        if (code.isBlank()) return@launch err("Ошибка входа")

        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        b.loginHint.text = "Подтвердите вход в Telegram…"

        repeat(60) {
            delay(2000)
            val st = withContext(Dispatchers.IO) {
                runCatching { Api.authStatus(code) }.getOrNull()
            } ?: return@repeat

            when (st.optString("status")) {
                "ok" -> {
                    Store.saveToken(this@MainActivity, st.optString("token"))
                    loadState()
                    return@launch
                }
                "limit" -> return@launch err(st.optString("message"))
                "no_account" -> return@launch err(
                    "Аккаунт не найден. Оформите подписку на сайте."
                )
            }
        }
        b.loginHint.text = "Время истекло. Попробуйте снова."
    }

    private fun loadState() = lifecycleScope.launch {
        val t = Store.token(this@MainActivity)
        val s = withContext(Dispatchers.IO) {
            runCatching { Api.state(t) }.getOrNull()
        }
        if (s == null || !s.optBoolean("ok")) {
            Store.clear(this@MainActivity)
            return@launch showLogin()
        }
        state = s
        showMain()
        render()
    }

    private fun render() {
        val s = state ?: return
        val up = Vpn.isUp(this)

        b.statusText.text = if (up) "ПОД ЗАЩИТОЙ ТРИАДЫ" else "ТРИАДА СПИТ"
        b.statusText.setTextColor(getColor(if (up) R.color.jade else R.color.gold_dim))
        b.statusSub.text = if (up) "Соединение устойчиво" else "Соединение не установлено"

        b.btnConnect.setBackgroundResource(if (up) R.drawable.ring_on else R.drawable.ring_off)
        b.glyph.setTextColor(getColor(if (up) R.color.jade else R.color.gold_dim))
        b.btnLabel.text = if (up) "ОТКЛЮЧИТЬ" else "ПОДКЛЮЧИТЬ"
        b.btnLabel.setTextColor(getColor(if (up) R.color.jade else R.color.gold))
        b.dragonBg.alpha = if (up) 0.16f else 0.10f

        b.infoCard.removeAllViews()
        val days = s.optInt("days_left", 0)
        val exp = s.optString("expires_at")
        val access = if (days > 3000 || exp.startsWith("01.01.2099"))
            "Бессрочный" else exp
        row("АККАУНТ", s.optString("client_name"), true)
        row("ДОСТУП", access, false)
        row("УСТРОЙСТВО", s.optInt("slot").toString() + " из " + s.optInt("slots_total"), false)
    }

    private fun row(label: String, value: String, first: Boolean) {
        if (!first) {
            val sep = android.view.View(this)
            sep.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(11); it.bottomMargin = dp(11) }
            sep.setBackgroundColor(0x24C9A15A)
            b.infoCard.addView(sep)
        }
        val line = android.widget.LinearLayout(this)
        line.orientation = android.widget.LinearLayout.HORIZONTAL

        val l = android.widget.TextView(this)
        l.text = label
        l.textSize = 10f
        l.letterSpacing = 0.22f
        l.setTextColor(getColor(R.color.muted))
        l.typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.oswald)
        l.layoutParams = android.widget.LinearLayout.LayoutParams(0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val v = android.widget.TextView(this)
        v.text = value
        v.textSize = 13f
        v.setTextColor(getColor(R.color.gold))
        v.typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.oswald)

        line.addView(l)
        line.addView(v)
        b.infoCard.addView(line)
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun toggle() {
        if (Vpn.isUp(this)) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { Vpn.disconnect(this@MainActivity) }
                }
                render()
            }
            return
        }
        lifecycleScope.launch {
            b.btnConnect.text = "ПОДКЛЮЧАЕМ…"
            val t = Store.token(this@MainActivity)
            val c = withContext(Dispatchers.IO) {
                runCatching { Api.config(t) }.getOrNull()
            }
            if (c == null || !c.optBoolean("ok")) {
                render()
                return@launch err("Подписка неактивна")
            }
            pendingConfig = c.optString("config")
            val prep = VpnService.prepare(this@MainActivity)
            if (prep != null) startActivityForResult(prep, 1001)
            else startTunnel()
        }
    }

    private fun startTunnel() {
        val cfg = pendingConfig ?: return
        lifecycleScope.launch {
            b.btnConnect.text = "ПОДКЛЮЧАЕМ…"
            val res = withContext(Dispatchers.IO) {
                runCatching { Vpn.connect(this@MainActivity, cfg) }
            }
            res.onFailure {
                android.util.Log.e("SanHeHuey", "connect failed", it)
                err(
                    "Ошибка: " + (it::class.java.simpleName) +
                    " — " + (it.message ?: it.cause?.message ?: "нет деталей")
                )
            }
            render()
        }
    }

    override fun onActivityResult(rq: Int, rs: Int, data: Intent?) {
        super.onActivityResult(rq, rs, data)
        if (rq == 1001 && rs == RESULT_OK) startTunnel() else render()
    }

    private fun err(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        b.loginHint.text = msg
    }

    override fun onResume() {
        super.onResume()
        if (Store.token(this).isNotBlank()) render()
    }
}
