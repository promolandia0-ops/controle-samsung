package br.geracaoai.controlesamsung.net

import android.annotation.SuppressLint
import br.geracaoai.controlesamsung.data.TvStore
import br.geracaoai.controlesamsung.data.StreamingApp
import br.geracaoai.controlesamsung.data.StreamingApps
import br.geracaoai.controlesamsung.protocol.InstalledApp
import br.geracaoai.controlesamsung.protocol.LaunchType
import br.geracaoai.controlesamsung.protocol.SamsungProtocol
import br.geracaoai.controlesamsung.protocol.TvEvent
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class ErrorKind { INVALID_IP, NOT_FOUND, REFUSED, TIMEOUT, DENIED, APPROVAL_TIMEOUT, TLS, OTHER }

sealed interface ConnState {
    object Idle : ConnState
    data class Connecting(val ip: String, val detail: String) : ConnState
    data class WaitingApproval(val ip: String) : ConnState
    data class Connected(val ip: String, val secure: Boolean) : ConnState
    data class Reconnecting(val ip: String, val attempt: Int) : ConnState
    data class Failed(val ip: String, val kind: ErrorKind, val message: String) : ConnState
}

/** Mensagens de erro em português, sempre dizendo o que o usuário pode fazer. */
object ErrorText {
    fun message(kind: ErrorKind, ip: String, detail: String? = null): String = when (kind) {
        ErrorKind.INVALID_IP -> "Esse IP não parece certo. Use algo como 192.168.0.25 (IP da TV na mesma rede Wi-Fi)."
        ErrorKind.NOT_FOUND -> "TV não encontrada em $ip. Confira se a TV está ligada e no mesmo Wi-Fi do celular."
        ErrorKind.REFUSED -> "A TV em $ip recusou a conexão. Ela pode estar desligada/em espera (use \"Ligar TV\") ou com o controle por rede bloqueado nas configurações da TV."
        ErrorKind.TIMEOUT -> "A TV em $ip não respondeu a tempo. Verifique o Wi-Fi e se a TV está ligada, e tente de novo."
        ErrorKind.DENIED -> "A TV negou o acesso. Na TV, vá em Configurações > Geral > Gerenciador de dispositivos externos > Gerenciador de conexão de dispositivos e permita este celular."
        ErrorKind.APPROVAL_TIMEOUT -> "Ninguém aceitou o pedido na TV. Olhe a tela da TV, escolha \"Permitir\" e tente conectar de novo."
        ErrorKind.TLS -> "Falha na conexão segura com a TV em $ip. Tentei também sem criptografia. Tente de novo."
        ErrorKind.OTHER -> "Não consegui falar com a TV em $ip" + (if (detail.isNullOrBlank()) "." else ": $detail")
    }
}

private sealed interface Sig {
    object Opened : Sig
    data class Msg(val text: String) : Sig
    data class Closed(val code: Int, val reason: String) : Sig
    data class Fail(val t: Throwable, val httpCode: Int?) : Sig
}

private sealed interface Attempt {
    data class Dropped(val wasConnected: Boolean) : Attempt
    data class Fatal(val kind: ErrorKind, val message: String) : Attempt
    data class Unreachable(val kind: ErrorKind, val message: String) : Attempt
}

class TvClient(private val prefs: TvStore, private val scope: CoroutineScope, private val appName: String = "Controle Samsung") {
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _ime = MutableSharedFlow<TvEvent>(extraBufferCapacity = 32)
    val ime: SharedFlow<TvEvent> = _ime.asSharedFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val installedApps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    @Volatile private var socket: WebSocket? = null
    private var job: Job? = null

    private val base = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private val tlsClients = HashMap<String, OkHttpClient>()

    /** Cliente TLS que aceita o certificado autoassinado da TV, travado ao IP literal informado (rede local). */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun tlsClient(ip: String): OkHttpClient = tlsClients.getOrPut(ip) {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
        base.newBuilder().sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { host, _ -> host == ip }.build()
    }

    val isConnected: Boolean get() = _state.value is ConnState.Connected

