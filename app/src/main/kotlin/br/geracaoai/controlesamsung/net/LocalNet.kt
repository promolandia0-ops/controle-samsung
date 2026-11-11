package br.geracaoai.controlesamsung.net

import java.net.Inet4Address
import java.net.InetAddress

object LocalNet {
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isValidIpv4(s: String): Boolean {
        val m = ipv4.matchEntire(s.trim()) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    /** Só IPv4 literal de rede local (10/8, 172.16/12, 192.168/16, 169.254/16, loopback). Nunca resolve DNS. */
    fun isPrivateHost(host: String): Boolean {
        if (!isValidIpv4(host.trim())) return false
        val p = host.trim().split(".").map { it.toInt() }
        return p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) ||
            p[0] == 127
    }

    /** Endereço de broadcast da sub-rede a partir de IP + prefixo. */
    fun broadcastOf(ip: String, prefix: Int): String? {
        if (!isValidIpv4(ip) || prefix !in 8..30) return null
        val b = (InetAddress.getByName(ip) as? Inet4Address)?.address ?: return null
        val addr = b.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xFF) }
        val mask = (-1 shl (32 - prefix))
        val bc = addr or mask.inv()
        return "${(bc ushr 24) and 255}.${(bc ushr 16) and 255}.${(bc ushr 8) and 255}.${bc and 255}"
    }
}
