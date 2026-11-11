package br.geracaoai.controlesamsung.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vibração curta nos botões (respeita a opção do app). */
class Haptics(context: Context) {
    private val vib: Vibrator? = context.applicationContext.getSystemService(Vibrator::class.java)
    var enabled: Boolean = true
    fun tick() {
        if (!enabled) return
        try { vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) } catch (_: Exception) {}
    }
}

/**
 * Botão de controle: dispara ao tocar e, se [repeat], repete enquanto segurado (volume, canal, setas).
 */
@Composable
fun PressButton(
    description: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = C.button,
    repeat: Boolean = false,
    content: @Composable () -> Unit,
) {
    val current by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) C.buttonPressed else color)
            .semantics { contentDescription = description; role = Role.Button; onClick { current(); true } }
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    current()
                    val job = if (repeat) scope.launch { delay(420); while (true) { current(); delay(110) } } else null
                    try { tryAwaitRelease() } finally { job?.cancel(); pressed = false }
                })
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun IconKey(
    icon: ImageVector, description: String, onPress: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 56.dp, color: Color = C.button, tint: Color = C.text, repeat: Boolean = false, iconSize: Dp = 26.dp,
) {
    PressButton(description, onPress, modifier.size(size), CircleShape, color, repeat) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun TextKey(
    label: String, onPress: () -> Unit, modifier: Modifier = Modifier, description: String = label,
    color: Color = C.button, textColor: Color = C.text, fontSize: Int = 16, corner: Dp = 18.dp,
) {
    PressButton(description, onPress, modifier, RoundedCornerShape(corner), color) {
        Text(label, color = textColor, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FullBox(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize()) { content() } }
