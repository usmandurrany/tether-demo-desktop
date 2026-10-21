package io.github.usmandurrany.tether.demo.ui.chats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.demo.ui.Times
import io.github.usmandurrany.tether.demo.ui.components.Avatar
import io.github.usmandurrany.tether.demo.ui.components.RemoteImage
import io.github.usmandurrany.tether.demo.ui.components.ToolButton
import io.github.usmandurrany.tether.demo.ui.components.onEnter
import io.github.usmandurrany.tether.demo.ui.components.presenceColor
import io.github.usmandurrany.tether.demo.ui.components.rememberRemoteImage
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.MonoStyle
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageStatus
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** The open chat: a header, the messages, and the composer. */
@Composable
fun Conversation(
    state: ChatsViewModel.State,
    item: ChatItem,
    actions: ChatsActions,
    showBack: Boolean,
    onDialog: (ChatDialog) -> Unit,
    modifier: Modifier,
    menuOpenFor: String? = null,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Header(item, actions, showBack, onDialog)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        key(item.jid) {
            Messages(state, actions, onDialog, Modifier.weight(1f), menuOpenFor)
            Composer(item, actions, onDialog)
        }
    }
}

@Composable
private fun Header(item: ChatItem, actions: ChatsActions, showBack: Boolean, onDialog: (ChatDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (showBack) 8.dp else 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showBack) ToolButton(AppIcons.Back, "Back to chats", actions::close)
            Avatar(item.avatar, item.name, 42.dp, presence = presenceColor(item.show, item.online))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = if (item.typing) "typing…" else listOf(item.presence, item.jid.toString()).distinct().joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.typing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = if (item.typing) FontStyle.Italic else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                ToolButton(AppIcons.More, "Contact options", { menu = true })
                ContactDropdown(item, actions, menu, onDismiss = { menu = false }, onDialog = onDialog)
            }
        }
    }
}

/** What the message list shows: a day separator or a message. */
private sealed interface Entry {
    val key: String

    data class Day(val label: String, val at: Long) : Entry {
        override val key get() = "day/$at"
    }

    data class Bubble(val message: Message, val first: Boolean) : Entry {
        override val key get() = (if (message.outgoing) "o/" else "i/") + message.id
    }
}

/** Newest at the bottom; a separator for each day; consecutive messages from one side grouped closer. */
private fun entries(messages: List<Message>): List<Entry> {
    val out = ArrayList<Entry>(messages.size + 8)
    var previous: Message? = null
    for (m in messages) {
        val newDay = previous == null || !Times.sameDay(previous.timestamp, m.timestamp)
        if (newDay) out += Entry.Day(Times.day(m.timestamp), m.timestamp)
        val grouped = !newDay && previous!!.outgoing == m.outgoing && m.timestamp - previous.timestamp < 5 * 60_000
        out += Entry.Bubble(m, first = !grouped)
        previous = m
    }
    return out.asReversed()
}

@Composable
private fun Messages(state: ChatsViewModel.State, actions: ChatsActions, onDialog: (ChatDialog) -> Unit, modifier: Modifier, menuOpenFor: String?) {
    val list = rememberLazyListState()
    val items = remember(state.messages) { entries(state.messages) }
    val newest = state.messages.lastOrNull()
    // Follow new messages while at the bottom, and always after sending one.
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.outgoing || list.firstVisibleItemIndex <= 2)) list.animateScrollToItem(0)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bubbleMax = (maxWidth * 0.72f).coerceAtMost(560.dp).coerceAtLeast(minOf(maxWidth - 40.dp, 220.dp))
        LazyColumn(
            state = list,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.key }) { entry ->
                when (entry) {
                    is Entry.Day -> DayChip(entry.label)
                    is Entry.Bubble -> MessageRow(entry.message, entry.first, bubbleMax, actions, onDialog, menuInitiallyOpen = entry.message.id == menuOpenFor)
                }
            }
            if (state.messages.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No messages yet. Say hello.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.canLoadOlder) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = actions::loadOlder, enabled = !state.loadingOlder, shape = CircleShape) {
                            Icon(AppIcons.Clock, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.loadingOlder) "Loading…" else "Load older messages")
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight(), reverseLayout = true)
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** How a bubble is coloured. */
private enum class Look { IN, OUT, FAILED, DELETED }

/**
 * One message: theirs on the left, yours on the right in the accent colour.
 * Right-click it, or use the ⋯ that appears on hover, to copy or delete.
 */
