package io.github.usmandurrany.tether.demo.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.MediaProbe
import io.github.usmandurrany.tether.demo.ui.Times
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Contact
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.Show
import io.github.usmandurrany.tether.im.Subscription
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One entry of the chat list: a contact, someone we only have a conversation with, or both. */
data class ChatItem(
    val jid: BareJid,
    val name: String,
    val avatar: Avatar?,
    val online: Boolean,
    val show: Show?,
    /** Presence in a few words, for when there's no message to preview. */
    val presence: String,
    val lastMessage: Message?,
    val unread: Int,
    val typing: Boolean,
    val contact: Contact?,
)

data class ContactRequest(val from: BareJid, val note: String?)

/**
 * The chat list and the open chat. The SDK's [io.github.usmandurrany.tether.im.TetherChat]
 * already does the chat work (ticks, read markers, typing timers, history,
 * de-duplication); this only shapes its flows for the screen and forwards
 * what the person does.
 */
class ChatsViewModel(private val session: DemoSession) : ViewModel() {
    data class State(
        val chats: List<ChatItem> = emptyList(),
        val anyChats: Boolean = false,
        val query: String = "",
        val requests: List<ContactRequest> = emptyList(),
        val open: ChatItem? = null,
        val openJid: BareJid? = null,
        val messages: List<Message> = emptyList(),
        val canLoadOlder: Boolean = false,
        val loadingOlder: Boolean = false,
    )

    private val chat = session.chat
    private val query = MutableStateFlow("")
    private val openJid = MutableStateFlow<BareJid?>(null)
    private val historyComplete = MutableStateFlow<Set<BareJid>>(emptySet())
    private val loadingOlder = MutableStateFlow(false)
    private val _errors = Channel<String>(Channel.BUFFERED)

    /** One-off problems to show in a snackbar. */
    val errors: Flow<String> = _errors.receiveAsFlow()

    private val allChats: Flow<List<ChatItem>> = combine(chat.contacts, chat.conversations, chat.typingContacts) { contacts, conversations, typing ->
        val byJid = contacts.associateBy { it.jid }
        val chats = conversations.associateBy { it.peer }
        (byJid.keys + chats.keys).filter { it != session.account }.map { jid ->
            val c = byJid[jid]
            val conv = chats[jid]
            val online = c?.online ?: conv?.online ?: false
            ChatItem(
                jid = jid,
                name = c?.name ?: conv?.name ?: jid.toString(),
                avatar = c?.avatar ?: conv?.avatar,
                online = online,
                show = c?.show,
                presence = presenceText(c, online, c?.lastSeen ?: conv?.lastSeen),
                lastMessage = conv?.lastMessage,
                unread = conv?.unreadCount ?: 0,
                typing = jid in typing,
                contact = c,
            )
        }
            // Recent chats first, then everyone else, online first.
            .sortedWith(compareByDescending<ChatItem> { it.lastMessage?.timestamp ?: Long.MIN_VALUE }.thenBy { !it.online }.thenBy { it.name.lowercase() })
    }

    private val requests = combine(chat.contactRequests, session.requestNotes) { pending, notes -> pending.map { ContactRequest(it, notes[it]) } }

    private val openMessages = combine(chat.allMessages, openJid) { all, jid -> jid?.let { all[it] }.orEmpty() }

    private val history = combine(session.im.history.available, historyComplete, openJid, loadingOlder) { available, complete, jid, loading ->
        (available && jid != null && jid !in complete) to loading
    }