    fun connect(ipRaw: String) {
        val ip = ipRaw.trim()
        job?.cancel()
        closeSocket()
        if (!LocalNet.isPrivateHost(ip)) {
            _state.value = ConnState.Failed(ip, ErrorKind.INVALID_IP, ErrorText.message(ErrorKind.INVALID_IP, ip))
            return
        }
        _apps.value = null
        job = scope.launch { session(ip) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeSocket()
        _state.value = ConnState.Idle
    }

    private fun closeSocket() {
        try { socket?.close(1000, "bye") } catch (_: Exception) {}
        socket = null
    }

    private suspend fun session(ip: String) {
        var everConnected = false
        var retries = 0
        while (scope.isActive) {
            val r = attempt(ip, reconnecting = everConnected)
            when (r) {
                is Attempt.Fatal -> { _state.value = ConnState.Failed(ip, r.kind, r.message); return }
                is Attempt.Dropped -> {
                    if (r.wasConnected) { everConnected = true; retries = 0 }
                    retries++
                    if (retries > 6) {
                        _state.value = ConnState.Failed(ip, ErrorKind.TIMEOUT, "Perdi a conexão com a TV. " + ErrorText.message(ErrorKind.TIMEOUT, ip))
                        return
                    }
                }
                is Attempt.Unreachable -> {
                    if (!everConnected) { _state.value = ConnState.Failed(ip, r.kind, r.message); return }
                    retries++
                    if (retries > 6) { _state.value = ConnState.Failed(ip, r.kind, r.message); return }
                }
            }
            _state.value = ConnState.Reconnecting(ip, retries)
            delay((1000L * retries).coerceAtMost(5000L))
        }
    }

    private suspend fun attempt(ip: String, reconnecting: Boolean): Attempt {
        val unreachable = ArrayList<Pair<ErrorKind, String>>()
        for (secure in listOf(true, false)) {
            if (!reconnecting) _state.value = ConnState.Connecting(ip, if (secure) "Conectando (seguro)…" else "Tentando conexão simples…")
            val token = if (secure) prefs.token(ip) else null
            val url = SamsungProtocol.wsUrl(ip, secure, appName, token)
            val sigs = Channel<Sig>(Channel.UNLIMITED)
            val client = if (secure) tlsClient(ip) else base
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { sigs.trySend(Sig.Opened) }
                override fun onMessage(webSocket: WebSocket, text: String) { sigs.trySend(Sig.Msg(text)) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { sigs.trySend(Sig.Closed(code, reason)) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { sigs.trySend(Sig.Fail(t, response?.code)) }
            })
            try {
                val opened = withTimeoutOrNull(7000) { sigs.receive() }
                when (opened) {
                    null -> { unreachable += ErrorKind.TIMEOUT to ErrorText.message(ErrorKind.TIMEOUT, ip); ws.cancel(); continue }
                    is Sig.Fail -> { unreachable += classify(opened, ip, secure); ws.cancel(); continue }
                    is Sig.Closed -> { unreachable += ErrorKind.OTHER to ErrorText.message(ErrorKind.OTHER, ip, "conexão fechada (${opened.code})"); continue }
                    else -> Unit
                }
                // Socket aberto: aguardar autorização/"ms.channel.connect".
                val hadToken = token != null
                var waitingShown = false
                val deadline = System.currentTimeMillis() + 45_000
                var connected = false
                while (!connected) {
                    val remain = deadline - System.currentTimeMillis()
                    if (remain <= 0) { ws.cancel(); return Attempt.Fatal(ErrorKind.APPROVAL_TIMEOUT, ErrorText.message(ErrorKind.APPROVAL_TIMEOUT, ip)) }
                    val slice = if (!waitingShown) (if (hadToken) 2500L else 400L) else remain
                    val sig = withTimeoutOrNull(slice.coerceAtMost(remain)) { sigs.receive() }
                    if (sig == null) {
                        if (!waitingShown) { waitingShown = true; if (!reconnecting) _state.value = ConnState.WaitingApproval(ip) }
                        continue
                    }
                    when (sig) {
                        is Sig.Msg -> when (val ev = SamsungProtocol.parseEvent(sig.text)) {
                            is TvEvent.Connected -> {
                                connected = true
                                if (!ev.token.isNullOrBlank()) prefs.setToken(ip, ev.token)
                            }
                            TvEvent.Unauthorized -> { ws.cancel(); return Attempt.Fatal(ErrorKind.DENIED, ErrorText.message(ErrorKind.DENIED, ip)) }
                            TvEvent.ChannelTimeout -> { ws.cancel(); return Attempt.Fatal(ErrorKind.APPROVAL_TIMEOUT, ErrorText.message(ErrorKind.APPROVAL_TIMEOUT, ip)) }
                            is TvEvent.Error -> { ws.cancel(); return Attempt.Fatal(ErrorKind.OTHER, ErrorText.message(ErrorKind.OTHER, ip, ev.message)) }
                            else -> Unit
                        }
                        is Sig.Closed, is Sig.Fail -> {
                            // Caiu antes de autorizar: tenta o próximo modo (8001) ou considera recusado.
                            unreachable += ErrorKind.REFUSED to ErrorText.message(ErrorKind.REFUSED, ip)
                            ws.cancel()
                            break
                        }
                        else -> Unit
                    }
                }
                if (!connected) continue
                // Conectado.
                socket = ws
                prefs.lastIp = ip
                _state.value = ConnState.Connected(ip, secure)
                scope.launch {
                    TvHttp.info(ip)?.let { d ->
                        d.wifiMac?.let { prefs.setMac(ip, it) }
                        d.name?.let { prefs.lastName = it }
                    }
                }
                ws.send(SamsungProtocol.installedApps())
                while (true) {
                    when (val sig = sigs.receive()) {
                        is Sig.Msg -> handle(SamsungProtocol.parseEvent(sig.text))
                        is Sig.Closed, is Sig.Fail -> { socket = null; return Attempt.Dropped(true) }
                        else -> Unit
                    }
                }
            } finally {
                if (socket !== ws) { try { ws.cancel() } catch (_: Exception) {} }
            }
        }
        val best = unreachable.firstOrNull { it.first == ErrorKind.REFUSED }
            ?: unreachable.firstOrNull { it.first == ErrorKind.NOT_FOUND }
            ?: unreachable.firstOrNull { it.first == ErrorKind.TIMEOUT }
            ?: unreachable.firstOrNull()
            ?: (ErrorKind.OTHER to ErrorText.message(ErrorKind.OTHER, ip))
        return Attempt.Unreachable(best.first, best.second)
    }

    private fun handle(ev: TvEvent?) {
        when (ev) {
            is TvEvent.ImeStart, is TvEvent.ImeUpdate, TvEvent.ImeEnd -> _ime.tryEmit(ev)
            is TvEvent.Apps -> _apps.value = ev.apps
            is TvEvent.Error -> _notices.tryEmit("A TV respondeu com erro: ${ev.message}")
            TvEvent.Unauthorized -> _notices.tryEmit("A TV negou este comando.")
            else -> Unit
        }
    }

    private fun classify(f: Sig.Fail, ip: String, secure: Boolean): Pair<ErrorKind, String> {
        val t = f.t
        val chain = generateSequence<Throwable>(t) { it.cause }.take(5).toList()
        val msg = chain.joinToString(" | ") { it.message ?: "" }.lowercase()
        return when {
            chain.any { it is SocketTimeoutException } || msg.contains("timed out") -> ErrorKind.TIMEOUT to ErrorText.message(ErrorKind.TIMEOUT, ip)
            chain.any { it is ConnectException } && msg.contains("refused") -> ErrorKind.REFUSED to ErrorText.message(ErrorKind.REFUSED, ip)
            chain.any { it is NoRouteToHostException || it is UnknownHostException } || msg.contains("unreachable") || msg.contains("no route") -> ErrorKind.NOT_FOUND to ErrorText.message(ErrorKind.NOT_FOUND, ip)
            chain.any { it is SSLException } -> ErrorKind.TLS to ErrorText.message(ErrorKind.TLS, ip)
            f.httpCode != null -> ErrorKind.OTHER to ErrorText.message(ErrorKind.OTHER, ip, "a TV respondeu HTTP ${f.httpCode}")
            else -> ErrorKind.OTHER to ErrorText.message(ErrorKind.OTHER, ip, t.message)
        }
    }

    // ------------------------------------------------------------------ envio

    private fun send(text: String): Boolean {
        val ws = socket
        if (ws == null || !isConnected) { _notices.tryEmit("Sem conexão com a TV. Conecte primeiro."); return false }
        return ws.send(text)
    }

    fun key(code: String): Boolean = send(SamsungProtocol.key(code))
    fun text(s: String): Boolean = send(SamsungProtocol.inputString(s))
    fun endInput(): Boolean = send(SamsungProtocol.inputEnd())
    fun refreshApps() { socket?.send(SamsungProtocol.installedApps()) }

    /**
     * Abre um app. Tenta o ID real (lista da TV) e os IDs conhecidos via WebSocket (DEEP_LINK);
     * confirma pela API REST quando a TV suporta e, sem confirmação, também dispara o POST REST.
     * Retorna mensagem para o usuário.
     */
    suspend fun launch(app: StreamingApp): String {
        val ip = (state.value as? ConnState.Connected)?.ip ?: return "Sem conexão com a TV. Conecte primeiro."
        return launchIds(ip, app.label, StreamingApps.launchOrder(app, installedApps.value.orEmpty()))
    }

    suspend fun launchInstalled(app: InstalledApp): String {
        val ip = (state.value as? ConnState.Connected)?.ip ?: return "Sem conexão com a TV. Conecte primeiro."
        return launchIds(ip, app.name, listOf(app.appId))
    }

    private suspend fun launchIds(ip: String, label: String, ids: List<String>): String {
        for ((i, id) in ids.take(3).withIndex()) {
            if (!send(SamsungProtocol.launchApp(id, LaunchType.DEEP_LINK))) return "Não consegui enviar o comando para a TV."
            delay(2200)
            when (TvHttp.appRunning(ip, id)) {
                true -> return "Abrindo $label…"
                false -> if (i == ids.take(3).lastIndex) break else continue
                null -> {
                    // TV não suporta consulta REST: dispara também o POST e assume enviado.
                    TvHttp.launchRest(ip, id)
                    return "Comando enviado: $label. Se não abrir, o app pode não estar instalado nesta TV."
                }
            }
        }
        return "Não consegui abrir $label. Talvez não esteja instalado nesta TV ou o ID seja diferente no seu modelo."
    }
}
