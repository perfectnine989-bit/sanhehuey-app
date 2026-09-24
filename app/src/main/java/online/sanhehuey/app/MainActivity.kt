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
        b.burger.setOnClickListener { openMenu(true) }
        b.scrim.setOnClickListener { openMenu(false) }
        b.navVpn.setOnClickListener { section(0) }
        b.navCab.setOnClickListener { section(1) }
        b.navSet.setOnClickListener { section(2) }
        b.btnRenewBal.setOnClickListener { renewFromBalance() }
        b.btnTopupWeb.setOnClickListener { openWeb("topup_url") }
        b.btnSupport.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/S1llonGOD")))
            }
        }
        b.btnLogout.setOnClickListener { doLogout() }
        b.verText.text = "Версия " + packageManager
            .getPackageInfo(packageName, 0).versionName

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
                runCatching { Api.authStatus(code, Store.deviceId(this@MainActivity)) }.getOrNull()
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
        section(0)
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

        val loc = s.optString("location")
        b.locText.text = if (loc.isBlank()) "" else loc
        b.locText.visibility =
            if (loc.isNotBlank()) android.view.View.VISIBLE
            else android.view.View.INVISIBLE
        b.locText.setTextColor(getColor(if (up) R.color.gold else R.color.muted))

        breathe(up)

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

    private var breathAnim: android.animation.AnimatorSet? = null

    private fun breathe(on: Boolean) {
        breathAnim?.cancel()
        breathAnim = null
        if (!on) {
            b.btnConnect.scaleX = 1f
            b.btnConnect.scaleY = 1f
            b.dragonBg.alpha = 0.10f
            return
        }
        val sx = android.animation.ObjectAnimator.ofFloat(
            b.btnConnect, "scaleX", 1f, 1.035f, 1f)
        val sy = android.animation.ObjectAnimator.ofFloat(
            b.btnConnect, "scaleY", 1f, 1.035f, 1f)
        val al = android.animation.ObjectAnimator.ofFloat(
            b.dragonBg, "alpha", 0.13f, 0.21f, 0.13f)
        listOf(sx, sy, al).forEach {
            it.duration = 3400
            it.repeatCount = android.animation.ValueAnimator.INFINITE
            it.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        breathAnim = android.animation.AnimatorSet().apply {
            playTogether(sx, sy, al)
            start()
        }
    }

    private fun openWeb(key: String) {
        val url = state?.optString(key) ?: ""
        val target = if (url.isBlank()) "https://sanhehuey.online" else url
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }.onFailure { err("Не удалось открыть страницу") }
    }

    private var tab = 0

    private fun openMenu(show: Boolean) {
        b.sidebar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        b.scrim.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        if (show) {
            b.sidebar.translationX = -264f * resources.displayMetrics.density
            b.sidebar.animate().translationX(0f).setDuration(220).start()
        }
    }

    private fun section(i: Int) {
        tab = i
        openMenu(false)
        b.mainBox.visibility = if (i == 0) android.view.View.VISIBLE else android.view.View.GONE
        b.cabinetBox.visibility = if (i == 1) android.view.View.VISIBLE else android.view.View.GONE
        b.settingsBox.visibility = if (i == 2) android.view.View.VISIBLE else android.view.View.GONE
        b.dragonBg.visibility = if (i == 0) android.view.View.VISIBLE else android.view.View.GONE
        b.navVpn.isSelected = i == 0
        b.navCab.isSelected = i == 1
        b.navSet.isSelected = i == 2
        if (i == 1) renderCabinet()
    }

    private fun renderCabinet() {
        val s = state ?: return
        b.cabCard.removeAllViews()
        val days = s.optInt("days_left", 0)
        val exp = s.optString("expires_at")
        val lifetime = days > 3000 || exp.startsWith("01.01.2099")
        cabRow("БАЛАНС", s.optInt("balance").toString() + " ₽", true)
        cabRow("ПОДПИСКА", if (lifetime) "Бессрочная" else exp, false)
        if (!lifetime && days > 0) cabRow("ОСТАЛОСЬ", days.toString() + " дн.", false)
        cabRow("УСТРОЙСТВА",
            s.optInt("slots_used").toString() + " из " + s.optInt("slots_total"), false)
        cabRow("ПРОДЛЕНИЕ", s.optInt("renew_price").toString() + " ₽", false)

        val lvl = s.optJSONObject("loyalty")
        val tier = lvl?.optString("tier") ?: ""
        if (tier.isNotBlank()) cabRow("СТАТУС", tier, false)

        b.cabHint.text =
            "Баланс общий с сайтом и ботом — пополнил там, здесь обновится сразу."
    }

    private fun cabRow(label: String, value: String, first: Boolean) {
        if (!first) {
            val sep = android.view.View(this)
            sep.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(12); it.bottomMargin = dp(12) }
            sep.setBackgroundColor(0x24C9A15A)
            b.cabCard.addView(sep)
        }
        val line = android.widget.LinearLayout(this)
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
        v.textSize = 14f
        v.setTextColor(getColor(R.color.gold))
        v.typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.russo)
        line.addView(l); line.addView(v)
        b.cabCard.addView(line)
    }

    private fun renewFromBalance() = lifecycleScope.launch {
        b.btnRenewBal.text = "СПИСЫВАЕМ…"
        val t = Store.token(this@MainActivity)
        val r = withContext(Dispatchers.IO) {
            runCatching { Api.renew(t) }.getOrNull()
        }
        b.btnRenewBal.text = "ПРОДЛИТЬ С БАЛАНСА"
        if (r == null) { err("Нет связи с сервером"); return@launch }
        if (r.optBoolean("ok")) {
            Toast.makeText(this@MainActivity,
                "Продлено до " + r.optString("expires_at"), Toast.LENGTH_LONG).show()
            loadState()
            section(1)
        } else {
            err(r.optString("message").ifBlank { "Не удалось продлить" })
        }
    }

    private fun doLogout() = lifecycleScope.launch {
        val t = Store.token(this@MainActivity)
        if (Vpn.isUp(this@MainActivity)) {
            withContext(Dispatchers.IO) {
                runCatching { Vpn.disconnect(this@MainActivity) }
            }
        }
        withContext(Dispatchers.IO) { runCatching { Api.logout(t) } }
        Store.clearToken(this@MainActivity)
        state = null
        openMenu(false)
        section(0)
        showLogin()
        Toast.makeText(this@MainActivity,
            "Вы вышли, слот устройства освобождён", Toast.LENGTH_LONG).show()
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
            b.btnLabel.text = "ПОДКЛЮЧАЕМ…"
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
            b.btnLabel.text = "ПОДКЛЮЧАЕМ…"
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

    override fun onPause() {
        super.onPause()
        breathAnim?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (Store.token(this).isNotBlank()) render()
    }
}
