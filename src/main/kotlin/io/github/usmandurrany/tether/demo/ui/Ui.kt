package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.NegotiationFailure
import io.github.usmandurrany.tether.core.XmppNegotiationException
import io.github.usmandurrany.tether.im.Show
import io.github.usmandurrany.tether.transport.TlsVerificationException
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

/** A two-column label/field form with section headings. */
class Form : JPanel(GridBagLayout()), javax.swing.Scrollable {
    private var row = 0

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }

    fun section(title: String) {
        add(
            JLabel(title).apply { font = font.deriveFont(Font.BOLD, font.size2D + 1f) },
            GridBagConstraints().apply {
                gridx = 0; gridy = row++; gridwidth = 2; anchor = GridBagConstraints.WEST
                insets = Insets(if (row == 1) 0 else 14, 0, 4, 0)
            },
        )
    }

    fun field(label: String, field: JComponent, hint: String? = null) {
        add(JLabel(label), GridBagConstraints().apply { gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = Insets(3, 0, 3, 12) })
        add(field, GridBagConstraints().apply { gridx = 1; gridy = row++; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(3, 0, 3, 0) })
        hint?.let { hintRow(it) }
    }

    /** A component across both columns (checkboxes, button rows). */
    fun wide(component: JComponent, hint: String? = null) {
        add(component, GridBagConstraints().apply { gridx = 0; gridy = row++; gridwidth = 2; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = Insets(2, 0, 2, 0) })
        hint?.let { hintRow(it) }
    }

    private fun hintRow(text: String) {
        add(
            muted("<html><body style='width:330px'>$text</body></html>"),
            GridBagConstraints().apply { gridx = 1; gridy = row++; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 4, 0) },
        )
    }

    // In a scroll pane, follow its width (wrapping hints) and scroll only vertically.
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(r: java.awt.Rectangle, o: Int, d: Int) = 16
    override fun getScrollableBlockIncrement(r: java.awt.Rectangle, o: Int, d: Int) = r.height
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false

    /** Pushes everything to the top. */
    fun finish() {
        add(JPanel().apply { isOpaque = false }, GridBagConstraints().apply { gridx = 0; gridy = row++; weighty = 1.0 })
    }
}

fun muted(text: String): JLabel = JLabel(text).apply {
    foreground = UIManager.getColor("Label.disabledForeground")
    font = font.deriveFont(font.size2D - 1f)
}

/** A small filled circle, for presence and connection state. */
class Dot(var color: Color, private val size: Int = 10) : Icon {
    override fun paintIcon(c: Component?, g: java.awt.Graphics, x: Int, y: Int) {
        val g2 = g.create() as java.awt.Graphics2D
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.fillOval(x, y + 1, size, size)
        g2.dispose()
    }

    override fun getIconWidth() = size
    override fun getIconHeight() = size + 2
}

object Palette {
    val online = Color(0x2E, 0xA0, 0x43)
    val away = Color(0xE0, 0x9A, 0x1A)
    val busy = Color(0xD0, 0x3B, 0x3B)
    val offline = Color(0x99, 0x99, 0x99)
}

fun presenceColor(show: Show?, online: Boolean): Color = when {
    !online -> Palette.offline
    show == Show.AWAY || show == Show.XA -> Palette.away
    show == Show.DND -> Palette.busy
    else -> Palette.online
}

fun stateText(state: ConnectionState): Pair<String, Color> = when (state) {
    is ConnectionState.Online -> "Online as ${state.jid}" to Palette.online
    is ConnectionState.Connecting -> (if (state.attempt <= 1) "Connecting…" else "Reconnecting (attempt ${state.attempt})…") to Palette.away
    is ConnectionState.Backoff -> "Connection lost, retrying in ${(state.delayMillis + 999) / 1000}s" to Palette.away
    ConnectionState.WaitingForNetwork -> "Waiting for the network…" to Palette.offline
    ConnectionState.Paused -> "Paused" to Palette.offline
    is ConnectionState.Terminal -> "Stopped: ${describe(state.error)}" to Palette.busy
    ConnectionState.Idle -> "Not connected" to Palette.offline
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

fun JComponent.padded(top: Int, left: Int, bottom: Int, right: Int): JComponent = apply {
    border = BorderFactory.createEmptyBorder(top, left, bottom, right)
}

fun JComponent.fixedWidth(width: Int): JComponent = apply {
    preferredSize = Dimension(width, preferredSize.height)
}

fun htmlEscape(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\n' -> append("<br>")
            else -> append(c)
        }
    }
}

fun Color.hex(): String = String.format("#%02x%02x%02x", red, green, blue)