@Composable
private fun MessageRow(
    m: Message,
    first: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    actions: ChatsActions,
    onDialog: (ChatDialog) -> Unit,
    menuInitiallyOpen: Boolean,
) {
    val look = when {
        m.deleted -> Look.DELETED
        m.outgoing && m.status == MessageStatus.FAILED -> Look.FAILED
        m.outgoing -> Look.OUT
        else -> Look.IN
    }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(menuInitiallyOpen) }
    val items = messageActions(m, actions, onDialog)

    Row(
        Modifier.fillMaxWidth().hoverable(hover).padding(top = if (first) 10.dp else 2.dp),
        horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val moreButton: @Composable () -> Unit = {
            Box(Modifier.alpha(if (hovered || menu) 1f else 0f)) {
                ToolButton(AppIcons.More, "More", { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenu(menu, { menu = false }) {
                    items.forEach { a ->
                        val color = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        DropdownMenuItem(
                            text = { Text(a.label, color = color) },
                            leadingIcon = { Icon(a.icon, null, Modifier.size(18.dp), tint = color) },
                            onClick = { a.run(); menu = false },
                        )
                    }
                }
            }
        }
        if (m.outgoing) moreButton()
        ContextMenuArea(items = { items.map { a -> ContextMenuItem(a.label, a.run) } }) {
            Bubble(m, look, first, Modifier.widthIn(max = maxWidth))
        }
        if (!m.outgoing) moreButton()
    }
}

private class Action(val label: String, val icon: ImageVector, val danger: Boolean = false, val run: () -> Unit)

private fun messageActions(m: Message, actions: ChatsActions, onDialog: (ChatDialog) -> Unit): List<Action> = buildList {
    if (!m.deleted && m.body.isNotBlank()) add(Action("Copy text", AppIcons.Copy) { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(m.body), null) })
    if (m.outgoing && m.status == MessageStatus.FAILED) add(Action("Try again", AppIcons.Refresh) { actions.retry(m) })
    if (m.deleted) {
        add(Action("Remove", AppIcons.Trash) { actions.deleteForMe(m) })
    } else {
        if (m.outgoing && m.status != MessageStatus.FAILED) add(Action("Delete for everyone", AppIcons.Trash, danger = true) { onDialog(ChatDialog.DeleteForEveryone(m)) })
        add(Action("Delete for me", AppIcons.Trash) { actions.deleteForMe(m) })
    }
}

@Composable
private fun Bubble(m: Message, look: Look, first: Boolean, modifier: Modifier) {
    val chat = TetherTheme.chat
    val container = when (look) {
        Look.IN -> chat.bubbleIn
        Look.OUT -> chat.bubbleOut
        Look.FAILED -> MaterialTheme.colorScheme.errorContainer
        Look.DELETED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (look == Look.OUT) chat.onBubbleOut else MaterialTheme.colorScheme.onSurface
    val muted = if (look == Look.OUT) chat.onBubbleOutMuted else MaterialTheme.colorScheme.onSurfaceVariant
    // A small tail corner on the first bubble of a group.
    val shape = if (!first) RoundedCornerShape(18.dp) else if (m.outgoing) RoundedCornerShape(18.dp, 18.dp, 18.dp, 18.dp).copy(topEnd = androidx.compose.foundation.shape.CornerSize(6.dp))
    else RoundedCornerShape(18.dp).copy(topStart = androidx.compose.foundation.shape.CornerSize(6.dp))
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        border = if (look == Look.IN) BorderStroke(1.dp, chat.bubbleInBorder) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m.deleted) {
                Text("This message was deleted", fontStyle = FontStyle.Italic, color = muted, style = MaterialTheme.typography.bodyLarge)
            } else {
                val contents = Attachments.of(m)
                contents.forEach { Part(it, look == Look.OUT, muted) }
                // A caption that only repeats the link (sent by an app without one) is not shown twice.
                val caption = m.body.takeIf { it.isNotBlank() && contents.none { c -> m.body == fallbackOf(c) } }
                caption?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            }
            Meta(m, look, muted, Modifier.align(Alignment.End))
        }
    }
}

private fun fallbackOf(content: Attachments.Content): String? = when (content) {
    is Attachments.Photo -> content.url
    is Attachments.Video -> content.url
    is Attachments.Location -> Attachments.fallbackText(Attachments.location(content.latitude, content.longitude, null))
    is Attachments.Other -> null
}

/** Time, "from another device", and the ticks: one, two, or two coloured once read. */
@Composable
private fun Meta(m: Message, look: Look, muted: Color, modifier: Modifier) {
    val text = listOfNotNull(
        if (look == Look.FAILED) "Not delivered" + (m.error?.let { " ($it)" } ?: "") else null,
        "from another device".takeIf { m.fromOtherDevice },
        Times.clock(m.timestamp),
    ).joinToString(" · ")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (look == Look.FAILED) MaterialTheme.colorScheme.error else muted)
        if (look == Look.OUT) Ticks(m.status, if (m.status == MessageStatus.READ) TetherTheme.chat.read else muted)
    }
}

