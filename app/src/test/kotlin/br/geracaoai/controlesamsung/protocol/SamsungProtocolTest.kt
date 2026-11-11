package br.geracaoai.controlesamsung.protocol

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungProtocolTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun JsonObject.p(k: String) = (this[k] as JsonObject)
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun wsUrlSecureHasPortNameAndToken() {
        val url = SamsungProtocol.wsUrl("192.168.0.25", true, "Controle Samsung", "12345678")
        assertTrue(url.startsWith("wss://192.168.0.25:8002/api/v2/channels/samsung.remote.control?name="))
        // "Controle Samsung" em Base64 = Q29udHJvbGUgU2Ftc3VuZw==  (url-encoded: ==  -> %3D%3D)
        assertTrue(url, url.contains("name=Q29udHJvbGUgU2Ftc3VuZw%3D%3D"))
        assertTrue(url.endsWith("&token=12345678"))
    }

    @Test fun wsUrlPlainHasNoTokenWhenMissing() {
        val url = SamsungProtocol.wsUrl("10.0.0.5", false, "X", null)
        assertTrue(url.startsWith("ws://10.0.0.5:8001/api/v2/channels/samsung.remote.control?name="))
        assertTrue(!url.contains("token"))
        assertTrue(!SamsungProtocol.wsUrl("10.0.0.5", true, "X", "").contains("token"))
    }

    @Test fun keyClickMessage() {
        val m = obj(SamsungProtocol.key(Keys.VOL_UP))
        assertEquals("ms.remote.control", m.s("method"))
        val p = m.p("params")
        assertEquals("Click", p.s("Cmd"))
        assertEquals("KEY_VOLUP", p.s("DataOfCmd"))
        assertEquals("false", p.s("Option"))
        assertEquals("SendRemoteKey", p.s("TypeOfRemote"))
    }

    @Test fun keyPressAndRelease() {
        assertEquals("Press", obj(SamsungProtocol.key(Keys.UP, "Press")).p("params").s("Cmd"))
        assertEquals("Release", obj(SamsungProtocol.key(Keys.UP, "Release")).p("params").s("Cmd"))
    }

    @Test fun digitKeys() {
        assertEquals("KEY_0", Keys.digit(0)); assertEquals("KEY_9", Keys.digit(9))
        try { Keys.digit(10); throw AssertionError("deveria falhar") } catch (_: IllegalArgumentException) {}
    }

    @Test fun inputStringIsBase64AndUtf8() {
        val text = "Pão de Açúcar ção 日本"
        val p = obj(SamsungProtocol.inputString(text)).p("params")
        assertEquals("SendInputString", p.s("TypeOfRemote"))
        assertEquals("base64", p.s("DataOfCmd"))
        assertEquals(text, String(Base64.getDecoder().decode(p.s("Cmd")), Charsets.UTF_8))
    }

    @Test fun inputStringEscapesQuotesInJson() {
        val text = "a \"b\" \\ c"
        val raw = SamsungProtocol.inputString(text)
        val p = obj(raw).p("params")
        assertEquals(text, SamsungProtocol.b64Decode(p.s("Cmd")))
    }

    @Test fun inputStringEmptyClearsField() {
        val p = obj(SamsungProtocol.inputString("")).p("params")
        assertEquals("", p.s("Cmd"))
    }

    @Test fun inputEnd() {
        val m = obj(SamsungProtocol.inputEnd())
        assertEquals("ms.remote.control", m.s("method"))
        assertEquals("SendInputEnd", m.p("params").s("TypeOfRemote"))
    }

    @Test fun launchAppDeepLink() {
        val m = obj(SamsungProtocol.launchApp("3201901017640"))
        assertEquals("ms.channel.emit", m.s("method"))
        val p = m.p("params")
        assertEquals("ed.apps.launch", p.s("event"))
        assertEquals("host", p.s("to"))
        val d = p.p("data")
        assertEquals("DEEP_LINK", d.s("action_type"))
        assertEquals("3201901017640", d.s("appId"))
        assertEquals("", d.s("metaTag"))
    }

    @Test fun launchAppNative() {
        val d = obj(SamsungProtocol.launchApp("org.tizen.browser", LaunchType.NATIVE_LAUNCH)).p("params").p("data")
        assertEquals("NATIVE_LAUNCH", d.s("action_type"))
    }

    @Test fun installedAppsRequest() {
        val p = obj(SamsungProtocol.installedApps()).p("params")
        assertEquals("ed.installedApp.get", p.s("event"))
        assertEquals("host", p.s("to"))
    }

    @Test fun restUrls() {
        assertEquals("http://192.168.0.2:8001/api/v2/", SamsungProtocol.infoUrl("192.168.0.2"))
        assertEquals("http://192.168.0.2:8001/api/v2/applications/111299001912", SamsungProtocol.appUrl("192.168.0.2", "111299001912"))
        assertEquals("http://1.2.3.4:8001/api/v2/applications/org.tizen.browser", SamsungProtocol.appUrl("1.2.3.4", "org.tizen.browser"))
    }

    // ------------------------------------------------------------- Wake-on-LAN
    @Test fun macParsing() {
        assertArrayEquals(byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0xEF.toByte()), SamsungProtocol.parseMac("00:1a:2B:3c:4D:eF"))
        assertArrayEquals(byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0xEF.toByte()), SamsungProtocol.parseMac("00-1A-2B-3C-4D-EF"))
        assertArrayEquals(byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0xEF.toByte()), SamsungProtocol.parseMac("001A2B3C4DEF"))
        assertNull(SamsungProtocol.parseMac(null))
        assertNull(SamsungProtocol.parseMac("00:1A:2B"))
        assertNull(SamsungProtocol.parseMac("ZZ:1A:2B:3C:4D:EF"))
    }

    @Test fun magicPacketLayout() {
        val mac = SamsungProtocol.parseMac("AA:BB:CC:DD:EE:FF")!!
        val pkt = SamsungProtocol.magicPacket(mac)
        assertEquals(102, pkt.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), pkt[i])
        for (r in 0 until 16) for (b in 0 until 6) assertEquals(mac[b], pkt[6 + r * 6 + b])
    }

    // ------------------------------------------------------------- Interpretação
    @Test fun parseConnectEventWithToken() {
        val ev = SamsungProtocol.parseEvent("""{"data":{"clients":[{"attributes":{"name":"Q29udHJvbGU="},"connectTime":1,"deviceName":"Q29udHJvbGU=","id":"abc","isHost":false}],"id":"abc","token":"98765432"},"event":"ms.channel.connect"}""")
        assertEquals(TvEvent.Connected("98765432"), ev)
    }

    @Test fun parseConnectEventWithoutToken() {
        assertEquals(TvEvent.Connected(null), SamsungProtocol.parseEvent("""{"event":"ms.channel.connect","data":{"id":"x"}}"""))
    }

    @Test fun parseUnauthorizedAndTimeout() {
        assertEquals(TvEvent.Unauthorized, SamsungProtocol.parseEvent("""{"event":"ms.channel.unauthorized"}"""))
        assertEquals(TvEvent.ChannelTimeout, SamsungProtocol.parseEvent("""{"event":"ms.channel.timeout"}"""))
    }

    @Test fun parseImeEvents() {
        val start = SamsungProtocol.parseEvent("""{"event":"ms.remote.imeStart","data":"${SamsungProtocol.b64("olá")}"}""")
        assertEquals(TvEvent.ImeStart("olá"), start)
        val upd = SamsungProtocol.parseEvent("""{"event":"ms.remote.imeUpdate","data":"${SamsungProtocol.b64("abc")}"}""")
        assertEquals(TvEvent.ImeUpdate("abc"), upd)
        assertEquals(TvEvent.ImeEnd, SamsungProtocol.parseEvent("""{"event":"ms.remote.imeEnd","data":""}"""))
        assertEquals(TvEvent.ImeEnd, SamsungProtocol.parseEvent("""{"event":"ms.remote.imeDone"}"""))
    }

    @Test fun imeStartWithEmptyOrBinaryData() {
        assertEquals(TvEvent.ImeStart(""), SamsungProtocol.parseEvent("""{"event":"ms.remote.imeStart","data":""}"""))
        assertEquals(TvEvent.ImeStart(null), SamsungProtocol.parseEvent("""{"event":"ms.remote.imeStart"}"""))
        // base64 de bytes de controle: não é texto → null
        assertEquals(TvEvent.ImeStart(null), SamsungProtocol.parseEvent("""{"event":"ms.remote.imeStart","data":"AAECAw=="}"""))
    }

    @Test fun parseInstalledApps() {
        val ev = SamsungProtocol.parseEvent("""{"data":{"data":[{"appId":"111299001912","app_type":2,"icon":"/x","is_lock":0,"name":"YouTube"},{"appId":"11101200001","app_type":2,"name":"Netflix"},{"nope":1}]},"event":"ed.installedApp.get","from":"host"}""") as TvEvent.Apps
        assertEquals(listOf(InstalledApp("111299001912", "YouTube", 2), InstalledApp("11101200001", "Netflix", 2)), ev.apps)
    }

    @Test fun parseErrorAndUnknown() {
        assertEquals(TvEvent.Error("boom"), SamsungProtocol.parseEvent("""{"event":"ms.error","data":{"message":"boom"}}"""))
        assertEquals(TvEvent.Other("ed.edenTV.update"), SamsungProtocol.parseEvent("""{"event":"ed.edenTV.update","data":{}}"""))
        assertNull(SamsungProtocol.parseEvent("isto não é json"))
        assertNull(SamsungProtocol.parseEvent("""{"semEvento":1}"""))
    }

    @Test fun parseDeviceInfo() {
        val d = SamsungProtocol.parseDeviceInfo("""{"device":{"FrameTVSupport":"false","GamePadSupport":"true","ImeSyncedSupport":"true","name":"[TV] Sala","modelName":"UN55TU8000","wifiMac":"AA:BB:CC:00:11:22","PowerState":"on","type":"Samsung SmartTV"},"id":"uuid:1","isSupport":"{}","name":"[TV] Sala","type":"Samsung SmartTV","uri":"http://x"}""")!!
        assertTrue(d.isSamsung)
        assertEquals("[TV] Sala", d.name)
        assertEquals("UN55TU8000", d.model)
        assertEquals("AA:BB:CC:00:11:22", d.wifiMac)
        assertEquals("on", d.powerState)
    }

    @Test fun parseDeviceInfoRejectsNonSamsung() {
        assertEquals(false, SamsungProtocol.parseDeviceInfo("""{"hello":"world"}""")?.isSamsung ?: false)
        assertNull(SamsungProtocol.parseDeviceInfo("<html>"))
        assertNotNull(SamsungProtocol.parseDeviceInfo("{}"))
    }
}
