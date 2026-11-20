package online.sanhehuey.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Api {
    // В сборке для команды адрес сервера зашит при сборке (домен), чтобы при бане менять только DNS
    private val BASE: String =
        if (BuildConfig.TEAM && BuildConfig.TEAM_API.isNotBlank()) BuildConfig.TEAM_API.trimEnd('/')
        else "https://sanhehuey.online"

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

    /** Сборка для команды: без входа, регистрация устройства по общему ключу приложения. */
    fun teamConnect(secret: String, device: String, name: String): JSONObject {
        val json = JSONObject().put("secret", secret).put("device", device).put("name", name)
        val req = Request.Builder()
            .url(BASE + "/api/team/connect")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return if (t.isBlank()) JSONObject() else JSONObject(t)
        }
    }

    fun authStart(): JSONObject = post("/api/app/auth/start")

    fun authStatus(code: String, device: String): JSONObject =
        get("/api/app/auth/status/$code?device=$device")

    fun logout(token: String): JSONObject {
        val body = ("{\"token\":\"" + token + "\"}")
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(BASE + "/api/app/logout").post(body).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return if (t.isBlank()) JSONObject() else JSONObject(t)
        }
    }

    fun state(token: String): JSONObject =
        get("/api/app/state?token=$token")

    fun renew(token: String): JSONObject {
        val body = ("{\"token\":\"" + token + "\",\"plan\":\"1m\"}")
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(BASE + "/api/app/renew").post(body).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return if (t.isBlank()) JSONObject() else JSONObject(t)
        }
    }

    /** keyId пустой — старое поведение сервера (ключ по умолчанию). */
    fun config(token: String, device: String = "", keyId: String = ""): JSONObject {
        var path = "/api/app/config?token=$token"
        if (device.isNotBlank()) path += "&device=" + enc(device)
        if (keyId.isNotBlank()) path += "&key_id=" + enc(keyId)
        return get(path)
    }

    /**
     * Ключи аккаунта и то, кем они заняты:
     * {ok, selected, keys:[{id, name, in_use, mine, by}]}
     */
    fun keys(token: String, device: String): JSONObject =
        get("/api/app/keys?token=$token&device=" + enc(device))

    /**
     * Закрепить ключ за этим устройством. Если ключ занят другим устройством,
     * сервер отвечает {ok:false, busy:true, by:"..."}; с force=true ключ забирается.
     */
    fun selectKey(token: String, device: String, deviceName: String, keyId: String, force: Boolean): JSONObject {
        val json = JSONObject()
            .put("token", token)
            .put("device", device)
            .put("device_name", deviceName)
            .put("key_id", keyId)
            .put("force", force)
        val req = Request.Builder()
            .url(BASE + "/api/app/key/select")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            return if (t.isBlank()) JSONObject() else JSONObject(t)
        }
    }

    private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")
}
