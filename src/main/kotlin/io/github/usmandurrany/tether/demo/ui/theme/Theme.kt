package io.github.usmandurrany.tether.demo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The indigo accent, used for your bubbles, the selected tab and primary buttons. */
private val Indigo = Color(0xFF5B5BD6)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E9FB),
    onPrimaryContainer = Color(0xFF2E2E8F),
    secondaryContainer = Color(0xFFE2E2FA),
    onSecondaryContainer = Color(0xFF2E2E8F),
    background = Color(0xFFF3F4F8),
    onBackground = Color(0xFF1C1E24),
    surface = Color.White,
    onSurface = Color(0xFF1C1E24),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEDEEF4),
    surfaceContainerHigh = Color(0xFFE8E9EF),
    surfaceContainerHighest = Color(0xFFE2E4EB),
    outline = Color(0xFFC9CCD6),
    outlineVariant = Color(0xFFE2E4EB),
    error = Color(0xFFD03B3B),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF8A1F1F),
    inverseSurface = Color(0xFF2A2C33),
    inverseOnSurface = Color(0xFFF1F2F6),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA9AAFF),
    onPrimary = Color(0xFF1E1F66),
    primaryContainer = Color(0xFF2E2F5C),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondaryContainer = Color(0xFF34356A),
    onSecondaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFF16171B),
    onBackground = Color(0xFFE7E8EC),
    surface = Color(0xFF1E2025),
    onSurface = Color(0xFFE7E8EC),
    surfaceVariant = Color(0xFF272A31),
    onSurfaceVariant = Color(0xFF9AA0AB),
    surfaceContainerLowest = Color(0xFF111215),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF131418),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF30333B),
    outline = Color(0xFF4A4E58),
    outlineVariant = Color(0xFF2D3038),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C1111),
    errorContainer = Color(0xFF3D2124),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7E8EC),
    inverseOnSurface = Color(0xFF1E2025),
)

/** Colours Material 3 has no role for: chat bubbles and presence. */
@Immutable
data class ChatColors(
    val bubbleIn: Color,
    val bubbleInBorder: Color,
    val bubbleOut: Color,
    val onBubbleOut: Color,
    val onBubbleOutMuted: Color,
    val read: Color,
    val online: Color,
    val away: Color,
    val busy: Color,
    val offline: Color,
    val warningContainer: Color,
)

private val LightChat = ChatColors(
    bubbleIn = Color.White,
    bubbleInBorder = Color(0xFFE4E6EC),
    bubbleOut = Indigo,
    onBubbleOut = Color.White,
    onBubbleOutMuted = Color.White.copy(alpha = 0.76f),
    read = Color(0xFFB7F7D0),
    online = Color(0xFF22A559),
    away = Color(0xFFE39A1A),
    busy = Color(0xFFDC4848),
    offline = Color(0xFF9CA3AF),
    warningContainer = Color(0xFFFFF4E0),
)

private val DarkChat = LightChat.copy(
    bubbleIn = Color(0xFF272A31),
    bubbleInBorder = Color(0xFF272A31),
    bubbleOut = Color(0xFF5050C8),
    read = Color(0xFF9BF0BE),
    warningContainer = Color(0xFF3A2F1C),
)

private val LocalChatColors = staticCompositionLocalOf { LightChat }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = t.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The app's Material 3 theme, light or dark. */
@Composable
fun TetherTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChatColors provides if (dark) DarkChat else LightChat) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Access as `TetherTheme.chat.bubbleOut` and so on. */
object TetherTheme {
    val chat: ChatColors
        @Composable @ReadOnlyComposable get() = LocalChatColors.current
}

val ColorScheme.isDark: Boolean get() = background.luminanceApprox() < 0.5f

private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** For logs and protocol details. */
val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
