package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.geracaoai.controlesamsung.protocol.Keys
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState

/** Aba "Controle": o controle principal, no estilo escuro/arredondado de controle inteligente. */
@Composable
fun ControlPage(send: (String) -> Unit, onKeyboard: () -> Unit, onPower: () -> Unit, tick: () -> Unit) {
    var touchpad by rememberSaveable { mutableStateOf(false) }
    val press: (String) -> Unit = { tick(); send(it) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Filled.PowerSettingsNew, "Ligar ou desligar a TV", { tick(); onPower() }, color = C.power, tint = Color.White)
            IconKey(Icons.Filled.Input, "Fonte de entrada", { press(Keys.SOURCE) })
            IconKey(Icons.Filled.Keyboard, "Abrir teclado do celular", { tick(); onKeyboard() })
            IconKey(Icons.Filled.Settings, "Configurações da TV", { press(Keys.MENU) })
        }
        if (touchpad) TouchPad(press) else DPad(press)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Filled.ArrowBack, "Voltar", { press(Keys.BACK) }, size = 60.dp)
            IconKey(Icons.Filled.Home, "Início", { press(Keys.HOME) }, size = 60.dp)
            IconKey(if (touchpad) Icons.Filled.Gamepad else Icons.Filled.TouchApp, if (touchpad) "Voltar para as setas" else "Modo touchpad", { tick(); touchpad = !touchpad }, size = 60.dp, tint = if (touchpad) C.accent else C.text)
            IconKey(Icons.Filled.Close, "Sair", { press(Keys.EXIT) }, size = 60.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RockerPill("Volume", Icons.Filled.Add, Icons.Filled.Remove, "Aumentar volume", "Diminuir volume", { press(Keys.VOL_UP) }, { press(Keys.VOL_DOWN) }, "VOL")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IconKey(Icons.Filled.VolumeOff, "Mudo", { press(Keys.MUTE) })
                TextKey("Guia", { press(Keys.GUIDE) }, Modifier.width(76.dp).height(44.dp), "Guia da TV", fontSize = 14)
                TextKey("Info", { press(Keys.INFO) }, Modifier.width(76.dp).height(44.dp), "Informações", fontSize = 14)
            }
            RockerPill("Canal", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown, "Próximo canal", "Canal anterior", { press(Keys.CH_UP) }, { press(Keys.CH_DOWN) }, "CH")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconKey(Icons.Filled.FastRewind, "Retroceder", { press(Keys.REWIND) }, size = 52.dp, iconSize = 24.dp)
            IconKey(Icons.Filled.PlayArrow, "Reproduzir", { press(Keys.PLAY) }, size = 52.dp)
            IconKey(Icons.Filled.Pause, "Pausar", { press(Keys.PAUSE) }, size = 52.dp)
            IconKey(Icons.Filled.FastForward, "Avançar", { press(Keys.FF) }, size = 52.dp, iconSize = 24.dp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Pílula vertical de + / − (volume e canal). Segurar repete. */
@Composable
fun RockerPill(
    label: String, up: androidx.compose.ui.graphics.vector.ImageVector, down: androidx.compose.ui.graphics.vector.ImageVector,
    upDesc: String, downDesc: String, onUp: () -> Unit, onDown: () -> Unit, tag: String,
) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(38.dp)).background(C.surface).border(1.dp, C.outline, RoundedCornerShape(38.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PressButton(upDesc, onUp, Modifier.size(64.dp), CircleShape, Color.Transparent, repeat = true) { Icon(up, null, tint = C.text, modifier = Modifier.size(30.dp)) }
        Text(tag, color = C.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 14.dp).semantics { contentDescription = label })
        PressButton(downDesc, onDown, Modifier.size(64.dp), CircleShape, Color.Transparent, repeat = true) { Icon(down, null, tint = C.text, modifier = Modifier.size(30.dp)) }
    }
}

private enum class Dir(val key: String) { UP(Keys.UP), DOWN(Keys.DOWN), LEFT(Keys.LEFT), RIGHT(Keys.RIGHT), OK(Keys.ENTER) }

/** Anel direcional com OK no centro: toque em cada quadrante envia a seta; segurar repete. */
@Composable
fun DPad(press: (String) -> Unit, size: Dp = 264.dp) {
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<Dir?>(null) }
    val cur by rememberUpdatedState(press)
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(C.button, C.surface))).border(1.dp, C.outline, CircleShape)
            .semantics { contentDescription = "Setas direcionais e OK" }
            .pointerInput(Unit) {
                val w = this.size.width.toFloat()
                val h = this.size.height.toFloat()
                detectTapGestures(onPress = { pos ->
                    val c = Offset(w / 2f, h / 2f)
                    val dx = pos.x - c.x
                    val dy = pos.y - c.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val d = when {
                        dist < w * 0.20f -> Dir.OK
                        abs(dx) > abs(dy) -> if (dx > 0) Dir.RIGHT else Dir.LEFT
                        else -> if (dy > 0) Dir.DOWN else Dir.UP
                    }
                    active = d
                    cur(d.key)
                    val job = if (d != Dir.OK) scope.launch { delay(420); while (true) { cur(d.key); delay(110) } } else null
                    try { tryAwaitRelease() } finally { job?.cancel(); active = null }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2f
            // Centro (OK)
            drawCircle(if (active == Dir.OK) C.buttonPressed else Color(0xFF0E0E11), radius = r * 0.40f, center = center)
            drawCircle(C.outline, radius = r * 0.40f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            // Destaque do quadrante pressionado
            active?.takeIf { it != Dir.OK }?.let {
                val o = when (it) { Dir.UP -> Offset(0f, -r * 0.70f); Dir.DOWN -> Offset(0f, r * 0.70f); Dir.LEFT -> Offset(-r * 0.70f, 0f); else -> Offset(r * 0.70f, 0f) }
                drawCircle(C.buttonPressed, radius = r * 0.24f, center = center + o)
            }
        }
        Text("OK", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.KeyboardArrowUp, null, tint = C.text, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(36.dp))
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = C.text, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(36.dp))
        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = C.text, modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(36.dp))
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = C.text, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(36.dp))
    }
}

