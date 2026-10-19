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
import io.github.usmandurrany.tether.im.ChatState
import io.github.usmandurrany.tether.im.InstantMessaging
import io.github.usmandurrany.tether.im.StoredRoster
import io.github.usmandurrany.tether.im.SubscriptionEvent
import io.github.usmandurrany.tether.sasl.ScramKeys
import io.github.usmandurrany.tether.transport.TlsVerificationException
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
 * Order matters: create the client, install features ([InstantMessaging]),
 * start collecting events, and only then call [connect]. Events nobody
 * collects yet are dropped.
 */
class DemoSession private constructor(
    val settings: ConnectionSettings,
    private val store: KeyValueStore,
    val debug: TetherDebug,
    val logs: MemorySink,
    val remoteLogs: RemoteLogSink?,
    val client: XmppClient,
    val im: InstantMessaging,
    private val scope: CoroutineScope,
) {
    val account: BareJid get() = client.account
    val conversations = Conversations()

    private val _requests = MutableStateFlow<Map<BareJid, String?>>(emptyMap())

    /** People asking to see our presence, with the note they sent; answer with [answerRequest]. */
    val subscriptionRequests: StateFlow<Map<BareJid, String?>> = _requests.asStateFlow()

    init {
        scope.launch { im.messaging.events.collect(conversations::onEvent) }
        scope.launch {
            im.presences.subscriptions.collect { event ->
                if (event is SubscriptionEvent.Requested) _requests.update { it + (event.from to event.status) }
                debug.log(LogLevel.INFO, "app", "Subscription: $event")
            }
        }
        debug.trackConnection(client.state, scope)
        NetworkMonitor(client).start(scope)
    }

    /**
     * Accepting also asks to see their presence back and adds them to the roster,
     * which is what most chat apps do.
     */
    suspend fun answerRequest(from: BareJid, accept: Boolean) {
        _requests.update { it - from }
        if (accept) {
            im.presences.approve(from)
            if (im.roster.items.value[from] == null) im.roster.set(from)
            im.presences.request(from)
        } else {
            im.presences.deny(from)
        }
    }

    suspend fun addContact(jid: BareJid, name: String?, group: String?) {
        im.roster.set(jid, name = name, groups = setOfNotNull(group))
        im.presences.request(jid)
    }

    /** First attempt; after it the client keeps reconnecting by itself until [disconnect]. */
    suspend fun connect(): ConnectResult = client.connect()

    suspend fun sendMessage(to: BareJid, body: String) {
        val id = im.messaging.send(to, body)
        // Without Stream Management (XEP-0198) the server never acks, so "on the wire" is as good as it gets.
        val noAcks = client.session.value?.let { it.streamManagement == null } ?: false
        conversations.addOutgoing(to, id, body, if (noAcks) Delivery.SENT else Delivery.QUEUED)
    }

    suspend fun sendTyping(to: BareJid, state: ChatState) {
        runCatching { im.messaging.sendChatState(to, state) }
    }

    /** Loads the newest archive page for [peer], or the page before what is shown. */
    suspend fun loadHistory(peer: BareJid) {
        if (!im.history.available.value) return
        val conv = conversations.all.value[peer]
        val page = im.history.fetch(with = peer, before = conv?.historyCursor.takeIf { conv?.historyLoaded == true }, max = 25)
        conversations.addHistory(peer, page)
    }

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
        remoteLogs?.let { withTimeoutOrNull(3_000) { it.flush() } }
        shutdown()
    }

    private fun shutdown() {
        client.close()
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
            // Without "remember me", nothing that could log in again is written to disk.
            val store: KeyValueStore = if (settings.rememberLogin) fileStore else WithoutCredentials(fileStore)
            if (!settings.rememberLogin) scope.launch { CredentialVault(fileStore).clear(config.jid) }
            val client = TetherJvm.client(
                config,
                scope,
                // A desktop app stays connected when its window loses focus; it only tells
                // the server it is inactive (XEP-0352), so the server can hold back noise.
                policy = ClientPolicy(reconnect = ReconnectPolicy(backgroundMode = BackgroundMode.STAY_CONNECTED)),
                store = store,
                logger = debug.trafficLogger(),
            )
            val im = InstantMessaging(client, rosterStore = StoredRoster(store, config.jid))
            debug.log(LogLevel.INFO, "app", "Signing in as ${config.jid} via ${config.host ?: "DNS SRV"}")
            return DemoSession(settings, store, debug, logs, remote, client, im, scope)
        }

        /** The SHA-256 of a certificate the client refused, found anywhere in the error's causes. */
        fun refusedCertificate(error: Throwable): String? =
            generateSequence(error) { it.cause }.filterIsInstance<TlsVerificationException>().firstOrNull()?.peerCertificateSha256

        const val APP_ID = "io.github.usmandurrany.tether.demo"
        const val APP_VERSION = "0.1.0"
    }
}

/**
 * A store that drops the SCRAM login keys, for sign-ins without "remember me".
 * The prefix is the one [CredentialVault] uses for its keys.
 */
private class WithoutCredentials(private val inner: KeyValueStore) : KeyValueStore {
    override suspend fun get(key: String): String? = if (key.startsWith(CREDENTIAL_PREFIX)) null else inner.get(key)

    override suspend fun put(key: String, value: String?) {
        // Still clear old keys, so unticking "remember me" also forgets the last saved login.
        if (key.startsWith(CREDENTIAL_PREFIX)) inner.put(key, null) else inner.put(key, value)
    }

    companion object {
        const val CREDENTIAL_PREFIX = "tether.scram."
    }
}
