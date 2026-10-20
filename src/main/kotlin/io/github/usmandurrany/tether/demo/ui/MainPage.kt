package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.Show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.UIManager

/** Everything shown while signed in: a status bar, then Chats, Server and Debug log tabs. */
class MainPage(
    private val session: DemoSession,
    private val uiScope: CoroutineScope,
    onDisconnect: () -> Unit,
    onLogOut: () -> Unit,
    onReconnect: () -> Unit,
) : JPanel(BorderLayout()) {
    private enum class Mode(val label: String, val show: Show?) {
        AVAILABLE("Available", null), CHAT("Free to chat", Show.CHAT), AWAY("Away", Show.AWAY),
        XA("Away for long", Show.XA), DND("Do not disturb", Show.DND), INVISIBLE("Invisible", null);

        override fun toString() = label
    }

    private val stateDot = Dot(Palette.offline)
    private val stateLabel = JLabel().apply { icon = stateDot; iconTextGap = 8 }
    private val mode = JComboBox(Mode.entries.toTypedArray())
    private val statusText = JTextField(16).apply { putClientProperty("JTextField.placeholderText", "Status message") }
    private val ownAvatar = AvatarIcon(26)
    private val profile = JButton("Profile…").apply { toolTipText = "Your name and picture, as contacts see them" }
    private val reconnect = JButton("Reconnect").apply { isVisible = false; addActionListener { onReconnect() } }

    init {
        val account = JLabel(session.account.toString(), ownAvatar, JLabel.LEADING).apply { font = font.deriveFont(Font.BOLD); iconTextGap = 8 }
        ownAvatar.name = session.account.local.orEmpty()
        profile.isVisible = session.chat.features.profiles
        profile.addActionListener { ProfileDialog(this, session, uiScope).isVisible = true }
        mode.addActionListener { applyPresence() }
        statusText.addActionListener { applyPresence() }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
            isOpaque = false
            add(account)
            add(profile)
            add(mode)
            add(statusText)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(stateLabel)
            add(reconnect)
            add(JButton("Disconnect").apply {
                toolTipText = "Sign out but keep the saved login (if Remember me was on)"
                addActionListener { onDisconnect() }
            })
            add(JButton("Log out").apply {
                toolTipText = "Sign out and forget the saved login"
                addActionListener { onLogOut() }
            })
        }
        val bar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 6, 8, 6),
            )
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }

        val tabs = JTabbedPane().apply {
            addTab("Chats", ChatsPanel(session, uiScope))
            addTab("Server", ServerPanel(session, uiScope))
            addTab("Debug log", DebugPanel(session, uiScope))
        }
        add(bar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)

        uiScope.launch {
            session.client.state.collect { state ->
                val (text, color) = stateText(state)
                stateLabel.text = if (state is ConnectionState.Online) "Online" else text
                stateLabel.toolTipText = text
                stateDot.color = color
                stateLabel.repaint()
                reconnect.isVisible = state is ConnectionState.Terminal
                profile.isEnabled = state is ConnectionState.Online
            }
        }
        uiScope.launch {
            session.chat.myProfile.collect { mine ->
                ownAvatar.avatar = mine?.avatar
                ownAvatar.name = mine?.displayName ?: session.account.local.orEmpty()
                account.repaint()
            }
        }
    }

    private fun applyPresence() {
        val selected = mode.selectedItem as Mode
        val status = statusText.text.trim().ifEmpty { null }
        uiScope.launch {
            runCatching { session.chat.setPresence(selected.show, status, invisible = selected == Mode.INVISIBLE) }
        }
    }
}
