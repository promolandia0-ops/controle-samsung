package br.geracaoai.controlesamsung.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class FoundTv(val ip: String, val name: String, val model: String?, val mac: String?)

/** Descoberta de TVs Samsung na rede local: SSDP + mDNS (NSD) e, se nada aparecer, varredura da sub-rede (porta 8001). */
class Discovery(context: Context) {
    private val ctx = context.applicationContext
    private val found = ConcurrentHashMap<String, FoundTv>()
    private val probed = ConcurrentHashMap.newKeySet<String>()

    /** IP e prefixo da rede Wi-Fi atual (ex.: 192.168.0.23/24). */
    fun localNetwork(): Pair<String, Int>? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net)
        if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
        val lp = cm.getLinkProperties(net) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is java.net.Inet4Address } ?: return null
        return la.address.hostAddress!! to la.prefixLength
    }

    fun broadcasts(): List<String> = localNetwork()?.let { (ip, p) -> listOfNotNull(LocalNet.broadcastOf(ip, p)) } ?: emptyList()

    suspend fun discover(onFound: (FoundTv) -> Unit, onPhase: (String) -> Unit) = withContext(Dispatchers.IO) {
        found.clear(); probed.clear()
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try { wifi?.createMulticastLock("controle-samsung")?.also { it.setReferenceCounted(false); it.acquire() } } catch (_: Exception) { null }
        try {
            onPhase("Procurando TVs Samsung na rede…")
            coroutineScope {
                val cand = ConcurrentHashMap.newKeySet<String>()
                val ssdp = async { ssdp(cand) }
                val mdns = async { mdns(cand) }
                // Sonda candidatos assim que aparecem, durante 4,5 s.
                val probes = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
                val end = System.currentTimeMillis() + 4500
                while (System.currentTimeMillis() < end) {
                    for (ip in cand.toList()) if (probed.add(ip)) probes += async { probe(ip, onFound) }
                    delay(250)
                }
                ssdp.await(); mdns.await()
                for (ip in cand.toList()) if (probed.add(ip)) probes += async { probe(ip, onFound) }
                probes.awaitAll()
            }
            if (found.isEmpty()) {
                onPhase("Varrendo a rede inteira…")
                scanSubnet(onFound)
            }
            onPhase(if (found.isEmpty()) "Nenhuma TV encontrada" else "Pronto")
        } finally {
            try { lock?.release() } catch (_: Exception) {}
        }
    }

    private suspend fun probe(ip: String, onFound: (FoundTv) -> Unit) {
        if (!LocalNet.isPrivateHost(ip)) return
        val info = TvHttp.info(ip) ?: return
        if (!info.isSamsung) return
        val tv = FoundTv(ip, info.name ?: "TV Samsung", info.model, info.wifiMac)
        if (found.putIfAbsent(ip, tv) == null) onFound(tv)
    }

    private fun ssdp(out: MutableSet<String>) {
        val targets = listOf(
            "urn:samsung.com:device:RemoteControlReceiver:1",
            "urn:dial-multiscreen-org:service:dial:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
        )
        try {
            DatagramSocket(0).use { s ->
                s.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) {
                    for (st in targets) {
                        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n").toByteArray()
                        try { s.send(DatagramPacket(msg, msg.size, group, 1900)) } catch (_: Exception) {}
                    }
                }
                val buf = ByteArray(2048)
                val end = System.currentTimeMillis() + 4000
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        p.address.hostAddress?.let { out.add(it) }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun mdns(out: MutableSet<String>) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String?, e: Int) {}
            override fun onStopDiscoveryFailed(t: String?, e: Int) {}
            override fun onDiscoveryStarted(t: String?) {}
            override fun onDiscoveryStopped(t: String?) {}
            override fun onServiceLost(s: NsdServiceInfo?) {}
            @Suppress("DEPRECATION")
            override fun onServiceFound(s: NsdServiceInfo) {
                try {
                    nsd.resolveService(s, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo?, e: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) { si.host?.hostAddress?.let { out.add(it) } }
                    })
                } catch (_: Exception) {}
            }
        }
        try {
            nsd.discoverServices("_samsungmsf._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            delay(4000)
        } catch (_: Exception) {
        } finally {
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
    }

    private suspend fun scanSubnet(onFound: (FoundTv) -> Unit) = coroutineScope {
        val (ip, prefix) = localNetwork() ?: return@coroutineScope
        if (prefix < 16 || prefix > 30) return@coroutineScope
        val base = ip.split(".").map { it.toInt() }
        // Limita a no máximo uma /24 em volta do IP do celular (rápido e suficiente na maioria das casas).
        val hosts = (1..254).map { "${base[0]}.${base[1]}.${base[2]}.$it" }.filter { it != ip }
        val sem = Semaphore(48)
        hosts.map { h ->
            async {
                sem.withPermit {
                    if (portOpen(h, 8001, 350)) { if (probed.add(h)) probe(h, onFound) }
                }
            }
        }.awaitAll()
    }

    private fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }
}
