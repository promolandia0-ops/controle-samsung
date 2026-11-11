package br.geracaoai.controlesamsung.protocol

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Tipo de lançamento de app no WebSocket (`ed.apps.launch`). */
enum class LaunchType(val wire: String) { DEEP_LINK("DEEP_LINK"), NATIVE_LAUNCH("NATIVE_LAUNCH") }

data class InstalledApp(val appId: String, val name: String, val type: Int?)

data class DeviceInfo(
    val name: String?,
    val model: String?,
    val wifiMac: String?,
    val powerState: String?,
    val isSamsung: Boolean,
)

/** Eventos recebidos da TV, já interpretados. */
sealed class TvEvent {
    data class Connected(val token: String?) : TvEvent()
    object Unauthorized : TvEvent()
    object ChannelTimeout : TvEvent()
    data class ImeStart(val currentText: String?) : TvEvent()
    data class ImeUpdate(val text: String?) : TvEvent()
    object ImeEnd : TvEvent()
    data class Apps(val apps: List<InstalledApp>) : TvEvent()
    data class Error(val message: String) : TvEvent()
    data class Other(val event: String) : TvEvent()
}

/**
 * Monta e interpreta as mensagens do protocolo remoto WebSocket das Samsung Smart TV (Tizen, 2016+).
 * Código próprio, escrito contra o protocolo público/conhecido. Sem dependência de Android → testável na JVM.
 */
object SamsungProtocol {
    const val PORT_SECURE = 8002
    const val PORT_PLAIN = 8001
    private const val PATH = "/api/v2/channels/samsung.remote.control"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun b64Decode(text: String): String? = try {
        String(Base64.getDecoder().decode(text.trim()), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** URL do WebSocket. O nome do cliente vai em Base64; o token (se houver) vai em `token=`. */
    fun wsUrl(host: String, secure: Boolean, appName: String, token: String?): String {
        val scheme = if (secure) "wss" else "ws"
        val port = if (secure) PORT_SECURE else PORT_PLAIN
        val tokenPart = if (!token.isNullOrBlank()) "&token=" + java.net.URLEncoder.encode(token, "UTF-8") else ""
        val name = java.net.URLEncoder.encode(b64(appName), "UTF-8")
        return "$scheme://$host:$port$PATH?name=$name$tokenPart"
    }

    fun infoUrl(host: String) = "http://$host:$PORT_PLAIN/api/v2/"
    fun appUrl(host: String, appId: String) = "http://$host:$PORT_PLAIN/api/v2/applications/" + java.net.URLEncoder.encode(appId, "UTF-8").replace("+", "%20")

    /** Cmd: "Click" (toque), "Press" (segurar) ou "Release" (soltar). */
    fun key(code: String, cmd: String = "Click"): String = buildJsonObject {
        put("method", "ms.remote.control")
        putJsonObject("params") {
            put("Cmd", cmd)
            put("DataOfCmd", code)
            put("Option", "false")
            put("TypeOfRemote", "SendRemoteKey")
        }
    }.toString()

    /** Substitui o texto do campo da TV pelo texto informado (Base64). */
    fun inputString(text: String): String = buildJsonObject {
        put("method", "ms.remote.control")
        putJsonObject("params") {
            put("Cmd", b64(text))
            put("DataOfCmd", "base64")
            put("TypeOfRemote", "SendInputString")
        }
    }.toString()

    fun inputEnd(): String = buildJsonObject {
        put("method", "ms.remote.control")
        putJsonObject("params") { put("TypeOfRemote", "SendInputEnd") }
    }.toString()

    fun launchApp(appId: String, type: LaunchType = LaunchType.DEEP_LINK, metaTag: String = ""): String = buildJsonObject {
        put("method", "ms.channel.emit")
        putJsonObject("params") {
            put("event", "ed.apps.launch")
            put("to", "host")
            putJsonObject("data") {
                put("action_type", type.wire)
                put("appId", appId)
                put("metaTag", metaTag)
            }
        }
    }.toString()

    fun installedApps(): String = buildJsonObject {
        put("method", "ms.channel.emit")
        putJsonObject("params") {
            put("event", "ed.installedApp.get")
            put("to", "host")
        }
    }.toString()

    // ---------------------------------------------------------------- Wake-on-LAN

    /** Normaliza MAC para 6 bytes; null se inválido. Aceita ":" "-" ou sem separador. */
    fun parseMac(mac: String?): ByteArray? {
        if (mac == null) return null
        val hex = mac.trim().replace(":", "").replace("-", "")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Pacote mágico: 6×0xFF + 16×MAC (102 bytes). */
    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6) { "MAC deve ter 6 bytes" }
        val out = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) out[i] = 0xFF.toByte()
        for (r in 0 until 16) System.arraycopy(mac, 0, out, 6 + r * 6, 6)
        return out
    }

    // ---------------------------------------------------------------- Interpretação

    fun parseDeviceInfo(body: String): DeviceInfo? = try {
        val root = json.parseToJsonElement(body).jsonObject
        val dev = root["device"] as? JsonObject
        val name = dev?.str("name") ?: root.str("name")
        val model = dev?.str("modelName") ?: root.str("name")
        val mac = dev?.str("wifiMac")
        val power = dev?.str("PowerState")
        // /api/v2/ na porta 8001 só existe em TVs Samsung Tizen: ter o objeto "device" já basta.
        val isSamsung = dev != null
        DeviceInfo(name, model, mac, power, isSamsung)
    } catch (_: Exception) {
        null
    }

    fun parseEvent(message: String): TvEvent? {
        val root = try { json.parseToJsonElement(message).jsonObject } catch (_: Exception) { return null }
        val event = root.str("event") ?: return null
        val data = root["data"]
        return when (event) {
            "ms.channel.connect" -> TvEvent.Connected((data as? JsonObject)?.str("token"))
            "ms.channel.unauthorized" -> TvEvent.Unauthorized
            "ms.channel.timeout" -> TvEvent.ChannelTimeout
            "ms.remote.imeStart" -> TvEvent.ImeStart(decodeImeText(data))
            "ms.remote.imeUpdate" -> TvEvent.ImeUpdate(decodeImeText(data))
            "ms.remote.imeEnd", "ms.remote.imeDone" -> TvEvent.ImeEnd
            "ed.installedApp.get" -> TvEvent.Apps(parseInstalledApps(data))
            "ms.error" -> TvEvent.Error((data as? JsonObject)?.str("message") ?: (data as? JsonPrimitive)?.contentOrNull ?: "erro")
            else -> TvEvent.Other(event)
        }
    }

    /** O texto do campo vem em Base64 (às vezes dentro de um objeto). Retorna null se não for texto legível. */
    fun decodeImeText(data: JsonElement?): String? {
        val raw = when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> data.str("text") ?: data.str("data")
            else -> null
        } ?: return null
        if (raw.isEmpty()) return ""
        val decoded = b64Decode(raw) ?: return null
        // Descarta binário / JSON estranho: só aceita texto imprimível.
        if (decoded.any { it.isISOControl() && it != '\n' }) return null
        return decoded
    }

    fun parseInstalledApps(data: JsonElement?): List<InstalledApp> {
        val arr: JsonArray = when (data) {
            is JsonObject -> (data["data"] as? JsonArray) ?: return emptyList()
            is JsonArray -> data
            else -> return emptyList()
        }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("appId") ?: return@mapNotNull null
            InstalledApp(id, o.str("name") ?: id, o.str("app_type")?.toIntOrNull())
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
