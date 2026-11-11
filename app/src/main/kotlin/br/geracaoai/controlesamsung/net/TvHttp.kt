package br.geracaoai.controlesamsung.net

import br.geracaoai.controlesamsung.protocol.DeviceInfo
import br.geracaoai.controlesamsung.protocol.SamsungProtocol
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Chamadas REST simples (porta 8001, rede local, texto puro). */
object TvHttp {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun info(host: String): DeviceInfo? = withContext(Dispatchers.IO) {
        if (!LocalNet.isPrivateHost(host)) return@withContext null
        try {
            client.newCall(Request.Builder().url(SamsungProtocol.infoUrl(host)).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()?.let { SamsungProtocol.parseDeviceInfo(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** true = rodando, false = não está rodando, null = TV não responde/não suporta. */
    suspend fun appRunning(host: String, appId: String): Boolean? = withContext(Dispatchers.IO) {
        if (!LocalNet.isPrivateHost(host)) return@withContext null
        try {
            client.newCall(Request.Builder().url(SamsungProtocol.appUrl(host, appId)).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body?.string() ?: return@use null
                val running = (Json.parseToJsonElement(body) as? JsonObject)?.get("running") as? JsonPrimitive
                running?.content?.let { it.equals("true", true) }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun launchRest(host: String, appId: String): Boolean = withContext(Dispatchers.IO) {
        if (!LocalNet.isPrivateHost(host)) return@withContext false
        try {
            val req = Request.Builder().url(SamsungProtocol.appUrl(host, appId)).post("".toRequestBody()).build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
