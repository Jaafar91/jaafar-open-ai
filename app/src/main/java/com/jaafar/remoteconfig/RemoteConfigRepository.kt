package com.jaafar.remoteconfig

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class RemoteConfig(val message: String, val enabled: Boolean)

class RemoteConfigRepository(private val baseUrl: String) {
    private val client = OkHttpClient()

    fun fetch(callback: (Result<RemoteConfig>) -> Unit) {
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/config").get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) return callback(Result.failure(IOException("HTTP ${it.code}")))
                    val body = it.body
                        ?: return callback(Result.failure(IOException("Empty response body")))
                    callback(runCatching {
                        val json = JSONObject(body.string())
                        RemoteConfig(
                            json.optString("message", "Welcome"),
                            json.optBoolean("enabled", true),
                        )
                    })
                }
            }
        })
    }
}
