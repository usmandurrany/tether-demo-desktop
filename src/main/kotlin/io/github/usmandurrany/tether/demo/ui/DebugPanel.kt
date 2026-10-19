package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.debug.LogEntry
import io.github.usmandurrany.tether.debug.LogFormatter
import io.github.usmandurrany.tether.demo.DemoSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The in-app log screen: every stanza sent and received (credentials always
 * redacted by tether-debug) plus connection changes, from a [io.github.usmandurrany.tether.debug.MemorySink].
 */
@OptIn(FlowPreview::class)
class DebugPanel(private val session: DemoSession, private val uiScope: CoroutineScope) : JPanel(BorderLayout()) {
    private val logArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size - 1)
    }
    private val filter = JTextField(18).apply { putClientProperty("JTextField.placeholderText", "Filter, e.g. roster or message") }
    private val pretty = JCheckBox("Indent XML")
    private val follow = JCheckBox("Follow", true)
    private val stanzasOnly = JCheckBox("Stanzas only")
    private val remote = muted("")
    private var entries: List<LogEntry> = emptyList()

    init {
        val clear = JButton("Clear").apply { addActionListener { session.logs.clear() } }
        val copy = JButton("Copy all").apply {
            addActionListener { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(logArea.text), null) }
        }
        listOf(pretty, stanzasOnly).forEach { it.addActionListener { render() } }
        filter.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = render()
            override fun removeUpdate(e: DocumentEvent) = render()
            override fun changedUpdate(e: DocumentEvent) {}
        })
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            add(JLabel("Filter"))
            add(filter)
            add(stanzasOnly)
            add(pretty)
            add(follow)
            add(clear)
            add(copy)
        }
        val top = JPanel(BorderLayout()).apply {
            add(bar, BorderLayout.CENTER)
            add(remote.padded(0, 12, 6, 12), BorderLayout.SOUTH)
        }
        add(top, BorderLayout.NORTH)
        add(JScrollPane(logArea).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)

        uiScope.launch {
            session.logs.entries.sample(250).collect {
                entries = it
                render()
            }
        }
        val sink = session.remoteLogs
        if (sink == null) {
            remote.text = "Uploading to a log portal is off. Set a portal URL and key on the settings page to turn it on."
        } else {
            uiScope.launch {
                sink.stats.collect { s ->
                    remote.text = "Log portal: ${s.uploaded} uploaded, ${s.queued} queued, ${s.dropped} dropped" +
                        (s.lastError?.let { " · last error: $it" } ?: "") + " · session ${sink.sessionId}"
                }
            }
        }
    }

    private fun render() {
        val formatter = LogFormatter(pretty = pretty.isSelected)
        val needle = filter.text.trim().lowercase()
        val shown = entries.asSequence()
            .filter { !stanzasOnly.isSelected || it.isStanza }
            .map(formatter::format)
            .filter { needle.isEmpty() || it.lowercase().contains(needle) }
            .joinToString("\n")
        if (shown == logArea.text) return
        val keep = logArea.caretPosition
        logArea.text = shown
        logArea.caretPosition = if (follow.isSelected) logArea.document.length else keep.coerceAtMost(logArea.document.length)
    }
}
