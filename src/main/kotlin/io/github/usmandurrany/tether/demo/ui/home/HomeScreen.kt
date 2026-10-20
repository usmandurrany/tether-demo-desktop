package io.github.usmandurrany.tether.demo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.ui.chats.ChatsScreen
import io.github.usmandurrany.tether.demo.ui.components.Avatar
import io.github.usmandurrany.tether.demo.ui.components.BrandMark
import io.github.usmandurrany.tether.demo.ui.components.Dot
import io.github.usmandurrany.tether.demo.ui.components.TextInputDialog
import io.github.usmandurrany.tether.demo.ui.components.ToolButton
import io.github.usmandurrany.tether.demo.ui.components.healthColor
import io.github.usmandurrany.tether.demo.ui.describe
import io.github.usmandurrany.tether.demo.ui.log.LogScreen
import io.github.usmandurrany.tether.demo.ui.server.ServerScreen
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme

private enum class Destination(val label: String, val icon: ImageVector) {
    CHATS("Chats", AppIcons.Chat), SERVER("Server", AppIcons.Server), LOG("Log", AppIcons.Log)
}

/**
 * Everything shown while signed in: a navigation rail (Chats, Server, Log,
 * and you at the bottom) beside the chosen screen. A slim banner above the
 * screen says when the connection isn't online.
 */
@Composable
fun HomeScreen(
    session: DemoSession,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onDisconnect: () -> Unit,
    onLogOut: () -> Unit,
    onReconnect: () -> Unit,
    onShowFiles: () -> Unit,
    onQuit: () -> Unit,
) {
    val home = viewModel { HomeViewModel(session) }
    val state by home.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(Destination.CHATS) }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = { Box(Modifier.padding(top = 12.dp, bottom = 16.dp)) { BrandMark(36.dp) } },
            modifier = Modifier.fillMaxHeight(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Destination.entries.forEach { d ->
                    NavigationRailItem(
                        selected = d == destination,
                        onClick = { destination = d },
                        icon = {
                            BadgedBox(badge = { if (d == Destination.CHATS && state.unread > 0) Badge { Text(if (state.unread > 99) "99+" else "${state.unread}") } }) {
                                Icon(d.icon, contentDescription = d.label, modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text(d.label) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ToolButton(if (dark) AppIcons.Sun else AppIcons.Moon, "Switch between light and dark", onToggleTheme)
            Spacer(Modifier.height(8.dp))
            AccountButton(home, state, onDisconnect, onLogOut, onReconnect, onShowFiles, onQuit)
            Spacer(Modifier.height(16.dp))
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxSize()) {
            ConnectionBanner(state.connection, onReconnect)
            Box(Modifier.weight(1f)) {
                when (destination) {
                    Destination.CHATS -> ChatsScreen(session)
                    Destination.SERVER -> ServerScreen(session)
                    Destination.LOG -> LogScreen(session)
                }
            }
        }
    }
}

/** Shown above the screens while the connection is not online. */
@Composable
private fun ConnectionBanner(state: ConnectionState, onReconnect: () -> Unit) {
    AnimatedVisibility(state !is ConnectionState.Online) {
        val (text, health) = describe(state)
        Surface(color = TetherTheme.chat.warningContainer) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dot(healthColor(health), 9.dp)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                if (state is ConnectionState.Terminal) TextButton(onClick = onReconnect) { Text("Reconnect") }
            }
        }
    }
}

/** Your picture with your presence dot; opens a menu with presence, profile and sign-out. */
@Composable
private fun AccountButton(
    home: HomeViewModel,
    state: HomeViewModel.State,
    onDisconnect: () -> Unit,
    onLogOut: () -> Unit,
    onReconnect: () -> Unit,
    onShowFiles: () -> Unit,
    onQuit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var editStatus by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf(false) }
    val online = state.connection is ConnectionState.Online
    val name = state.profile?.displayName ?: home.account.local.orEmpty()
    val dot = if (online) presenceColor(state.presence) else TetherTheme.chat.offline

    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable { open = true }.pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(state.profile?.avatar, name, 38.dp, presence = dot, ring = MaterialTheme.colorScheme.surfaceContainer)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 260.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(state.profile?.avatar, name, 44.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(home.account.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val (text, health) = describe(state.connection)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(healthColor(health), 7.dp)
                        Text(if (online) "Online" else text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.statusMessage?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Presence.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    leadingIcon = { Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) { Dot(presenceColor(p), 10.dp) } },
                    trailingIcon = { if (p == state.presence) Icon(AppIcons.Check, null, Modifier.size(16.dp)) },
                    enabled = online,
                    onClick = { home.setPresence(p); open = false },
                )
            }
            MenuItem(if (state.statusMessage == null) "Set a status message…" else "Change status message…", AppIcons.Edit, enabled = online) {
                editStatus = true; open = false
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (home.profilesEnabled) MenuItem("Edit profile…", AppIcons.User, enabled = online) { editProfile = true; open = false }
            MenuItem("Where are my files?", AppIcons.Folder) { onShowFiles(); open = false }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (state.connection is ConnectionState.Terminal) MenuItem("Reconnect", AppIcons.Refresh) { onReconnect(); open = false }
            MenuItem("Disconnect", AppIcons.Power, hint = "Keeps the saved login") { onDisconnect(); open = false }
            MenuItem("Log out", AppIcons.LogOut, danger = true, hint = "Forgets the saved login and chats") { onLogOut(); open = false }
            MenuItem("Quit", AppIcons.Close) { onQuit(); open = false }
        }
    }
    if (editStatus) {
        TextInputDialog(
            "Status message", "What are you up to?", state.statusMessage.orEmpty(), "Save",
            onDismiss = { editStatus = false }, supporting = "Leave empty to clear it.", onConfirm = home::setStatusMessage,
        )
    }
    if (editProfile) ProfileDialog(home, state.profile, name, onDismiss = { editProfile = false })
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, enabled: Boolean = true, danger: Boolean = false, hint: String? = null, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Column {
                Text(text, color = color)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = color) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun presenceColor(p: Presence) = when (p) {
    Presence.AVAILABLE, Presence.CHAT -> TetherTheme.chat.online
    Presence.AWAY, Presence.XA -> TetherTheme.chat.away
    Presence.DND -> TetherTheme.chat.busy
    Presence.INVISIBLE -> TetherTheme.chat.offline
}
