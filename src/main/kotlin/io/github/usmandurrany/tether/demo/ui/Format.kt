package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.NegotiationFailure
import io.github.usmandurrany.tether.core.XmppNegotiationException
import io.github.usmandurrany.tether.transport.TlsVerificationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** How healthy the connection is, for picking a colour. */
enum class Health { GOOD, TRYING, OFF, BAD }

/** The connection state in words, and how healthy it is. */
fun describe(state: ConnectionState): Pair<String, Health> = when (state) {
    is ConnectionState.Online -> "Online as ${state.jid}" to Health.GOOD
    is ConnectionState.Connecting -> (if (state.attempt <= 1) "Connecting…" else "Reconnecting (attempt ${state.attempt})…") to Health.TRYING
    is ConnectionState.Backoff -> "Connection lost, retrying in ${(state.delayMillis + 999) / 1000}s" to Health.TRYING
    ConnectionState.WaitingForNetwork -> "Waiting for the network…" to Health.OFF
    ConnectionState.Paused -> "Paused" to Health.OFF
    is ConnectionState.Terminal -> "Stopped: ${describe(state.error)}" to Health.BAD
    ConnectionState.Idle -> "Not connected" to Health.OFF
}

/** A sentence a person can act on, for any error the client reports. */
fun describe(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.toList()
    chain.filterIsInstance<TlsVerificationException>().firstOrNull()?.let {
        return "The server's certificate isn't trusted (${it.message ?: "verification failed"})."
    }
    val negotiation = chain.filterIsInstance<XmppNegotiationException>().firstOrNull()
        ?: return error.message ?: error::class.simpleName.orEmpty()
    val detail = negotiation.message?.substringAfter(": ", "").orEmpty()
    return when (negotiation.failure) {
        NegotiationFailure.UNREACHABLE -> "Couldn't reach the server. Check the host and port, or leave them empty to use DNS."
        NegotiationFailure.CONNECTION_LOST -> "The connection dropped while signing in. $detail"
        NegotiationFailure.TLS_UNAVAILABLE -> "The server doesn't offer TLS, and Tether never connects without it."
        NegotiationFailure.TLS_VERIFICATION -> "The server's certificate isn't trusted. $detail"
        NegotiationFailure.AUTHENTICATION -> if (detail.contains("NO_ACCEPTABLE_MECHANISM")) {
            val offered = detail.substringAfter("mechanisms ", "").substringBefore(" is allowed", "").ifEmpty { "other mechanisms" }
            "The server only offers $offered. Turn on \"Allow SCRAM-SHA-1\" or \"Allow PLAIN\" under Security if you trust it."
        } else {
            "The server refused the login. Check the address and password. $detail"
        }
        NegotiationFailure.STREAM_ERROR -> when (negotiation.streamError) {
            "conflict" -> "Another device signed in with the same resource. Use a different resource or leave it empty."
            "host-unknown" -> "The server doesn't host this domain. Check the address, or the host if you set one."
            else -> "The server closed the stream (${negotiation.streamError ?: "error"}). $detail"
        }
        NegotiationFailure.PROTOCOL -> "The server answered in a way Tether didn't expect. $detail"
    }.trim()
}

/** Dates and times the way chat apps show them. */
object Times {
    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
    private val now get() = System.currentTimeMillis()

    fun sameDay(a: Long, b: Long): Boolean {
        val x = cal(a)
        val y = cal(b)
        return x[Calendar.YEAR] == y[Calendar.YEAR] && x[Calendar.DAY_OF_YEAR] == y[Calendar.DAY_OF_YEAR]
    }

    private fun yesterday(t: Long) = sameDay(t, now - 86_400_000)
    private fun thisWeek(t: Long) = now - t < 6 * 86_400_000L

    fun clock(t: Long): String = SimpleDateFormat("HH:mm").format(Date(t))

    /** The separator between days in a chat. */
    fun day(t: Long): String = when {
        sameDay(t, now) -> "Today"
        yesterday(t) -> "Yesterday"
        thisWeek(t) -> SimpleDateFormat("EEEE").format(Date(t))
        cal(t)[Calendar.YEAR] == cal(now)[Calendar.YEAR] -> SimpleDateFormat("EEEE, d MMMM").format(Date(t))
        else -> SimpleDateFormat("d MMMM yyyy").format(Date(t))
    }

    /** The chat list: a time today, else a day or a date. */
    fun stamp(t: Long): String = when {
        sameDay(t, now) -> clock(t)
        yesterday(t) -> "Yesterday"
        thisWeek(t) -> SimpleDateFormat("EEE").format(Date(t))
        else -> SimpleDateFormat("d MMM").format(Date(t))
    }

    fun lastSeen(at: Long): String {
        val ago = (now - at).coerceAtLeast(0) / 60_000
        return when {
            ago < 1 -> "just now"
            ago < 60 -> "$ago min ago"
            sameDay(at, now) -> "today at ${clock(at)}"
            yesterday(at) -> "yesterday at ${clock(at)}"
            else -> SimpleDateFormat("d MMM 'at' HH:mm").format(Date(at))
        }
    }
}
