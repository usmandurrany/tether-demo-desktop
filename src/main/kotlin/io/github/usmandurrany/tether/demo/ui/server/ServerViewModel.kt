package io.github.usmandurrany.tether.demo.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.XmppSession
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.PushState
import io.github.usmandurrany.tether.im.PushTarget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the server offers on this session, which features Tether turned on,
 * and push registration (XEP-0357). Tether only enables what the server
 * advertises, so this is also a quick way to check a new server.
 */
class ServerViewModel(private val session: DemoSession) : ViewModel() {
    /** The live session, in what the screen shows. */
    data class SessionInfo(
        val jid: String,
        val server: String?,
        val resumed: Boolean,
        val streamManagement: Boolean,
        val csi: Boolean,
        /** Stream features and disco#info results, one per line. */
        val features: String,
    )

    data class State(
        val session: SessionInfo? = null,
        val connection: ConnectionState = ConnectionState.Idle,
        val carbons: Boolean = false,
        val history: Boolean = false,
        val push: PushState = PushState.Off,
    )

    val state: StateFlow<State> = combine(
        session.client.session,
        session.client.state,
        session.im.carbons.enabled,
        session.im.history.available,
        session.chat.pushState,
    ) { live, connection, carbons, history, push -> State(live?.let(::info), connection, carbons, history, push) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State())

    private fun info(live: XmppSession): SessionInfo {
        val caps = live.capabilities
        val features = buildString {
            appendLine("Stream features")
            caps.streamFeatures.elements.forEach { appendLine("  ${it.name}  (${it.namespace})") }
            appendLine()
            appendLine("Server (disco#info)")
            caps.server.features.sorted().forEach { appendLine("  $it") }
            appendLine()
            appendLine("Your account (disco#info)")
            caps.account.features.sorted().forEach { appendLine("  $it") }
        }.trimEnd()
        return SessionInfo(
            jid = live.jid.toString(),
            server = caps.server.identities.firstOrNull()?.let { listOfNotNull(it.name, "${it.category}/${it.type}").joinToString(" ") },
            resumed = live.resumed,
            streamManagement = live.streamManagement != null,
            csi = caps.hasStreamFeature("csi", "urn:xmpp:csi:0"),
            features = features,
        )
    }

    /** Pings the server at once, or skips the wait before the next retry. */
    fun checkNow() {
        session.client.wake()
    }

    /** Returns a problem to show, or null. */
    fun register(service: String, node: String, options: String, createNode: Boolean): String? {
        if (service.isBlank() || node.isBlank()) return "Push needs a service and a node."
        val map = options.lines().mapNotNull { line ->
            val key = line.substringBefore('=', "").trim()
            if (key.isEmpty()) null else key to line.substringAfter('=').trim()
        }.toMap()
        viewModelScope.launch { runCatching { session.chat.registerPush(PushTarget(service.trim(), node.trim(), map, createNode)) } }
        return null
    }

    fun unregister() {
        viewModelScope.launch { runCatching { session.chat.unregisterPush() } }
    }
}