@Composable
private fun Ticks(status: MessageStatus, color: Color) {
    val count = when (status) {
        MessageStatus.SENT -> 1
        MessageStatus.DELIVERED, MessageStatus.READ -> 2
        else -> 0
    }
    if (status == MessageStatus.SENDING) {
        Icon(AppIcons.Clock, "Sending", Modifier.size(12.dp), tint = color)
        return
    }
    if (count == 0) return
    Canvas(Modifier.size(width = if (count == 2) 17.dp else 12.dp, height = 10.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun check(dx: Float) {
            val p = Path().apply {
                moveTo(dx + 0.5.dp.toPx(), size.height * 0.55f)
                lineTo(dx + 3.5.dp.toPx(), size.height - 1.dp.toPx())
                lineTo(dx + 11.dp.toPx(), 1.dp.toPx())
            }
            drawPath(p, color, style = stroke)
        }
        check(0f)
        if (count == 2) check(5.dp.toPx())
    }
}

@Composable
private fun Part(content: Attachments.Content, onAccent: Boolean, muted: Color) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }
    when (content) {
        is Attachments.Photo -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Photo(content) { open(content.url) }
            val info = listOfNotNull(
                if (content.width != null && content.height != null) "${content.width}×${content.height}" else null,
                Attachments.size(content.bytes),
            ).joinToString(" · ")
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.labelSmall, color = muted)
        }
        is Attachments.Video -> Tile(
            AppIcons.Video, "Play video",
            listOfNotNull(Attachments.duration(content.seconds), Attachments.size(content.bytes)).joinToString(" · ").ifEmpty { "Opens in your browser" },
            onAccent, muted,
        ) { open(content.url) }
        is Attachments.Location -> Tile(AppIcons.Pin, content.label ?: "Shared location", "%.5f, %.5f".format(content.latitude, content.longitude), onAccent, muted) {
            open(content.mapUrl)
        }
        is Attachments.Other -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(AppIcons.Data, "Custom data", content.data.type, onAccent, muted, onClick = null)
            val fields = content.data.fields.entries.joinToString("\n") { (k, v) -> "$k = $v" }
            if (fields.isNotEmpty()) SelectionContainer { Text(fields, style = MonoStyle) }
        }
    }
}

/** A photo, rounded, sized from the sender's dimensions before it loads. Click opens it. */
@Composable
private fun Photo(photo: Attachments.Photo, onClick: () -> Unit) {
    val image = rememberRemoteImage(photo.url)
    val ratio = when {
        image is RemoteImage.Loaded -> image.image.width.toFloat() / image.image.height
        photo.width != null && photo.height != null && photo.width > 0 && photo.height > 0 -> photo.width.toFloat() / photo.height
        else -> 1.5f
    }.coerceIn(0.5f, 2.5f)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.widthIn(max = 320.dp).width(320.dp).aspectRatio(ratio).heightIn(max = 320.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        when (image) {
            is RemoteImage.Loaded -> Image(image.image, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            RemoteImage.Loading -> Icon(AppIcons.Photo, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            RemoteImage.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(AppIcons.Photo, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Couldn't load the photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A link-like card inside a bubble: an icon, a title and a detail line. */
@Composable
private fun Tile(icon: ImageVector, title: String, detail: String, onAccent: Boolean, muted: Color, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(if (onAccent) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .let { if (onClick != null) it.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else it }
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (onAccent) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

/**
 * Where you write: attach, a text box that grows up to six lines (Enter
 * sends, Shift+Enter starts a new line) and a round send button.
 */
@Composable
private fun Composer(item: ChatItem, actions: ChatsActions, onDialog: (ChatDialog) -> Unit) {
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val send = {
        val t = text.trim()
        if (t.isNotEmpty()) {
            actions.send(t)
            text = ""
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    ToolButton(AppIcons.Attach, "Attach a photo, video, location or custom data", { attach = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(attach, { attach = false }) {
                        @Composable
                        fun entry(label: String, icon: ImageVector, dialog: ChatDialog) = DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = { attach = false; onDialog(dialog) },
                        )
                        entry("Photo…", AppIcons.Photo, ChatDialog.Photo)
                        entry("Video…", AppIcons.Video, ChatDialog.Video)
                        entry("Location…", AppIcons.Pin, ChatDialog.Location)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        entry("Custom data…", AppIcons.Data, ChatDialog.Custom)
                    }
                }
                TextField(
                    text,
                    { new ->
                        if (new.length > text.length) actions.typing()
                        text = new
                    },
                    placeholder = { Text("Message ${item.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).focusRequester(focus).onEnter(send),
                )
                FilledIconButton(
                    onClick = send,
                    enabled = text.isNotBlank(),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(bottom = 2.dp).size(42.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(AppIcons.Send, "Send", Modifier.size(20.dp).padding(start = 2.dp))
                }
            }
        }
    }
}
