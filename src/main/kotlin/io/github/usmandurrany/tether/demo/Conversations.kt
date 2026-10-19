package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.im.ChatMessage
import io.github.usmandurrany.tether.im.ChatState
import io.github.usmandurrany.tether.im.Direction
import io.github.usmandurrany.tether.im.HistoryPage
import io.github.usmandurrany.tether.im.MessageEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.OffsetDateTime

/** How far one of our messages got. */
enum class Delivery {
    /** Waiting in the outbox or for the server's confirmation. */
    QUEUED,

    /** The server has it (XEP-0198 ack, or written when the server has no Stream Management). */
    SENT,

    /** The contact's device confirmed it (XEP-0184). */
    DELIVERED,

    /** The server bounced it. */
    FAILED,
}

data class UiMessage(
    val id: String?,
    val serverId: String?,
    val outgoing: Boolean,
    val body: String,
    val timeMillis: Long,
    val delivery: Delivery? = null,
    val error: String? = null,
    /** Came from another of our devices (XEP-0280 carbon). */
    val carbon: Boolean = false,
)

data class Conversation(
    val peer: BareJid,
    val messages: List<UiMessage> = emptyList(),
    val unread: Int = 0,
    val typing: ChatState? = null,
    /** Archive cursor for "load older": the oldest archive id loaded so far. */
    val historyCursor: String? = null,
    val historyLoaded: Boolean = false,
    val historyComplete: Boolean = false,
)

/**
 * The app's in-memory message list, fed by [MessageEvent]s and archive pages.
 * A real app would keep this in a database; the SDK leaves storage to the app.
 */
class Conversations {
    private val _all = MutableStateFlow<Map<BareJid, Conversation>>(emptyMap())
    val all: StateFlow<Map<BareJid, Conversation>> = _all.asStateFlow()

    @Volatile
    var open: BareJid? = null
        set(value) {
            field = value
            if (value != null) edit(value) { it.copy(unread = 0) }
        }

    fun clear() {
        _all.value = emptyMap()
    }

    /** A message we just handed to `Messaging.send`. */
    fun addOutgoing(peer: BareJid, id: String, body: String, delivery: Delivery) = edit(peer) {
        it.copy(messages = it.messages + UiMessage(id, null, true, body, System.currentTimeMillis(), delivery))
    }

    fun onEvent(event: MessageEvent) {
        when (event) {
            is MessageEvent.Received -> receive(event.message)
            is MessageEvent.SentToServer -> setDelivery(null, event.id, Delivery.SENT)
            is MessageEvent.Delivered -> setDelivery(event.peer, event.id, Delivery.DELIVERED)
            is MessageEvent.Failed -> setDelivery(event.peer, event.id, Delivery.FAILED, event.condition ?: "error")
            is MessageEvent.ChatStateChanged -> edit(event.peer) {
                it.copy(typing = event.state.takeIf { s -> s == ChatState.COMPOSING || s == ChatState.PAUSED })
            }
        }
    }

    /** Merges a page from the archive (XEP-0313) in front of what is already shown. */
    fun addHistory(peer: BareJid, page: HistoryPage) = edit(peer) { conv ->
        val older = page.messages.filter { it.body != null }.map { it.toUi() }
        // What is already shown wins, so live delivery states are kept.
        val merged = (conv.messages + older).distinctBy { it.key() }.sortedBy { it.timeMillis }
        conv.copy(
            messages = merged,
            historyCursor = page.first ?: conv.historyCursor,
            historyLoaded = true,
            historyComplete = page.complete,
        )
    }

    private fun receive(message: ChatMessage) {
        if (message.body == null) return
        edit(message.peer) { conv ->
            val ui = message.toUi()
            if (conv.messages.any { it.key() == ui.key() }) return@edit conv // already shown (live and from the archive)
            conv.copy(
                messages = (conv.messages + ui).sortedBy { it.timeMillis },
                unread = if (open == message.peer || ui.outgoing) conv.unread else conv.unread + 1,
                typing = if (ui.outgoing) conv.typing else null,
            )
        }
    }

    private fun setDelivery(peer: BareJid?, id: String, delivery: Delivery, error: String? = null) {
        _all.update { map ->
            map.mapValues { (jid, conv) ->
                if (peer != null && jid != peer) return@mapValues conv
                conv.copy(
                    messages = conv.messages.map { m ->
                        // Never move a message backwards (a late server ack after the receipt).
                        if (m.outgoing && m.id == id && (m.delivery == null || m.delivery < delivery || delivery == Delivery.FAILED)) {
                            m.copy(delivery = delivery, error = error)
                        } else {
                            m
                        }
                    },
                )
            }
        }
    }

    private fun edit(peer: BareJid, change: (Conversation) -> Conversation) {
        _all.update { it + (peer to change(it[peer] ?: Conversation(peer))) }
    }

    private fun ChatMessage.toUi(): UiMessage = UiMessage(
        id = id,
        serverId = serverId,
        outgoing = direction == Direction.OUTGOING,
        body = body.orEmpty(),
        timeMillis = delayedAt?.let(::parseTime) ?: System.currentTimeMillis(),
        delivery = if (direction == Direction.OUTGOING) Delivery.SENT else null,
        carbon = carbon,
    )

    private fun UiMessage.key(): String = when {
        // Our own messages carry the same id live and in the archive (origin-id).
        id != null -> (if (outgoing) "out:" else "in:") + id
        serverId != null -> "sid:$serverId"
        else -> "t:$timeMillis:$body"
    }

    private fun parseTime(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}