/** Touchpad opcional: arrastar = setas (repete conforme a distância), toque = OK. */
@Composable
fun TouchPad(press: (String) -> Unit, size: Dp = 264.dp) {
    val cur by rememberUpdatedState(press)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(36.dp)).background(Brush.verticalGradient(listOf(C.button, C.surface))).border(1.dp, C.outline, RoundedCornerShape(36.dp))
            .semantics { contentDescription = "Touchpad: arraste para mover, toque para OK" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var acc = Offset.Zero
                    var moved = false
                    val step = 46f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val d = ch.positionChange()
                        acc += d
                        if (d != Offset.Zero) ch.consume()
                        while (abs(acc.x) >= step || abs(acc.y) >= step) {
                            moved = true
                            if (abs(acc.x) >= abs(acc.y)) {
                                cur(if (acc.x > 0) Keys.RIGHT else Keys.LEFT); acc = acc.copy(x = acc.x - step * (if (acc.x > 0) 1 else -1))
                            } else {
                                cur(if (acc.y > 0) Keys.DOWN else Keys.UP); acc = acc.copy(y = acc.y - step * (if (acc.y > 0) 1 else -1))
                            }
                        }
                    }
                    if (!moved) cur(Keys.ENTER)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Arraste para navegar\nToque para OK", color = C.textDim, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Aba "Teclas": números, mídia, cores e atalhos extras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeysPage(send: (String) -> Unit, tick: () -> Unit) {
    val press: (String) -> Unit = { tick(); send(it) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("Números")
        val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                r.forEach { n -> TextKey("$n", { press(Keys.digit(n)) }, Modifier.width(88.dp).height(56.dp), "Número $n", fontSize = 22) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TextKey("−", { press(Keys.DASH) }, Modifier.width(88.dp).height(56.dp), "Traço (canal de 3 dígitos)", fontSize = 22)
            TextKey("0", { press(Keys.digit(0)) }, Modifier.width(88.dp).height(56.dp), "Número 0", fontSize = 22)
            TextKey("Lista", { press(Keys.CH_LIST) }, Modifier.width(88.dp).height(56.dp), "Lista de canais", fontSize = 15)
        }
        SectionLabel("Mídia")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconKey(Icons.Filled.FastRewind, "Retroceder", { press(Keys.REWIND) }, size = 52.dp)
            IconKey(Icons.Filled.PlayArrow, "Reproduzir", { press(Keys.PLAY) }, size = 52.dp)
            IconKey(Icons.Filled.Pause, "Pausar", { press(Keys.PAUSE) }, size = 52.dp)
            IconKey(Icons.Filled.Stop, "Parar", { press(Keys.STOP) }, size = 52.dp)
            IconKey(Icons.Filled.FastForward, "Avançar", { press(Keys.FF) }, size = 52.dp)
            IconKey(Icons.Filled.FiberManualRecord, "Gravar", { press(Keys.REC) }, size = 52.dp, tint = C.power)
        }
        SectionLabel("Teclas coloridas")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Triple("Vermelho", C.red, Keys.RED), Triple("Verde", C.green, Keys.GREEN), Triple("Amarelo", C.yellow, Keys.YELLOW), Triple("Azul", C.blue, Keys.BLUE)).forEach { (n, c, k) ->
                PressButton("Tecla $n", { press(k) }, Modifier.width(72.dp).height(36.dp), RoundedCornerShape(18.dp), c) {}
            }
        }
        SectionLabel("Atalhos")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chips = listOf(
                "Smart Hub" to Keys.SMART_HUB, "Tools" to Keys.TOOLS, "HDMI" to Keys.HDMI, "Canal anterior" to Keys.PRE_CH,
                "Legendas" to Keys.SUBTITLE, "Teletexto" to Keys.TELETEXT, "Proporção" to Keys.ASPECT, "Timer" to Keys.SLEEP,
                "Guia" to Keys.GUIDE, "Info" to Keys.INFO, "Menu" to Keys.MENU, "Fonte" to Keys.SOURCE,
            )
            chips.forEach { (n, k) -> TextKey(n, { press(k) }, Modifier.height(44.dp).width(if (n.length > 9) 132.dp else 104.dp), n, fontSize = 14) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = C.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
}
