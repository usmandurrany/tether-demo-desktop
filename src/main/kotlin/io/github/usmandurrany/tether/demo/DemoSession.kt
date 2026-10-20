package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.core.BackgroundMode
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ClientPolicy
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.core.CredentialVault
import io.github.usmandurrany.tether.core.FileKeyValueStore
import io.github.usmandurrany.tether.core.KeyValueStore
import io.github.usmandurrany.tether.core.ReconnectPolicy
import io.github.usmandurrany.tether.core.TetherJvm
import io.github.usmandurrany.tether.core.XmppClient
import io.github.usmandurrany.tether.debug.ConsoleSink
import io.github.usmandurrany.tether.debug.DebugConfig
import io.github.usmandurrany.tether.debug.DeviceInfo
import io.github.usmandurrany.tether.debug.LogLevel
import io.github.usmandurrany.tether.debug.MemorySink
import io.github.usmandurrany.tether.debug.RemoteLogConfig
import io.github.usmandurrany.tether.debug.RemoteLogSink
import io.github.usmandurrany.tether.debug.TetherDebug
import io.github.usmandurrany.tether.debug.http
import io.github.usmandurrany.tether.im.ChatFeatures
import io.github.usmandurrany.tether.im.FileMessageStore
import io.github.usmandurrany.tether.im.InstantMessaging
import io.github.usmandurrany.tether.im.SubscriptionEvent
import io.github.usmandurrany.tether.im.TetherChat
import io.github.usmandurrany.tether.sasl.ScramKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One signed-in account: the whole SDK integration of this demo lives here.
 *
 * [chat] ([TetherChat]) does the chat work: message list and ticks, read
 * markers, typing, history, contacts. The screens read its flows and call
 * `send`, `typing`, `openChat` and `closeChat`. This class only adds what is
 * specific to the demo: debug logging and its settings.
 */
class DemoSession private constructor(
    val settings: ConnectionSettings,
    private val store: KeyValueStore,
    val debug: TetherDebug,
    val logs: MemorySink,
    val remoteLogs: RemoteLogSink?,
    val chat: TetherChat,
    private val scope: CoroutineScope,
) {
    val client: XmppClient get() = chat.client
    val account: BareJid get() = chat.account

    /** The protocol-level features, for the server panel and presence menu. */
    val im: InstantMessaging get() = chat.im

    private val _notes = MutableStateFlow<Map<BareJid, String>>(emptyMap())

    /** The note sent with each contact request, to show next to [TetherChat.contactRequests]. */
    val requestNotes: StateFlow<Map<BareJid, String>> = _notes.asStateFlow()

    init {
        scope.launch {
            im.presences.subscriptions.collect { event ->
                if (event is SubscriptionEvent.Requested) event.status?.let { note -> _notes.update { it + (event.from to note) } }
                val auto = event is SubscriptionEvent.Requested && chat.features.autoAcceptContacts
                debug.log(LogLevel.INFO, "app", "Subscription: $event" + if (auto) " (accepted automatically)" else "")
            }
        }
        debug.trackConnection(client.state, scope)
    }

    /** Accepting also asks to see their presence back, which is what most chat apps do. */
    suspend fun answerRequest(from: BareJid, accept: Boolean) {
        if (accept) chat.approve(from) else chat.deny(from)
        _notes.update { it - from }
    }

    suspend fun addContact(jid: BareJid, name: String?, group: String?) =
        chat.addContact(jid, name, groups = group?.let(::setOf))

    /** First attempt; after it the client keeps reconnecting by itself until [disconnect]. */
    suspend fun connect(): ConnectResult = chat.connect()

    /** Ends the connection but keeps the saved login keys, so the next start needs no password. */
    suspend fun disconnect() {
        withTimeoutOrNull(6_000) { client.disconnect() }
        remoteLogs?.let { withTimeoutOrNull(3_000) { it.flush() } }
        shutdown()
    }

    /** Ends the connection and forgets this account's saved login keys. */
    suspend fun logOut() {
        withTimeoutOrNull(6_000) { client.disconnect() }
        CredentialVault(store).clear(account)
        // Signing out also forgets this account's chats on this computer.
        runCatching { FileMessageStore(AppPaths.messagesFile(account.toString())).clear() }
        remoteLogs?.let { withTimeoutOrNull(3_000) { it.flush() } }
        shutdown()
    }

    private fun shutdown() {
        chat.close()
        remoteLogs?.close()
        scope.cancel()
    }

    companion object {
        /** The store every session shares: one file for all accounts, keys are per account. */
        val fileStore: FileKeyValueStore by lazy { FileKeyValueStore(AppPaths.tetherStateFile) }

        /** Keys saved by an earlier "remember me" login, if any. */
        suspend fun savedKeys(account: BareJid) = CredentialVault(fileStore).loadKeys(account)

        fun create(settings: ConnectionSettings, password: String?, savedKeys: List<ScramKeys>): DemoSession {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            // Debug logging: an in-app log screen always, the terminal and a log portal when asked for.
            val logs = MemorySink(capacity = 2_000)
            val debug = TetherDebug(DebugConfig(redactBodies = settings.redactBodies), sinks = listOf(logs))
            if (settings.logToConsole) debug.addSink(ConsoleSink())
            val remote = if (settings.portalUrl.isNotBlank() && settings.portalKey.isNotBlank()) {
                RemoteLogSink.http(
                    RemoteLogConfig(
                        endpoint = settings.portalUrl.trim(),
                        ingestKey = settings.portalKey.trim(),
                        device = DeviceInfo.current(settings.installId, appId = APP_ID, appVersion = APP_VERSION),
                        label = settings.portalLabel.trim().ifEmpty { null },
                    ),
                    scope,
                ).also(debug::addSink)
            } else {
                null
            }

            val config = settings.toXmppConfig(password, savedKeys)
            val store: KeyValueStore = fileStore
            // Follows the network by itself. Without "remember me", no login keys are written to disk.
            val client = TetherJvm.client(
                config,
                scope,
                // A desktop app stays connected when its window loses focus; it only tells
                // the server it is inactive (XEP-0352), so the server can hold back noise.
                policy = ClientPolicy(reconnect = ReconnectPolicy(backgroundMode = BackgroundMode.STAY_CONNECTED)),
                store = store,
                logger = debug.trafficLogger(),
                rememberLogin = settings.rememberLogin,
            )
            // Every chat feature on: ticks, read markers, typing, contacts, presence, carbons, history,
            // last seen, delete for everyone and profiles. Auto-accept is the SDK's one opt-in feature.
            val features = ChatFeatures(autoAcceptContacts = settings.autoAcceptContacts, profiles = settings.profiles)
            // The SDK's file store keeps ticks, read state, deletions and photos across restarts.
            val messages = if (settings.keepMessages) FileMessageStore(AppPaths.messagesFile(config.jid.toString())) else null
            val chat = TetherChat(client, features, store = store, messageStore = messages)
            debug.log(LogLevel.INFO, "app", "Signing in as ${config.jid} via ${config.host ?: "DNS SRV"}")
            return DemoSession(settings, store, debug, logs, remote, chat, scope)
        }

        const val APP_ID = "io.github.usmandurrany.tether.demo"
        const val APP_VERSION = "0.1.0"
    }
}
