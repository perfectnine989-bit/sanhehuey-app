package online.sanhehuey.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Api {
    private const val BASE = "https://sanhehuey.online"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(path: String): JSONObject {
        val req = Request.Builder().url(BASE + path).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun post(path: String): JSONObject {
        val req = Request.Builder()
            .url(BASE + path)
            .post("".toRequestBody())
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    fun authStart(): JSONObject = post("/api/app/auth/start")

    fun authStatus(code: String): JSONObject =
        get("/api/app/auth/status/$code")

    fun state(token: String): JSONObject =
        get("/api/app/state?token=$token")

    fun config(token: String): JSONObject =
        get("/api/app/config?token=$token")
}
