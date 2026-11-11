package br.geracaoai.controlesamsung.net

import br.geracaoai.controlesamsung.data.StreamingApps
import br.geracaoai.controlesamsung.protocol.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetTest {
    @Test fun privateHosts() {
        assertTrue(LocalNet.isPrivateHost("192.168.0.25"))
        assertTrue(LocalNet.isPrivateHost("10.1.2.3"))
        assertTrue(LocalNet.isPrivateHost("172.16.0.1"))
        assertTrue(LocalNet.isPrivateHost("172.31.255.1"))
        assertFalse(LocalNet.isPrivateHost("172.32.0.1"))
        assertFalse(LocalNet.isPrivateHost("8.8.8.8"))
        assertFalse(LocalNet.isPrivateHost("example.com"))
        assertFalse(LocalNet.isPrivateHost("192.168.0.256"))
        assertFalse(LocalNet.isPrivateHost("192.168.0"))
        assertFalse(LocalNet.isPrivateHost(""))
    }

    @Test fun broadcast() {
        assertEquals("192.168.0.255", LocalNet.broadcastOf("192.168.0.23", 24))
        assertEquals("192.168.3.255", LocalNet.broadcastOf("192.168.2.10", 23))
        assertEquals("10.255.255.255", LocalNet.broadcastOf("10.1.2.3", 8))
        assertNull(LocalNet.broadcastOf("bad", 24))
    }

    @Test fun errorMessagesArePortugueseAndMentionIp() {
        for (k in ErrorKind.values()) {
            val m = ErrorText.message(k, "192.168.0.9", "x")
            assertTrue(k.name + ": " + m, m.isNotBlank())
        }
        assertTrue(ErrorText.message(ErrorKind.NOT_FOUND, "192.168.0.9").contains("192.168.0.9"))
        assertTrue(ErrorText.message(ErrorKind.APPROVAL_TIMEOUT, "1.1.1.1").contains("Permitir"))
    }

    @Test fun appMatchByIdThenName() {
        val netflix = StreamingApps.all.first { it.key == "netflix" }
        val installed = listOf(InstalledApp("999", "Netflix", 2), InstalledApp("3201907018807", "Outra coisa", 2))
        // O ID conhecido tem prioridade sobre o nome.
        assertEquals("3201907018807", StreamingApps.match(netflix, installed)?.appId)
        assertEquals("999", StreamingApps.match(netflix, listOf(InstalledApp("999", "Netflix", 2)))?.appId)
        assertNull(StreamingApps.match(netflix, listOf(InstalledApp("1", "YouTube", 2))))
    }

    @Test fun launchOrderPutsInstalledIdFirstWithoutDuplicates() {
        val prime = StreamingApps.all.first { it.key == "prime" }
        val order = StreamingApps.launchOrder(prime, listOf(InstalledApp("3201910019365", "Prime Video", 2)))
        assertEquals(listOf("3201910019365", "3201512006785"), order)
        assertEquals(prime.ids, StreamingApps.launchOrder(prime, emptyList()))
    }

    @Test fun maxDoesNotMatchUnrelatedApps() {
        val max = StreamingApps.all.first { it.key == "max" }
        assertNull(StreamingApps.match(max, listOf(InstalledApp("1", "Amazon Music", 2), InstalledApp("2", "Tomax", 2))))
        assertEquals("7", StreamingApps.match(max, listOf(InstalledApp("7", "Max", 2)))?.appId)
    }
}
