package br.geracaoai.controlesamsung.e2e

import br.geracaoai.controlesamsung.data.StreamingApps
import br.geracaoai.controlesamsung.data.TvStore
import br.geracaoai.controlesamsung.net.ConnState
import br.geracaoai.controlesamsung.net.TvClient
import br.geracaoai.controlesamsung.protocol.Keys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assume
import org.junit.Test

class FakeTvE2E {
    class Mem : TvStore {
        override var lastIp: String? = null; override var lastName: String? = null
        val t = HashMap<String, String?>(); val m = HashMap<String, String?>()
        override fun token(ip: String) = t[ip]; override fun setToken(ip: String, token: String?) { t[ip] = token }
        override fun mac(ip: String) = m[ip]; override fun setMac(ip: String, mac: String?) { m[ip] = mac }
    }
    @Test fun run() = runBlocking {
        Assume.assumeTrue(System.getenv("FAKETV") != null)
        if (System.getenv("FAKETV") == "2") { val sc = CoroutineScope(SupervisorJob() + Dispatchers.Default); val c2 = TvClient(Mem(), sc); c2.connect("127.0.0.1"); delay(6000); println("ERR REFUSED: " + c2.state.value); return@runBlocking }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = Mem()
        val c = TvClient(store, scope)
        c.connect("127.0.0.1")
        val states = ArrayList<String>()
        val t0 = System.currentTimeMillis()
        while (c.state.value !is ConnState.Connected && System.currentTimeMillis() - t0 < 15000) { states.add(c.state.value.toString()); delay(300) }
        println("STATES: " + states.distinct())
        println("FINAL: " + c.state.value + " token=" + store.token("127.0.0.1") + " mac=" + store.mac("127.0.0.1"))
        c.key(Keys.VOL_UP); c.key(Keys.digit(5)); c.text("Olá TV"); c.text("Olá TV 2")
        delay(500)
        println("APPS: " + c.installedApps.value)
        val yt = StreamingApps.all.first { it.key == "youtube" }
        println("LAUNCH YT: " + c.launch(yt))
        val nf = StreamingApps.all.first { it.key == "netflix" }
        println("LAUNCH NF: " + c.launch(nf))
        val ime = withTimeoutOrNull(14000) { c.ime.first() }
        println("IME: $ime")
        c.endInput()
        delay(300)
        c.disconnect()
        // erro: porta fechada
        val c2 = TvClient(Mem(), scope); c2.connect("127.0.0.1"); delay(1); println("ERR REFUSED: " + c2.state.value)
        val c3 = TvClient(Mem(), scope); c3.connect("8.8.8.8"); println("ERR INVALID: " + c3.state.value)
        val c4 = TvClient(Mem(), scope); c4.connect("10.255.255.1"); delay(20000); println("ERR TIMEOUT: " + c4.state.value)
        scope.cancel()
    }
}
