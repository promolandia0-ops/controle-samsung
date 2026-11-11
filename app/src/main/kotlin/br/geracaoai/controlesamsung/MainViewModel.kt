package br.geracaoai.controlesamsung

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.geracaoai.controlesamsung.data.Prefs
import br.geracaoai.controlesamsung.data.StreamingApp
import br.geracaoai.controlesamsung.net.ConnState
import br.geracaoai.controlesamsung.net.Discovery
import br.geracaoai.controlesamsung.net.FoundTv
import br.geracaoai.controlesamsung.net.LocalNet
import br.geracaoai.controlesamsung.net.TvClient
import br.geracaoai.controlesamsung.net.TvHttp
import br.geracaoai.controlesamsung.net.WakeOnLan
import br.geracaoai.controlesamsung.protocol.InstalledApp
import br.geracaoai.controlesamsung.protocol.Keys
import br.geracaoai.controlesamsung.protocol.TvEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KeyboardState(val initialText: String, val fromTv: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val client = TvClient(prefs, viewModelScope)
    private val discovery = Discovery(app)

    private val _tvs = MutableStateFlow<List<FoundTv>>(emptyList())
    val tvs: StateFlow<List<FoundTv>> = _tvs.asStateFlow()
    private val _phase = MutableStateFlow("")
    val phase: StateFlow<String> = _phase.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _keyboard = MutableStateFlow<KeyboardState?>(null)
    val keyboard: StateFlow<KeyboardState?> = _keyboard.asStateFlow()
    private val _launching = MutableStateFlow<String?>(null)
    val launching: StateFlow<String?> = _launching.asStateFlow()
    private val _poweringOn = MutableStateFlow(false)
    val poweringOn: StateFlow<Boolean> = _poweringOn.asStateFlow()

    private val _haptics = MutableStateFlow(prefs.haptics)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    private val _autoKb = MutableStateFlow(prefs.autoKeyboard)
    val autoKeyboard: StateFlow<Boolean> = _autoKb.asStateFlow()

    private var discoveryJob: Job? = null
    private var powerJob: Job? = null

    init {
        viewModelScope.launch { client.notices.collect { _messages.tryEmit(it) } }
        viewModelScope.launch {
            client.ime.collect { ev ->
                when (ev) {
                    is TvEvent.ImeStart -> if (_autoKb.value) _keyboard.value = KeyboardState(ev.currentText.orEmpty(), true)
                    TvEvent.ImeEnd -> if (_keyboard.value?.fromTv == true) _keyboard.value = null
                    else -> Unit
                }
            }
        }
        prefs.lastIp?.let { client.connect(it) }
    }

    val savedIp: String? get() = prefs.lastIp
    val savedName: String? get() = prefs.lastName

    fun onForeground() {
        val s = client.state.value
        val ip = prefs.lastIp
        if (ip != null && (s is ConnState.Idle)) client.connect(ip)
    }

    fun connect(ip: String) = client.connect(ip)
    fun connectTo(tv: FoundTv) {
        prefs.lastName = tv.name
        tv.mac?.let { prefs.setMac(tv.ip, it) }
        client.connect(tv.ip)
    }
    fun disconnect() { client.disconnect(); _keyboard.value = null }
    fun forget() {
        val ip = prefs.lastIp ?: (client.state.value as? ConnState.Connected)?.ip
        client.disconnect()
        ip?.let { prefs.forget(it) }
        _keyboard.value = null
    }

    fun setHaptics(v: Boolean) { prefs.haptics = v; _haptics.value = v }
    fun setAutoKeyboard(v: Boolean) { prefs.autoKeyboard = v; _autoKb.value = v }

    fun startDiscovery() {
        if (_discovering.value) return
        _tvs.value = emptyList()
        discoveryJob = viewModelScope.launch {
            _discovering.value = true
            try {
                discovery.discover(
                    onFound = { tv -> _tvs.value = (_tvs.value + tv).distinctBy { it.ip } },
                    onPhase = { _phase.value = it },
                )
            } catch (e: Exception) {
                _phase.value = "Erro na busca: ${e.message ?: "desconhecido"}. Use o IP manual."
            } finally {
                _discovering.value = false
            }
        }
    }

    fun key(code: String) { client.key(code) }

    fun power() {
        if (!client.isConnected) { _messages.tryEmit("Sem conexão. Use \"Ligar TV\" para ligar pela rede."); return }
        client.key(Keys.POWER)
        viewModelScope.launch {
            delay(900)
            _messages.tryEmit("Comando de ligar/desligar enviado.")
        }
    }

    fun sendText(t: String) { client.text(t) }
    fun enter() { client.key(Keys.ENTER) }
    fun openKeyboard() { _keyboard.value = KeyboardState("", false) }
    fun closeKeyboard() {
        val kb = _keyboard.value
        _keyboard.value = null
        if (kb != null) client.endInput()
    }

    fun launch(app: StreamingApp) = runLaunch(app.label) { client.launch(app) }
    fun launchInstalled(app: InstalledApp) = runLaunch(app.name) { client.launchInstalled(app) }

    private fun runLaunch(label: String, block: suspend () -> String) {
        if (_launching.value != null) return
        _launching.value = label
        viewModelScope.launch {
            try {
                _messages.tryEmit("Abrindo $label…")
                _messages.tryEmit(block())
            } finally {
                _launching.value = null
            }
        }
    }

    /** Liga a TV por Wake-on-LAN (pacote mágico) e espera ela responder para reconectar. */
    fun powerOn(ipRaw: String?) {
        val ip = ipRaw?.trim().orEmpty()
        if (!LocalNet.isPrivateHost(ip)) { _messages.tryEmit("Conecte (ou digite o IP da TV) pelo menos uma vez para eu poder ligá-la."); return }
        if (_poweringOn.value) return
        powerJob = viewModelScope.launch {
            _poweringOn.value = true
            try {
                var mac = prefs.mac(ip)
                if (mac == null) mac = TvHttp.info(ip)?.wifiMac?.also { prefs.setMac(ip, it) }
                if (mac == null) {
                    _messages.tryEmit("Ainda não sei o endereço (MAC) dessa TV. Conecte uma vez com a TV ligada, aí eu guardo e consigo ligá-la depois.")
                    return@launch
                }
                val ok = WakeOnLan.send(mac, discovery.broadcasts())
                if (!ok) { _messages.tryEmit("Não consegui enviar o sinal de ligar (MAC inválido ou sem rede)."); return@launch }
                repeat(2) { delay(400); WakeOnLan.send(mac, discovery.broadcasts()) }
                _messages.tryEmit("Sinal enviado. Aguardando a TV ligar…")
                val end = System.currentTimeMillis() + 45_000
                while (System.currentTimeMillis() < end) {
                    delay(2500)
                    val info = TvHttp.info(ip)
                    if (info != null && (info.powerState == null || info.powerState.equals("on", true))) {
                        delay(1500)
                        client.connect(ip)
                        return@launch
                    }
                }
                _messages.tryEmit("A TV não ligou. Ligar pela rede precisa estar ativado na TV (Configurações > Geral > Rede > Configurações de especialista > Ligar com Wi-Fi). Em alguns modelos só funciona com a TV em espera recente.")
            } finally {
                _poweringOn.value = false
            }
        }
    }
}
