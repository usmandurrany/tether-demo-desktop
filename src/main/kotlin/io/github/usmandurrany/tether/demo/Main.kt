package io.github.usmandurrany.tether.demo

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.demo.ui.MainPage
import io.github.usmandurrany.tether.demo.ui.SettingsPage
import io.github.usmandurrany.tether.demo.ui.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

fun main() {
    // Crisp text on Linux desktops that don't announce their font settings to Java.
    if (System.getProperty("awt.useSystemAAFontSettings") == null) System.setProperty("awt.useSystemAAFontSettings", "lcd")
    System.setProperty("flatlaf.menuBarEmbedded", "false")
    val settings = ConnectionSettings.load()
    SwingUtilities.invokeLater {
        if (settings.darkTheme) FlatDarkLaf.setup() else FlatLightLaf.setup()
        AppWindow(settings).show()
    }
}

/** The window: the settings page until signed in, then the main page. */
class AppWindow(private var settings: ConnectionSettings) {
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val frame = JFrame("Tether demo")
    private val cards = CardLayout()
    private val root = JPanel(cards)
    private val settingsPage = SettingsPage(::connect)

    private var session: DemoSession? = null
    private var sessionUi: Job? = null
    private var mainPage: MainPage? = null

    fun show() {
        settingsPage.load(settings)
        root.add(settingsPage, SETTINGS)
        frame.contentPane = root
        frame.jMenuBar = menuBar()
        frame.minimumSize = Dimension(820, 560)
        frame.size = Dimension(1060, 720)
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = quit()

            // Focus is the desktop's version of "foreground": the client reports
            // inactive to the server (XEP-0352) while the window is in the background.
            override fun windowActivated(e: WindowEvent) {
                session?.client?.setForeground(true)
            }

            override fun windowDeactivated(e: WindowEvent) {
                session?.client?.setForeground(false)
            }
        })
        frame.isVisible = true
        settingsPage.focusFirstField()
        autoConnect()
    }

    /** With "remember me" and saved login keys, sign in straight away. */
    private fun autoConnect() {
        if (!settings.rememberLogin || settings.validate() != null) return
        ui.launch {
            if (DemoSession.savedKeys(settings.account()).isNotEmpty()) connect(settings, "")
        }
    }

    private fun connect(newSettings: ConnectionSettings, password: String) {
        settings = newSettings
        runCatching { settings.save() }
        settingsPage.setBusy(true)
        ui.launch {
            val keys = DemoSession.savedKeys(settings.account())
            if (password.isEmpty() && keys.isEmpty()) {
                settingsPage.setBusy(false)
                settingsPage.showStatus("Enter the password. There's no saved login for ${settings.account()} yet.", error = true)
                return@launch
            }
            val created = runCatching { DemoSession.create(settings, password, keys) }.getOrElse {
                settingsPage.setBusy(false)
                settingsPage.showStatus(describe(it), error = true)
                return@launch
            }
            when (val result = created.connect()) {
                is ConnectResult.Connected -> {
                    settingsPage.clearPassword()
                    settingsPage.setBusy(false)
                    showMain(created)
                }
                is ConnectResult.Failed -> {
                    // The client would keep retrying on its own; on the settings page it's clearer to stop and say why.
                    created.disconnect()
                    settingsPage.setBusy(false)
                    val fingerprint = result.untrustedCertificate
                    if (fingerprint != null && settings.normalizedPin() != fingerprint && askToTrust(fingerprint)) {
                        val pinned = settings.copy(pinnedCertificate = fingerprint)
                        settingsPage.load(pinned)
                        connect(pinned, password)
                        return@launch
                    }
                    val usedSavedKeys = password.isEmpty()
                    settingsPage.showStatus(
                        describe(result.error) + if (usedSavedKeys) " The saved login was used; type the password to sign in afresh." else "",
                        error = true,
                    )
                    settingsPage.focusFirstField()
                }
                ConnectResult.Cancelled -> {
                    created.disconnect()
                    settingsPage.setBusy(false)
                    settingsPage.showStatus("Cancelled.", error = false)
                }
            }
        }
    }

    private fun askToTrust(fingerprint: String): Boolean {
        val pretty = fingerprint.chunked(2).joinToString(":").uppercase()
        val answer = JOptionPane.showConfirmDialog(
            frame,
            "<html><body style='width:420px'>The server's certificate isn't signed by an authority this computer trusts. " +
                "That's normal for a self-signed test server.<br><br>SHA-256 fingerprint:<br><tt>$pretty</tt><br><br>" +
                "Trust this certificate for this server? Only say yes if you recognise the fingerprint.</body></html>",
            "Untrusted certificate",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        return answer == JOptionPane.YES_OPTION
    }

    private fun showMain(newSession: DemoSession) {
        session = newSession
        sessionUi = SupervisorJob(ui.coroutineContext[Job])
        val scope = CoroutineScope(ui.coroutineContext + sessionUi!!)
        val page = MainPage(
            newSession,
            scope,
            onDisconnect = { signOut(forget = false) },
            onLogOut = { signOut(forget = true) },
            onReconnect = { ui.launch { newSession.connect() } },
        )
        mainPage = page
        root.add(page, MAIN)
        cards.show(root, MAIN)
        newSession.client.setForeground(frame.isActive)
        frame.title = "Tether demo · ${newSession.account}"
    }

    private fun signOut(forget: Boolean) {
        val current = session ?: return
        ui.launch {
            if (forget) current.logOut() else current.disconnect()
            closeMain()
            settingsPage.showStatus(if (forget) "Logged out. The saved login was removed." else "Disconnected.", error = false)
            settingsPage.focusFirstField()
        }
    }

    private fun closeMain() {
        sessionUi?.cancel()
        mainPage?.let(root::remove)
        mainPage = null
        session = null
        cards.show(root, SETTINGS)
        frame.title = "Tether demo"
    }

    private fun quit() {
        val current = session
        frame.isVisible = false
        // A clean close lets the server mark us offline at once; Tether saves its state on the way.
        if (current != null) runBlocking { withTimeoutOrNull(5_000) { current.disconnect() } }
        frame.dispose()
        kotlin.system.exitProcess(0)
    }

    private fun menuBar() = JMenuBar().apply {
        add(JMenu("File").apply {
            add(JMenuItem("Quit").apply { addActionListener { quit() } })
        })
        add(JMenu("View").apply {
            add(JCheckBoxMenuItem("Dark theme", settings.darkTheme).apply {
                addActionListener {
                    settings = settings.copy(darkTheme = isSelected)
                    runCatching { settingsPage.read().copy(darkTheme = isSelected).save() }
                    if (isSelected) FlatDarkLaf.setup() else FlatLightLaf.setup()
                    FlatLaf.updateUI()
                }
            })
        })
        add(JMenu("Help").apply {
            add(JMenuItem("Where are my files?").apply {
                addActionListener {
                    JOptionPane.showMessageDialog(
                        frame,
                        "Settings: ${AppPaths.settingsFile}\nTether state and saved login: ${AppPaths.tetherStateFile}",
                        "Files",
                        JOptionPane.INFORMATION_MESSAGE,
                    )
                }
            })
        })
    }

    private companion object {
        const val SETTINGS = "settings"
        const val MAIN = "main"
    }
}
