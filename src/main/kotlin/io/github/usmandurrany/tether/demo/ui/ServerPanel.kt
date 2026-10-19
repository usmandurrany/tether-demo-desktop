package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.XmppSession
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.PushState
import io.github.usmandurrany.tether.im.PushTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * What the server offers on this session, which features Tether turned on,
 * and push registration (XEP-0357). Tether only enables what the server
 * advertises, so this is also a quick way to check a new server.
 */
class ServerPanel(private val session: DemoSession, private val uiScope: CoroutineScope) : JPanel(BorderLayout()) {
    private val summary = JLabel()
    private val features = JTextArea().apply { isEditable = false; lineWrap = false }

    private val pushService = JTextField().apply { putClientProperty("JTextField.placeholderText", "pubsub.example.com or the push app server's JID") }
    private val pushNode = JTextField().apply { putClientProperty("JTextField.placeholderText", "Node name for this device") }
    private val pushOptions = JTextArea(3, 20).apply { putClientProperty("JTextArea.placeholderText", "key=value per line, e.g. device_id=<token>") }
    private val pushCreate = JCheckBox("Create the node first (MongooseIM)")
    private val pushState = JLabel()

    init {
        val form = Form()
        form.section("Session")
        form.wide(summary)
        form.wide(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JButton("Check connection now").apply {
                    toolTipText = "Pings the server at once, or skips the wait before the next retry (client.wake())."
                    addActionListener { session.client.wake() }
                })
            },
        )

        form.section("Push notifications (XEP-0357)")
        form.wide(muted("<html><body style='width:380px'>Desktop apps rarely need push; this shows how a mobile app registers its FCM or APNs token.</body></html>"))
        form.field("Service", pushService)
        form.field("Node", pushNode)
        form.field("Options", JScrollPane(pushOptions).apply { preferredSize = Dimension(200, 64) })
        form.wide(pushCreate)
        form.wide(pushState)
        form.wide(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JButton("Register").apply { addActionListener { register() } })
                add(javax.swing.Box.createHorizontalStrut(8))
                add(JButton("Unregister").apply { addActionListener { uiScope.launch { runCatching { session.im.push.unregister() } } } })
            },
        )
        form.finish()

        val featureBox = JPanel(BorderLayout(0, 6)).apply {
            add(JLabel("Advertised features"), BorderLayout.NORTH)
            add(JScrollPane(features), BorderLayout.CENTER)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        add(JPanel(GridLayout(1, 2)).apply {
            add(JScrollPane(form.padded(12, 12, 12, 12)).apply { border = null })
            add(featureBox)
        }, BorderLayout.CENTER)
        bind()
    }

    private fun bind() {
        val im = session.im
        uiScope.launch {
            combine(session.client.session, session.client.state, im.carbons.enabled, im.history.available) { live, state, carbons, history ->
                Snapshot(live, state, carbons, history)
            }.collect { show(it) }
        }
        uiScope.launch {
            im.push.state.collect { state ->
                pushState.text = when (state) {
                    PushState.Off -> "Not registered"
                    PushState.Unsupported -> "This server doesn't offer push (XEP-0357)"
                    is PushState.Pending -> "Registering ${state.target.node}…"
                    is PushState.On -> "Registered: ${state.target.node} on ${state.target.service}"
                    is PushState.Failed -> "The server refused: ${state.condition ?: "error"}"
                }
            }
        }
    }

    private data class Snapshot(val live: XmppSession?, val state: ConnectionState, val carbons: Boolean, val history: Boolean)

    private fun show(s: Snapshot) {
        val live = s.live
        fun yes(on: Boolean) = if (on) "on" else "off"
        summary.text = if (live == null) {
            "<html>No live session (${htmlEscape(stateText(s.state).first)}).</html>"
        } else {
            val caps = live.capabilities
            val identity = caps.server.identities.firstOrNull()?.let { listOfNotNull(it.name, "${it.category}/${it.type}").joinToString(" ") }
            "<html><table cellpadding='1'>" +
                row("Bound address", live.jid.toString()) +
                row("Server", identity ?: "unknown") +
                row("Resumed session", if (live.resumed) "yes" else "no") +
                row("Stream Management (XEP-0198)", yes(live.streamManagement != null)) +
                row("Message carbons (XEP-0280)", yes(s.carbons)) +
                row("Message archive (XEP-0313)", if (s.history) "available" else "not offered") +
                row("Client state indication (XEP-0352)", yes(caps.hasStreamFeature("csi", "urn:xmpp:csi:0"))) +
                "</table></html>"
        }
        features.text = if (live == null) "" else buildString {
            val caps = live.capabilities
            appendLine("Stream features")
            caps.streamFeatures.elements.forEach { appendLine("  ${it.name}  (${it.namespace})") }
            appendLine()
            appendLine("Server (disco#info)")
            caps.server.features.sorted().forEach { appendLine("  $it") }
            appendLine()
            appendLine("Your account (disco#info)")
            caps.account.features.sorted().forEach { appendLine("  $it") }
        }
        features.caretPosition = 0
    }

    private fun row(label: String, value: String) = "<tr><td>${htmlEscape(label)}</td><td>&nbsp;&nbsp;<b>${htmlEscape(value)}</b></td></tr>"

    private fun register() {
        val service = pushService.text.trim()
        val node = pushNode.text.trim()
        if (service.isEmpty() || node.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Push needs a service and a node.", "Push", JOptionPane.WARNING_MESSAGE)
            return
        }
        val options = pushOptions.text.lines().mapNotNull { line ->
            val key = line.substringBefore('=', "").trim()
            if (key.isEmpty()) null else key to line.substringAfter('=').trim()
        }.toMap()
        uiScope.launch { runCatching { session.im.push.register(PushTarget(service, node, options, pushCreate.isSelected)) } }
    }
}
