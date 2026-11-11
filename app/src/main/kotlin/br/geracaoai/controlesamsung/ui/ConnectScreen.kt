package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.geracaoai.controlesamsung.net.ConnState
import br.geracaoai.controlesamsung.net.FoundTv

@Composable
fun ConnectScreen(
    state: ConnState, tvs: List<FoundTv>, discovering: Boolean, phase: String, poweringOn: Boolean,
    savedIp: String?, savedName: String?,
    onSearch: () -> Unit, onConnectTv: (FoundTv) -> Unit, onConnectIp: (String) -> Unit,
    onPowerOn: (String?) -> Unit, onForget: () -> Unit,
) {
    var ip by rememberSaveable { mutableStateOf(savedIp.orEmpty()) }
    val busy = state is ConnState.Connecting || state is ConnState.WaitingApproval || state is ConnState.Reconnecting
    LaunchedEffect(Unit) { if (tvs.isEmpty() && savedIp == null && !discovering) onSearch() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Tv, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Column {
                Text("Controle Samsung", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Sem anúncios · sem login · sem nuvem", color = C.textDim, fontSize = 13.sp)
            }
        }

        StatusCard(state)

        if (savedIp != null) {
            Card {
                Text("Última TV", color = C.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(savedName ?: "TV Samsung", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(savedIp, color = C.textDim, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onConnectIp(savedIp) }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) { Text("Reconectar") }
                    OutlinedButton(onClick = { onPowerOn(savedIp) }, enabled = !poweringOn) {
                        Icon(Icons.Filled.PowerSettingsNew, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(if (poweringOn) "Ligando…" else "Ligar TV")
                    }
                    TextButton(onClick = onForget, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text("Esquecer", color = C.textDim, softWrap = false, fontSize = 13.sp) }
                }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TVs na rede", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onSearch, enabled = !discovering) {
                    Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(if (discovering) "Buscando…" else "Buscar")
                }
            }
            if (discovering) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = C.accent)
                Text(phase, color = C.textDim, fontSize = 13.sp)
            } else if (phase.isNotBlank()) Text(phase, color = C.textDim, fontSize = 13.sp)
            tvs.forEach { tv ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.button).clickable(enabled = !busy) { onConnectTv(tv) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Tv, null, tint = C.accent)
                    Column(Modifier.weight(1f)) {
                        Text(tv.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(tv.model, tv.ip).joinToString(" · "), color = C.textDim, fontSize = 13.sp)
                    }
                    Text("Conectar", color = C.accent, fontSize = 14.sp)
                }
            }
            if (!discovering && tvs.isEmpty() && phase.isNotBlank()) {
                Text("Dica: o celular precisa estar no mesmo Wi-Fi da TV, com a TV ligada. Se não aparecer, digite o IP abaixo (TV: Configurações > Geral > Rede > Status da rede).", color = C.textDim, fontSize = 13.sp)
            }
        }

        Card {
            Text("Conectar por IP", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = ip, onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                label = { Text("IP da TV (ex.: 192.168.0.25)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onConnectIp(ip) }, enabled = !busy && ip.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = C.accent), modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
            Text("Na primeira conexão a TV mostra \"Permitir este dispositivo?\". Escolha Permitir — eu guardo a autorização.", color = C.textDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun StatusCard(state: ConnState) {
    when (state) {
        is ConnState.Connecting, is ConnState.Reconnecting -> Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = C.accent)
                Text(
                    when (state) {
                        is ConnState.Connecting -> "${state.detail} (${state.ip})"
                        is ConnState.Reconnecting -> "Reconectando… tentativa ${state.attempt}"
                        else -> ""
                    }, color = C.text, fontSize = 15.sp,
                )
            }
        }
        is ConnState.WaitingApproval -> Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = C.warn)
                Text("Olhe a tela da TV e escolha \"Permitir\" para este celular.", color = C.text, fontSize = 15.sp)
            }
        }
        is ConnState.Failed -> Card {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.ErrorOutline, null, tint = C.power)
                Text(state.message, color = C.text, fontSize = 15.sp)
            }
        }
        else -> Unit
    }
}
