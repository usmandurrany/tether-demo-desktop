package io.github.usmandurrany.tether.demo.ui.chats

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.ui.Times
import io.github.usmandurrany.tether.demo.ui.components.Avatar
import io.github.usmandurrany.tether.demo.ui.components.ConfirmDialog
import io.github.usmandurrany.tether.demo.ui.components.EmptyState
import io.github.usmandurrany.tether.demo.ui.components.TextInputDialog
import io.github.usmandurrany.tether.demo.ui.components.ToolButton
import io.github.usmandurrany.tether.demo.ui.components.presenceColor
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons

/** Below this width the chat list and the open chat take turns, like on a phone. */
private val TwoPaneWidth = 720.dp

/** The chat list beside the open chat, or one at a time in a narrow window. */
@Composable
fun ChatsScreen(session: DemoSession) {
    val vm = viewModel { ChatsViewModel(session) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneWidth
        val listWidth = (maxWidth * 0.32f).coerceIn(280.dp, 380.dp)
        val list: @Composable (Modifier) -> Unit = { m -> ChatList(state, vm, onDialog = { dialog = it }, modifier = m) }
        val conversation: @Composable (Modifier) -> Unit = { m ->
            val open = state.open
            if (open != null) {
                Conversation(state, open, vm, showBack = !twoPane, onDialog = { dialog = it }, modifier = m)
            } else {
                Box(m.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    EmptyState(AppIcons.Chat, "Pick a chat", "Choose someone on the left, or add a contact with +.")
                }
            }
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                conversation(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (state.open != null) {
            conversation(Modifier.fillMaxSize())
        } else {
            list(Modifier.fillMaxSize())
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
    dialog?.let { ChatDialogs(it, vm, onDismiss = { dialog = null }) }
}

// --- The list ------------------------------------------------------------------------

@Composable
private fun ChatList(state: ChatsViewModel.State, vm: ChatsViewModel, onDialog: (ChatDialog) -> Unit, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ToolButton(AppIcons.Plus, "Add a contact", { onDialog(ChatDialog.AddContact) })
        }
        SearchField(state.query, vm::search, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        state.requests.forEach { RequestCard(it, vm) }
        Spacer(Modifier.height(6.dp))
        when {
            !state.anyChats -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(AppIcons.Chat, "No chats yet", "Add someone by their address to start chatting.") {
                    Button(onClick = { onDialog(ChatDialog.AddContact) }) { Text("Add contact") }
                }
            }
            state.chats.isEmpty() -> Text(
                "No chats match “${state.query.trim()}”.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> Box(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    items(state.chats, key = { it.jid.toString() }) { item ->
                        ContextMenuArea(items = { contactMenu(item, vm, onDialog) }) {
                            ChatRow(item, selected = item.jid == state.openJid, onClick = { vm.open(item.jid) })
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    TextField(
        value, onChange,
        placeholder = { Text("Search") },
        leadingIcon = { Icon(AppIcons.Search, null, Modifier.size(18.dp)) },
        trailingIcon = if (value.isNotEmpty()) ({ ToolButton(AppIcons.Close, "Clear", { onChange("") }) }) else null,
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth().height(48.dp),
    )
}

@Composable
private fun RequestCard(request: ContactRequest, vm: ChatsViewModel) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${request.from} wants to add you", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                request.note?.let { "“$it”" } ?: "They'll see when you're online, and you'll see them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = { vm.answer(request.from, true) }, contentPadding = ButtonDefaults.SmallButtonContentPadding, modifier = Modifier.height(34.dp)) { Text("Accept") }
                OutlinedButton(onClick = { vm.answer(request.from, false) }, contentPadding = ButtonDefaults.SmallButtonContentPadding, modifier = Modifier.height(34.dp)) { Text("Decline") }
            }
        }
    }
}

/** One chat: picture with presence, name and time, a preview line, and the unread count. */
@Composable
private fun ChatRow(item: ChatItem, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background)
            .hoverable(hover).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(item.avatar, item.name, 46.dp, presence = presenceColor(item.show, item.online), ring = if (background == Color.Transparent) MaterialTheme.colorScheme.surface else background)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                item.lastMessage?.let {
                    Text(
                        Times.stamp(it.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (item.unread > 0) FontWeight.Bold else null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (text, accent) = when {
                    item.typing -> "typing…" to true
                    item.lastMessage != null -> preview(item.lastMessage) to false
                    else -> item.presence to false
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (item.typing) FontStyle.Italic else null,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.unread > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).height(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (item.unread > 99) "99+" else "${item.unread}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/** Rename, presence sharing and removal, from a right-click or the ⋯ in the chat header. */
fun contactMenu(item: ChatItem, vm: ChatsViewModel, onDialog: (ChatDialog) -> Unit) = listOfNotNull(
    ContextMenuItem(if (item.contact == null) "Add to contacts…" else "Rename…") { onDialog(ChatDialog.Rename(item)) },
    ContextMenuItem("Ask to see their presence") { vm.askForPresence(item.jid) },
    ContextMenuItem("Share my presence") { vm.sharePresence(item.jid) },
    ContextMenuItem("Stop sharing my presence") { vm.stopSharingPresence(item.jid) },
    item.contact?.let { ContextMenuItem("Remove contact…") { onDialog(ChatDialog.Remove(item)) } },
)

/** The same actions as [contactMenu], as a dropdown under a button. */
@Composable
fun ContactDropdown(item: ChatItem, vm: ChatsViewModel, expanded: Boolean, onDismiss: () -> Unit, onDialog: (ChatDialog) -> Unit) {
    @Composable
    fun entry(text: String, icon: ImageVector?, danger: Boolean = false, action: () -> Unit) {
        val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        DropdownMenuItem(
            text = { Text(text, color = color) },
            leadingIcon = { if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = color) else Spacer(Modifier.size(18.dp)) },
            onClick = { action(); onDismiss() },
        )
    }
    DropdownMenu(expanded, onDismiss) {
        entry(if (item.contact == null) "Add to contacts…" else "Rename…", if (item.contact == null) AppIcons.Plus else AppIcons.Edit) { onDialog(ChatDialog.Rename(item)) }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        entry("Ask to see their presence", null) { vm.askForPresence(item.jid) }
        entry("Share my presence", null) { vm.sharePresence(item.jid) }
        entry("Stop sharing my presence", null) { vm.stopSharingPresence(item.jid) }
        if (item.contact != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            entry("Remove contact…", AppIcons.Trash, danger = true) { onDialog(ChatDialog.Remove(item)) }
        }
    }
}

// --- Dialogs -------------------------------------------------------------------------

/** The dialogs the chat screen can open. */
sealed interface ChatDialog {
    data object AddContact : ChatDialog
    data class Rename(val item: ChatItem) : ChatDialog
    data class Remove(val item: ChatItem) : ChatDialog
    data class DeleteForEveryone(val message: io.github.usmandurrany.tether.im.Message) : ChatDialog
    data object Photo : ChatDialog
    data object Video : ChatDialog
    data object Location : ChatDialog
    data object Custom : ChatDialog
}

@Composable
private fun ChatDialogs(dialog: ChatDialog, vm: ChatsViewModel, onDismiss: () -> Unit) {
    when (dialog) {
        ChatDialog.AddContact -> AddContactDialog(vm, onDismiss)
        is ChatDialog.Rename -> TextInputDialog(
            if (dialog.item.contact == null) "Add to contacts" else "Rename", "Name for ${dialog.item.jid}", dialog.item.contact?.name.orEmpty(), "Save",
            onDismiss = onDismiss, supporting = "Leave empty to use the name in their profile.",
        ) { vm.rename(dialog.item.jid, it) }
        is ChatDialog.Remove -> ConfirmDialog(
            "Remove contact", "Remove ${dialog.item.name} from your contacts? Your chat history stays.", "Remove", onDismiss, danger = true,
        ) { vm.removeContact(dialog.item.jid) }
        is ChatDialog.DeleteForEveryone -> ConfirmDialog(
            "Delete for everyone", "Delete this message for everyone in the chat?", "Delete", onDismiss, danger = true,
        ) { vm.deleteForEveryone(dialog.message) }
        ChatDialog.Photo -> PhotoDialog(vm, onDismiss)
        ChatDialog.Video -> VideoDialog(vm, onDismiss)
        ChatDialog.Location -> LocationDialog(vm, onDismiss)
        ChatDialog.Custom -> CustomDataDialog(vm, onDismiss)
    }
}
