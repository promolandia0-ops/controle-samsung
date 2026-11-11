package br.geracaoai.controlesamsung

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import br.geracaoai.controlesamsung.net.ConnState
import br.geracaoai.controlesamsung.net.ErrorKind
import br.geracaoai.controlesamsung.net.FoundTv
import br.geracaoai.controlesamsung.protocol.InstalledApp
import br.geracaoai.controlesamsung.ui.*
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Garante que todas as telas compõem sem crash (e, se SHOTS=dir, grava capturas). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name: String) {
        val dir = System.getenv("SHOTS") ?: return
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val w = v.width.coerceAtLeast(1); val h = v.height.coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun controle() { rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) { ControlPage({}, {}, {}, {}) } } }; shot("controle") }
    @Test fun teclas() { rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) { KeysPage({}, {}) } } }; shot("teclas") }
    @Test fun apps() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) { AppsPage(listOf(InstalledApp("111299001912", "YouTube", 2), InstalledApp("zz", "Meu App", 2)), null, {}, {}, {}, {}) } } }; shot("apps")
    }
    @Test fun appsSemLista() { rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) { AppsPage(null, "Netflix", {}, {}, {}, {}) } } } }
    @Test fun ajustes() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) { SettingsPage("[TV] Sala", "192.168.0.9", true, true, true, false, {}, {}, {}, {}, {}, {}) } } }; shot("ajustes")
    }
    @Test fun conectar() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize().background(C.bg)) {
            ConnectScreen(ConnState.Failed("192.168.0.9", ErrorKind.REFUSED, "A TV em 192.168.0.9 recusou a conexão. Ela pode estar desligada/em espera (use \"Ligar TV\") ou com o controle por rede bloqueado nas configurações da TV."),
                listOf(FoundTv("192.168.0.9", "[TV] Sala", "UN55TU8000", "AA:BB:CC:00:11:22")), false, "Pronto", false, "192.168.0.9", "[TV] Sala", {}, {}, {}, {}, {})
        } } }; shot("conectar")
    }
    @Test fun conectarAprovacao() { rule.setContent { AppTheme { ConnectScreen(ConnState.WaitingApproval("1.2.3.4"), emptyList(), true, "Procurando…", true, null, null, {}, {}, {}, {}, {}) } } }
    @Test fun teclado() { rule.setContent { AppTheme { KeyboardOverlay(KeyboardState("", true), {}, {}, {}) } }; shot("teclado") }
}
