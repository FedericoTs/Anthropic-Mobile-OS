package os.amos.shell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AMOS house palette: near-black canvas, warm amber accent.
private val Accent = Color(0xFFD4A27F)
private val Canvas = Color(0xFF0B0B0F)
private val Surface = Color(0xFF15151B)
private val OnCanvas = Color(0xFFEDEAE3)

private val AmosDark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1206),
    background = Canvas,
    onBackground = OnCanvas,
    surface = Surface,
    onSurface = OnCanvas,
    surfaceVariant = Color(0xFF22222B),
    onSurfaceVariant = Color(0xFFB8B4AC),
)

private val AmosLight = lightColorScheme(
    primary = Color(0xFF9A5B33),
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun AmosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AmosDark else AmosLight,
        content = content,
    )
}
