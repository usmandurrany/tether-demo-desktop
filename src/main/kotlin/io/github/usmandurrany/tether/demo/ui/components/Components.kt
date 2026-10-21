package io.github.usmandurrany.tether.demo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.usmandurrany.tether.demo.ui.Health
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Decodes pictures once, by content or link, and keeps them for the session. */
object Images {
    private val avatars = ConcurrentHashMap<String, ImageBitmap>()
    private val photos = ConcurrentHashMap<String, ImageBitmap>()

    fun avatar(avatar: Avatar): ImageBitmap? = avatars[avatar.hash] ?: decode(avatar.data)?.also { avatars[avatar.hash] = it }

    fun cachedPhoto(url: String): ImageBitmap? = photos[url]

    /** Fetches a photo over http(s); null when it can't be read. */
    suspend fun photo(url: String): ImageBitmap? = photos[url] ?: withContext(Dispatchers.IO) {
        runCatching {
            val u = URI(url).toURL()
            require(u.protocol == "https" || u.protocol == "http")
            u.openStream().use { decode(it.readNBytes(20_000_000)) }
        }.getOrNull()
    }?.also { photos[url] = it }

    /** Puts a photo in the cache, so screenshots need no network. */
    internal fun preload(url: String, bytes: ByteArray) {
        decode(bytes)?.let { photos[url] = it }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

/** A photo from a link: loading, loaded, or failed. */
sealed interface RemoteImage {
    data object Loading : RemoteImage
    data class Loaded(val image: ImageBitmap) : RemoteImage
    data object Failed : RemoteImage
}

@Composable
fun rememberRemoteImage(url: String): RemoteImage {
    val state by produceState<RemoteImage>(Images.cachedPhoto(url)?.let { RemoteImage.Loaded(it) } ?: RemoteImage.Loading, url) {
        if (value is RemoteImage.Loading) value = Images.photo(url)?.let { RemoteImage.Loaded(it) } ?: RemoteImage.Failed
    }
    return state
}

/** The presence dot's colour. */
@Composable
fun presenceColor(show: Show?, online: Boolean): Color = when {
    !online -> TetherTheme.chat.offline
    show == Show.AWAY || show == Show.XA -> TetherTheme.chat.away
    show == Show.DND -> TetherTheme.chat.busy
    else -> TetherTheme.chat.online
}

@Composable
fun healthColor(health: Health): Color = when (health) {
    Health.GOOD -> TetherTheme.chat.online
    Health.TRYING -> TetherTheme.chat.away
    Health.OFF -> TetherTheme.chat.offline
    Health.BAD -> TetherTheme.chat.busy
}

/**
 * A round profile picture, or the name's initial on a colour picked from the
 * name. [presence] adds a dot in the corner, ringed in [ring] (the background).
 */
@Composable
fun Avatar(avatar: Avatar?, name: String, size: Dp, modifier: Modifier = Modifier, presence: Color? = null, ring: Color = MaterialTheme.colorScheme.surface) {
    Box(modifier.size(size)) {
        val image = avatar?.let(Images::avatar)
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            val hue = (name.lowercase().hashCode() and 0xffff) / 65535f * 360f
            Box(
                Modifier.size(size).clip(CircleShape).background(
                    Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.86f), Color.hsv((hue + 24) % 360, 0.55f, 0.70f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
        }
        if (presence != null) {
            val dot = (size * 0.28f).coerceAtLeast(10.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).size(dot).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(presence),
            )
        }
    }
}

/** A small filled circle. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** The app's mark: a rounded accent square with a chat glyph. */
@Composable
fun BrandMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFF7C7BF2), Color(0xFF4544B8)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Chat, contentDescription = "Tether", tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** An icon button with a tooltip, as on every desktop toolbar. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ToolButton(icon: ImageVector, tooltip: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = LocalContentColor.current) {
    TooltipArea(
        tooltip = { Tooltip(tooltip) },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 20.dp)),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier.pointerHoverIcon(PointerIcon.Hand)) {
            Icon(icon, contentDescription = tooltip, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun Tooltip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
        Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/** A titled card for settings-like screens. */
@Composable
fun SectionCard(title: String, subtitle: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

/** A heading for a whole screen. */
@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A rounded tag: tinted green for on, grey for off. */
@Composable
fun Pill(text: String, on: Boolean) {
    val base = if (on) TetherTheme.chat.online else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = base,
        modifier = Modifier.clip(CircleShape).background(base.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A big glyph in a soft accent circle with a title and a line of help, for empty screens. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.widthIn(max = 360.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

/** A label and its value on one line, with a hairline under it. */
@Composable
fun InfoRow(label: String, last: Boolean = false, value: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            value()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** Asks a yes/no question. */
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, danger: Boolean = false, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for one line of text. */
@Composable
fun TextInputDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, supporting: String? = null, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it },
                label = { Text(label) },
                supportingText = supporting?.let { s -> { Text(s) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onEnter { onConfirm(text); onDismiss() },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Runs [action] when Enter (without Shift) is pressed in this field. */
fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.key == Key.Enter && !e.isShiftPressed && e.type == KeyEventType.KeyDown) {
        action()
        true
    } else {
        false
    }
}

/** A thin outline around a box, in the theme's border colour. */
@Composable
fun Modifier.hairline(shape: androidx.compose.ui.graphics.Shape): Modifier = border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)

val ScreenPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp)
