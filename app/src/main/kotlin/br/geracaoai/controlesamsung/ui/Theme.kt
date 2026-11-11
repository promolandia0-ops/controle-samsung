package br.geracaoai.controlesamsung.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val bg = Color(0xFF08080A)
    val surface = Color(0xFF17171B)
    val button = Color(0xFF232328)
    val buttonPressed = Color(0xFF3A3A42)
    val outline = Color(0xFF2E2E35)
    val text = Color(0xFFF2F2F5)
    val textDim = Color(0xFF9A9AA6)
    val accent = Color(0xFF4C8DFF)
    val power = Color(0xFFE5484D)
    val ok = Color(0xFF35C46A)
    val warn = Color(0xFFFFB84D)
    val red = Color(0xFFE5484D)
    val green = Color(0xFF35C46A)
    val yellow = Color(0xFFF5C542)
    val blue = Color(0xFF4C8DFF)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.accent, onPrimary = Color.White,
        background = C.bg, onBackground = C.text,
        surface = C.surface, onSurface = C.text,
        surfaceVariant = C.button, onSurfaceVariant = C.textDim,
        secondaryContainer = C.button, onSecondaryContainer = C.text,
        error = C.power, outline = C.outline,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
