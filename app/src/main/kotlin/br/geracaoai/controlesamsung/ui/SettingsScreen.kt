package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.geracaoai.controlesamsung.BuildConfig

@Composable
fun SettingsPage(
    tvName: String?, ip: String?, secure: Boolean?, haptics: Boolean, autoKeyboard: Boolean, poweringOn: Boolean,
    onHaptics: (Boolean) -> Unit, onAutoKeyboard: (Boolean) -> Unit, onPowerOn: () -> Unit,
    onReconnect: () -> Unit, onDisconnect: () -> Unit, onForget: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box2 {
            Text(tvName ?: "TV Samsung", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text((ip ?: "—") + (if (secure == true) " · conexão segura (8002)" else if (secure == false) " · conexão simples (8001)" else ""), color = C.textDim, fontSize = 13.sp)
        }
        Box2 {
            SwitchRow("Vibrar ao tocar nos botões", haptics, onHaptics)
            SwitchRow("Abrir teclado quando a TV pedir texto", autoKeyboard, onAutoKeyboard)
        }
        Box2 {
            Button(onClick = onPowerOn, enabled = !poweringOn, colors = ButtonDefaults.buttonColors(containerColor = C.accent), modifier = Modifier.fillMaxWidth()) { Text(if (poweringOn) "Ligando…" else "Ligar TV pela rede (Wake-on-LAN)") }
            OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) { Text("Reconectar") }
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar / trocar de TV") }
            OutlinedButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) { Text("Esquecer esta TV (apaga a autorização)", color = C.power) }
        }
        Box2 {
            Text("Controle Samsung ${BuildConfig.VERSION_NAME}", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("App independente e original. Sem anúncios, sem login, sem nuvem: fala só com a sua TV pela rede local. Não é um produto oficial da Samsung.", color = C.textDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Box2(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}
