package br.geracaoai.controlesamsung.net

import br.geracaoai.controlesamsung.protocol.SamsungProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WakeOnLan {
    /** Envia o pacote mágico por broadcast (portas 9 e 7) para os endereços dados. Retorna true se enviou algo. */
    suspend fun send(macText: String?, broadcasts: List<String>): Boolean = withContext(Dispatchers.IO) {
        val mac = SamsungProtocol.parseMac(macText) ?: return@withContext false
        val data = SamsungProtocol.magicPacket(mac)
        var sent = false
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (addr in (broadcasts + "255.255.255.255").distinct()) {
                    for (port in intArrayOf(9, 7)) {
                        try {
                            s.send(DatagramPacket(data, data.size, InetAddress.getByName(addr), port))
                            sent = true
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        sent
    }
}
