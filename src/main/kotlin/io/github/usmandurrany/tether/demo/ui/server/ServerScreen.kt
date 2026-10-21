package io.github.usmandurrany.tether.demo.ui.server

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.ui.components.Dot
import io.github.usmandurrany.tether.demo.ui.components.InfoRow
import io.github.usmandurrany.tether.demo.ui.components.Pill
import io.github.usmandurrany.tether.demo.ui.components.ScreenTitle
import io.github.usmandurrany.tether.demo.ui.components.SectionCard
import io.github.usmandurrany.tether.demo.ui.describe
import io.github.usmandurrany.tether.demo.ui.signin.CheckRow
import io.github.usmandurrany.tether.demo.ui.signin.Field
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.MonoStyle
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme
import io.github.usmandurrany.tether.im.PushState

@Composable
fun ServerRoute(session: DemoSession) {
    val vm = viewModel { ServerViewModel(session) }
    val state by vm.state.collectAsState()
    ServerScreen(state, onCheckNow = vm::checkNow, onRegister = vm::register, onUnregister = vm::unregister)
}

/** [onRegister] returns a problem to show, or null. */
@Composable
fun ServerScreen(
    state: ServerViewModel.State,
    onCheckNow: () -> Unit = {},
    onRegister: (service: String, node: String, options: String, createNode: Boolean) -> String? = { _, _, _, _ -> null },
    onUnregister: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ScreenTitle("Server", "What this server offers on the current session. Tether only turns on what the server advertises.")
                SessionCard(state, onCheckNow)
                PushCard(state.push, onRegister, onUnregister)
                FeaturesCard(state)
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun SessionCard(state: ServerViewModel.State, onCheckNow: () -> Unit) {
    SectionCard("Session", "Live details of this connection") {
        val live = state.session
        if (live == null) {
            Text("No live session (${describe(state.connection).first}).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column {
                InfoRow("Bound address") { Text(live.jid, style = MaterialTheme.typography.titleSmall) }
                InfoRow("Server") { Text(live.server ?: "unknown", style = MaterialTheme.typography.titleSmall) }
                InfoRow("Resumed session") { Pill(if (live.resumed) "Yes" else "No", live.resumed) }
                InfoRow("Stream Management (XEP-0198)") { OnOff(live.streamManagement) }
                InfoRow("Message carbons (XEP-0280)") { OnOff(state.carbons) }
                InfoRow("Message archive (XEP-0313)") { Pill(if (state.history) "Available" else "Not offered", state.history) }
                InfoRow("Client state indication (XEP-0352)", last = true) { OnOff(live.csi) }
            }
        }
        OutlinedButton(onClick = onCheckNow) {
            Icon(AppIcons.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check connection now")
        }
    }
}

@Composable
private fun OnOff(on: Boolean) = Pill(if (on) "On" else "Off", on)

@Composable
private fun PushCard(push: PushState, onRegister: (String, String, String, Boolean) -> String?, onUnregister: () -> Unit) {
    var service by remember { mutableStateOf("") }
    var node by remember { mutableStateOf("") }
    var options by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    SectionCard("Push notifications", "XEP-0357. Desktop apps rarely need push; this shows how a mobile app registers its FCM or APNs token.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Service", service, { service = it }, placeholder = "pubsub.example.com", modifier = Modifier.weight(1f))
            Field("Node", node, { node = it }, placeholder = "Node name for this device", modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            options, { options = it },
            label = { Text("Options") },
            placeholder = { Text("key=value per line, e.g. device_id=<token>") },
            minLines = 2, maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        CheckRow("Create the node first (MongooseIM)", create, { create = it })
        val (text, color) = when (push) {
            PushState.Off -> "Not registered" to TetherTheme.chat.offline
            PushState.Unsupported -> "This server doesn't offer push (XEP-0357)" to TetherTheme.chat.offline
            is PushState.Pending -> "Registering ${push.target.node}…" to TetherTheme.chat.away
            is PushState.On -> "Registered: ${push.target.node} on ${push.target.service}" to TetherTheme.chat.online
            is PushState.Failed -> "The server refused: ${push.condition ?: "error"}" to TetherTheme.chat.busy
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(color)
            Text(problem ?: text, style = MaterialTheme.typography.bodyMedium, color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { problem = onRegister(service, node, options, create) }) { Text("Register") }
            OutlinedButton(onClick = onUnregister) { Text("Unregister") }
        }
    }
}

@Composable
private fun FeaturesCard(state: ServerViewModel.State) {
    SectionCard("Advertised features", "Stream features and service discovery, as the server sent them") {
        val text = state.session?.features ?: "Not connected."
        SelectionContainer {
            Text(
                text, style = MonoStyle,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
            )
        }
    }
}
