package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.geracaoai.controlesamsung.KeyboardState

/**
 * Teclado do celular para digitar na TV: cada alteração substitui o texto do campo da TV
 * (SendInputString em Base64). Abre sozinho quando a TV avisa (ms.remote.imeStart).
 */
@Composable
fun KeyboardOverlay(kb: KeyboardState, onText: (String) -> Unit, onEnter: () -> Unit, onClose: () -> Unit) {
    var tf by remember(kb) { mutableStateOf(TextFieldValue(kb.initialText, TextRange(kb.initialText.length))) }
    val focus = remember { FocusRequester() }
    val ime = LocalSoftwareKeyboardController.current
    LaunchedEffect(kb) { focus.requestFocus(); ime?.show() }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(C.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (kb.fromTv) "A TV pediu texto" else "Teclado", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("O que você digitar aparece na TV. Clique no campo de busca da TV antes, se ainda não clicou.", color = C.textDim, fontSize = 13.sp)
            OutlinedTextField(
                value = tf,
                onValueChange = { tf = it; onText(it.text) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Digite aqui…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEnter(); onClose() }, onDone = { onEnter(); onClose() }, onGo = { onEnter(); onClose() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { tf = TextFieldValue(""); onText("") }, modifier = Modifier.weight(1f)) { Text("Limpar") }
                Button(onClick = { onEnter(); onClose() }, colors = ButtonDefaults.buttonColors(containerColor = C.accent), modifier = Modifier.weight(1f)) { Text("Buscar / Enter") }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Fechar") }
            }
        }
    }
}
