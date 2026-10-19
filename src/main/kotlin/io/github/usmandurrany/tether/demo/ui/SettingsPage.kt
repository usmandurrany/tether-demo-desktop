package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.demo.ConnectionSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingConstants

/**
 * The connection settings page. Only the address and password are needed for
 * most servers; everything else covers servers without DNS SRV records,
 * self-signed certificates, old login mechanisms and debug logging.
 */
class SettingsPage(private val onConnect: (ConnectionSettings, String) -> Unit) : JPanel(BorderLayout()) {
    private val jid = JTextField(24).apply { putClientProperty("JTextField.placeholderText", "alice@example.com") }
    private val password = JPasswordField(24)
    private val remember = JCheckBox("Remember me (keeps login keys, never the password)")
    private val resource = JTextField().apply { putClientProperty("JTextField.placeholderText", "Assigned by the server") }

    private val host = JTextField().apply { putClientProperty("JTextField.placeholderText", "Found through DNS SRV") }
    private val port = JTextField().apply { putClientProperty("JTextField.placeholderText", "5222") }
    private val directTls = JCheckBox("Direct TLS (TLS from the first byte, usually port 5223)")

    private val pin = JTextField().apply { putClientProperty("JTextField.placeholderText", "SHA-256 fingerprint, only for self-signed certificates") }
    private val allowPlain = JCheckBox("Allow PLAIN login (still only over verified TLS)")
    private val allowSha1 = JCheckBox("Allow SCRAM-SHA-1 (for old servers)")

    private val console = JCheckBox("Print stanzas to the terminal")
    private val redact = JCheckBox("Hide message bodies in logs")
    private val portalUrl = JTextField().apply { putClientProperty("JTextField.placeholderText", "https://logs.example.com (optional)") }
    private val portalKey = JPasswordField()
    private val portalLabel = JTextField().apply { putClientProperty("JTextField.placeholderText", "Testing round, e.g. beta-1") }

    private val status = JLabel(" ").apply { horizontalAlignment = SwingConstants.LEFT }
    private val connect = JButton("Connect")
    private var current = ConnectionSettings()

    init {
        val form = Form()
        form.section("Account")
        form.field("Address", jid)
        form.field("Password", password, "Leave empty to use the login saved last time.")
        form.wide(remember)
        form.field("Resource", resource)

        form.section("Server")
        form.field("Host", host, "Only if the domain has no DNS SRV records, or to reach a server by IP or name.")
        form.field("Port", port)
        form.wide(directTls)

        form.section("Security")
        form.field("Trusted certificate", pin, "If the server uses a self-signed certificate, Connect offers to trust it and fills this in.")
        form.wide(allowPlain)
        form.wide(allowSha1)

        form.section("Debug logging")
        form.wide(console)
        form.wide(redact)
        form.field("Log portal URL", portalUrl, "A tether-debug log portal to upload logs to, for testing rounds.")
        form.field("Ingest key", portalKey)
        form.field("Label", portalLabel)
        form.finish()

        val title = JLabel("Connect to an XMPP server").apply { font = font.deriveFont(Font.BOLD, font.size2D + 6f) }
        val intro = muted("<html>Works with any standards-compliant server (ejabberd, Prosody, Openfire, MongooseIM…).</html>")
        val header = JPanel(BorderLayout(0, 4)).apply {
            add(title, BorderLayout.NORTH)
            add(intro, BorderLayout.CENTER)
            border = BorderFactory.createEmptyBorder(24, 4, 12, 4)
        }

        val column = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(form, BorderLayout.CENTER)
            maximumSize = Dimension(620, Int.MAX_VALUE)
            preferredSize = Dimension(560, form.preferredSize.height + 80)
        }
        // Centre a readable column instead of stretching fields across a wide window.
        val centred = JPanel(java.awt.GridBagLayout()).apply { add(column) }
        val scroll = JScrollPane(centred).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
        }

        connect.putClientProperty("JButton.buttonType", "default")
        connect.addActionListener { submit() }
        jid.addActionListener { submit() }
        password.addActionListener { submit() }
        val footer = JPanel(BorderLayout(12, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, javax.swing.UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(10, 16, 10, 16),
            )
            add(status, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { add(connect) }, BorderLayout.EAST)
        }

        add(scroll, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
    }

    fun load(settings: ConnectionSettings) {
        current = settings
        jid.text = settings.jid
        resource.text = settings.resource
        remember.isSelected = settings.rememberLogin
        host.text = settings.host
        port.text = settings.port
        directTls.isSelected = settings.directTls
        pin.text = settings.pinnedCertificate
        allowPlain.isSelected = settings.allowPlain
        allowSha1.isSelected = settings.allowScramSha1
        console.isSelected = settings.logToConsole
        redact.isSelected = settings.redactBodies
        portalUrl.text = settings.portalUrl
        portalKey.text = settings.portalKey
        portalLabel.text = settings.portalLabel
    }

    fun read(): ConnectionSettings = current.copy(
        jid = jid.text.trim(),
        resource = resource.text.trim(),
        rememberLogin = remember.isSelected,
        host = host.text.trim(),
        port = port.text.trim(),
        directTls = directTls.isSelected,
        pinnedCertificate = pin.text.trim(),
        allowPlain = allowPlain.isSelected,
        allowScramSha1 = allowSha1.isSelected,
        logToConsole = console.isSelected,
        redactBodies = redact.isSelected,
        portalUrl = portalUrl.text.trim(),
        portalKey = String(portalKey.password).trim(),
        portalLabel = portalLabel.text.trim(),
    )

    fun setBusy(busy: Boolean, message: String = if (busy) "Connecting…" else " ") {
        connect.isEnabled = !busy
        showStatus(message, error = false)
    }

    fun showStatus(message: String, error: Boolean) {
        status.text = "<html>${htmlEscape(message)}</html>"
        status.foreground = if (error) Palette.busy else javax.swing.UIManager.getColor("Label.foreground")
    }

    fun clearPassword() {
        password.text = ""
    }

    fun focusFirstField() {
        (if (jid.text.isBlank()) jid else password).requestFocusInWindow()
    }

    private fun submit() {
        if (!connect.isEnabled) return
        val settings = read()
        settings.validate()?.let { showStatus(it, error = true); return }
        onConnect(settings, String(password.password))
    }
}