    val state: StateFlow<State> = combine(allChats, query, requests, openJid, openMessages, history) { args ->
        @Suppress("UNCHECKED_CAST")
        val chats = args[0] as List<ChatItem>
        val q = args[1] as String
        @Suppress("UNCHECKED_CAST")
        val reqs = args[2] as List<ContactRequest>
        val jid = args[3] as BareJid?
        @Suppress("UNCHECKED_CAST")
        val msgs = args[4] as List<Message>
        @Suppress("UNCHECKED_CAST")
        val (canLoad, loading) = args[5] as Pair<Boolean, Boolean>
        val needle = q.trim().lowercase()
        State(
            chats = if (needle.isEmpty()) chats else chats.filter { needle in it.name.lowercase() || needle in it.jid.toString().lowercase() },
            anyChats = chats.isNotEmpty(),
            query = q,
            requests = reqs,
            open = chats.firstOrNull { it.jid == jid },
            openJid = jid,
            messages = msgs,
            canLoadOlder = canLoad,
            loadingOlder = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun search(text: String) {
        query.value = text
    }

    /** Opens a chat. The SDK marks it read while the window has focus, and loads its newest history the first time. */
    fun open(jid: BareJid) {
        val previous = openJid.value
        if (previous == jid) return
        previous?.let(chat::closeChat)
        openJid.value = jid
        chat.openChat(jid)
    }

    /** Back to the list; the SDK stops our typing notification. */
    fun close() {
        openJid.value?.let(chat::closeChat)
        openJid.value = null
    }

    override fun onCleared() {
        openJid.value?.let(chat::closeChat)
    }

    // --- Writing ---------------------------------------------------------------------

    /** Shows at once as "sending"; works offline too. */
    fun send(text: String) {
        val jid = openJid.value ?: return
        chat.send(jid, text)
    }

    /** The SDK sends "typing", then "paused" after a few quiet seconds. */
    fun typing() {
        openJid.value?.let(chat::typing)
    }

    /** Sends [data] with the caption as the body, or a link other XMPP apps can open when there is none. */
    private fun sendData(caption: String, data: MessageData) {
        val jid = openJid.value ?: return
        runCatching { chat.send(jid, caption.ifBlank { Attachments.fallbackText(data) }, data) }.onFailure { report(it) }
    }

    /** The demo has no file hosting: it sends a link and reads the photo's size and dimensions from the web server. */
    fun sendPhoto(url: String, caption: String) = viewModelScope.launch {
        val info = MediaProbe.photo(url)
        sendData(caption, Attachments.photo(url, info?.width, info?.height, info?.bytes, info?.mime))
    }

    fun sendVideo(url: String, seconds: Int?, caption: String) = viewModelScope.launch {
        sendData(caption, Attachments.video(url, seconds, MediaProbe.size(url)))
    }

    fun sendLocation(latitude: Double, longitude: Double, label: String?) = sendData("", Attachments.location(latitude, longitude, label))

    /** Any type and fields: the receiver shows them as they arrive. Returns a problem to show, or null. */
    fun sendCustom(type: String, fields: String, caption: String): String? {
        val pairs = fields.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        return runCatching { MessageData(type.trim(), pairs.toMap()) }.fold({ sendData(caption, it); null }, { it.message ?: "Can't send that." })
    }

    // --- Messages --------------------------------------------------------------------

    fun deleteForEveryone(m: Message) {
        if (!chat.deleteForEveryone(m)) _errors.trySend("This message can't be deleted for everyone.")
    }

    fun deleteForMe(m: Message) = chat.deleteForMe(m)

    fun retry(m: Message) {
        chat.retry(m)
    }

    fun loadOlder() {
        val jid = openJid.value ?: return
        if (loadingOlder.value) return
        loadingOlder.value = true
        viewModelScope.launch {
            runCatching { if (!chat.loadOlder(jid)) historyComplete.update { it + jid } }.onFailure { report(it) }
            loadingOlder.value = false
        }
    }

    // --- Contacts --------------------------------------------------------------------

    /** Returns a problem to show, or null when the address is fine. */
    fun addContact(address: String, name: String, group: String): String? {
        val jid = runCatching { BareJid.parse(address.trim()) }.getOrNull()
        if (jid?.local == null) return "Enter a full address, like bob@example.com."
        launch { session.addContact(jid, name.trim().ifEmpty { null }, group.trim().ifEmpty { null }) }
        return null
    }

    /** Renaming keeps their groups. */
    fun rename(jid: BareJid, name: String) = launch { chat.addContact(jid, name.trim().ifEmpty { null }) }

    fun removeContact(jid: BareJid) = launch { chat.removeContact(jid) }

    fun answer(jid: BareJid, accept: Boolean) = launch { session.answerRequest(jid, accept) }

    // Finer subscription control than TetherChat's approve and deny, from the protocol-level API.
    fun askForPresence(jid: BareJid) = launch { session.im.presences.request(jid) }
    fun sharePresence(jid: BareJid) = launch { session.im.presences.approve(jid) }
    fun stopSharingPresence(jid: BareJid) = launch { session.im.presences.deny(jid) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { report(it) } }
    }

    private fun report(e: Throwable) {
        val online = session.client.state.value is ConnectionState.Online
        val message = e.message ?: e::class.simpleName.orEmpty()
        _errors.trySend(if (online) message else "$message You're not connected right now.")
    }

    private fun presenceText(c: Contact?, online: Boolean, lastSeen: Long?): String = when {
        online -> c?.status ?: c?.show?.let(::showName) ?: "Online"
        c == null -> "Not in your contacts"
        c.pending -> "Contact request sent"
        c.subscription == Subscription.NONE || c.subscription == Subscription.FROM -> "Presence not shared"
        else -> lastSeen?.let { "Last seen ${Times.lastSeen(it)}" } ?: "Offline"
    }

    private fun showName(show: Show) = when (show) {
        Show.AWAY -> "Away"
        Show.XA -> "Away for long"
        Show.DND -> "Do not disturb"
        Show.CHAT -> "Free to chat"
    }
}

/** A message in one line, for the chat list. */
fun preview(m: Message): String {
    if (m.deleted) return "Message deleted"
    val kind = Attachments.of(m).firstOrNull()?.let {
        when (it) {
            is Attachments.Photo -> "Photo"
            is Attachments.Video -> "Video"
            is Attachments.Location -> "Location" + (it.label?.let { l -> ": $l" } ?: "")
            is Attachments.Other -> "Custom data"
        }
    }
    val body = m.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val text = when {
        kind != null && (body.isEmpty() || body.startsWith("http")) -> kind
        kind != null -> "$kind · $body"
        else -> body
    }
    return (if (m.outgoing) "You: " else "") + text
}
