package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.Conversation
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.Delivery
import io.github.usmandurrany.tether.im.ChatState
import io.github.usmandurrany.tether.im.ContactPresence
import io.github.usmandurrany.tether.im.RosterItem
import io.github.usmandurrany.tether.im.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** One row of the contact list: a roster entry, or someone we only have a conversation with. */
data class ContactRow(
    val jid: BareJid,
    val item: RosterItem?,
    val presences: List<ContactPresence>,
    val unread: Int,
) {
    val label: String get() = item?.name?.takeIf { it.isNotBlank() } ?: jid.toString()
    val online: Boolean get() = presences.isNotEmpty()
    val best: ContactPresence? get() = presences.maxByOrNull { it.priority }
}

/** Contacts on the left, the open conversation on the right. */
class ChatsPanel(private val session: DemoSession, private val uiScope: CoroutineScope) : JPanel(BorderLayout()) {
    private val model = DefaultListModel<ContactRow>()
    private val contacts = JList(model)
    private val requests = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    private val chatTitle = JLabel("Pick a contact").apply { font = font.deriveFont(Font.BOLD, font.size2D + 2f) }
    private val chatSubtitle = muted(" ")
    private val transcript = JEditorPane("text/html", "").apply {
        isEditable = false
        background = UIManager.getColor("List.background")
    }
    private val transcriptScroll = JScrollPane(transcript)
    private val loadOlder = JButton("Load older messages").apply { isVisible = false }
    private val input = JTextField().apply { putClientProperty("JTextField.placeholderText", "Write a message") }
    private val send = JButton("Send")

    private var peer: BareJid? = null
    private var typingJob: Job? = null
    private var composing = false
    private var renderedMessages: Any? = null

    init {
        contacts.selectionMode = ListSelectionModel.SINGLE_SELECTION
        contacts.cellRenderer = ContactRenderer()
        contacts.addListSelectionListener { if (!it.valueIsAdjusting) contacts.selectedValue?.let { row -> open(row.jid) } }
        contacts.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeMenu(e)
            override fun mouseReleased(e: MouseEvent) = maybeMenu(e)
        })

        val add = JButton("Add contact").apply { addActionListener { addContactDialog() } }
        val left = JPanel(BorderLayout()).apply {
            add(requests, BorderLayout.NORTH)
            add(JScrollPane(contacts).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply { add(add) }, BorderLayout.SOUTH)
            minimumSize = java.awt.Dimension(220, 100)
        }

        transcriptScroll.border = BorderFactory.createEmptyBorder()
        loadOlder.addActionListener { loadHistory() }
        send.addActionListener { sendCurrent() }
        input.addActionListener { sendCurrent() }
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = typed()
            override fun removeUpdate(e: DocumentEvent) = typed()
            override fun changedUpdate(e: DocumentEvent) {}
        })

        val header = JPanel(BorderLayout()).apply {
            add(JPanel(GridLayout(2, 1)).apply { isOpaque = false; add(chatTitle); add(chatSubtitle) }, BorderLayout.CENTER)
            add(loadOlder, BorderLayout.EAST)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 12, 8, 12),
            )
        }
        val composer = JPanel(BorderLayout(8, 0)).apply {
            add(input, BorderLayout.CENTER)
            add(send, BorderLayout.EAST)
            border = BorderFactory.createEmptyBorder(8, 12, 10, 12)
        }
        val right = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(transcriptScroll, BorderLayout.CENTER)
            add(composer, BorderLayout.SOUTH)
        }
        setComposerEnabled(false)
        transcript.text = "<html><body style='margin:16px; font-family:${UIManager.getFont("Label.font").family}'>" +
            "<span style='color:${UIManager.getColor("Label.disabledForeground").hex()}'>Choose a contact on the left, or add one.</span></body></html>"

        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right).apply { dividerLocation = 260; border = null }, BorderLayout.CENTER)
        bind()
    }

    private fun bind() {
        val im = session.im
        uiScope.launch {
            combine(im.roster.items, im.presences.contacts, session.conversations.all) { roster, presences, convs ->
                val jids = (roster.keys + convs.keys).filter { it != session.account }
                jids.map { ContactRow(it, roster[it], presences[it].orEmpty(), convs[it]?.unread ?: 0) }
                    .sortedWith(compareBy<ContactRow> { !it.online }.thenBy { it.label.lowercase() })
            }.collect(::showContacts)
        }
        uiScope.launch { session.conversations.all.collect { convs -> peer?.let { showConversation(convs[it]) } } }
        uiScope.launch { session.subscriptionRequests.collect(::showRequests) }
        uiScope.launch { im.history.available.collect { refreshLoadOlder() } }
        uiScope.launch { session.client.state.collect { refreshHeader() } }
    }

    private fun showContacts(rows: List<ContactRow>) {
        val selected = contacts.selectedValue?.jid ?: peer
        model.clear()
        rows.forEach(model::addElement)
        val index = rows.indexOfFirst { it.jid == selected }
        if (index >= 0) contacts.selectedIndex = index
        refreshHeader()
    }

    private fun showRequests(pending: Map<BareJid, String?>) {
        requests.removeAll()
        pending.forEach { (jid, note) ->
            val text = "<html><b>${htmlEscape(jid.toString())}</b> wants to see when you're online" +
                (note?.let { "<br>“${htmlEscape(it)}”" } ?: "") + "</html>"
            val row = JPanel(BorderLayout(6, 4)).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10),
                )
                background = UIManager.getColor("Panel.background").let { if (UIManager.getBoolean("laf.dark")) it.brighter() else it.darker() }
                add(JLabel(text), BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        isOpaque = false
                        add(JButton("Accept").apply { addActionListener { answer(jid, true) } })
                        add(javax.swing.Box.createHorizontalStrut(6))
                        add(JButton("Decline").apply { addActionListener { answer(jid, false) } })
                    },
                    BorderLayout.SOUTH,
                )
            }
            requests.add(row)
        }
        requests.revalidate()
        requests.repaint()
    }

    private fun answer(jid: BareJid, accept: Boolean) = uiScope.launch {
        runCatching { session.answerRequest(jid, accept) }.onFailure { error(it) }
    }

    private fun open(jid: BareJid) {
        if (peer == jid) return
        stopTyping()
        peer = jid
        session.conversations.open = jid
        renderedMessages = null
        setComposerEnabled(true)
        input.text = ""
        refreshHeader()
        showConversation(session.conversations.all.value[jid])
        val conv = session.conversations.all.value[jid]
        if (session.im.history.available.value && conv?.historyLoaded != true) loadHistory()
        input.requestFocusInWindow()
    }

    private fun refreshHeader() {
        val jid = peer ?: return
        val row = (0 until model.size()).map(model::get).firstOrNull { it.jid == jid }
        chatTitle.text = row?.label ?: jid.toString()
        val conv = session.conversations.all.value[jid]
        chatSubtitle.text = when {
            conv?.typing == ChatState.COMPOSING -> "typing…"
            conv?.typing == ChatState.PAUSED -> "stopped typing"
            row == null -> jid.toString()
            row.online -> listOfNotNull(row.best?.show?.wire ?: "online", row.best?.status).joinToString(" · ")
            row.item == null -> "not in your contacts"
            row.item.subscription == Subscription.NONE || row.item.subscription == Subscription.FROM ->
                if (row.item.pendingOut) "waiting for them to accept" else "you can't see their presence"
            else -> "offline"
        }
        refreshLoadOlder()
    }

    private fun refreshLoadOlder() {
        val conv = peer?.let { session.conversations.all.value[it] }
        loadOlder.isVisible = peer != null && session.im.history.available.value && conv?.historyComplete != true
    }

    private fun showConversation(conv: Conversation?) {
        refreshHeader()
        val messages = conv?.messages.orEmpty()
        if (renderedMessages === messages) return
        renderedMessages = messages
        val atBottom = transcriptScroll.verticalScrollBar.let { it.value + it.visibleAmount >= it.maximum - 24 }
        transcript.text = render(conv)
        if (atBottom || messages.lastOrNull()?.outgoing == true) {
            SwingUtilities.invokeLater { transcript.caretPosition = transcript.document.length }
        }
    }

    private fun render(conv: Conversation?): String {
        val dark = UIManager.getBoolean("laf.dark")
        val text = UIManager.getColor("Label.foreground").hex()
        val mutedColor = UIManager.getColor("Label.disabledForeground").hex()
        val bubbleIn = if (dark) "#3a3d41" else "#eef0f2"
        val bubbleOut = if (dark) "#1f4f7a" else "#d7e9fb"
        val base = UIManager.getFont("Label.font")
        val fmt = SimpleDateFormat("HH:mm")
        val day = SimpleDateFormat("d MMM, HH:mm")
        val now = System.currentTimeMillis()
        val body = StringBuilder()
        val messages = conv?.messages.orEmpty()
        if (messages.isEmpty()) {
            body.append("<p style='color:$mutedColor; margin:16px'>No messages yet. Say hello.</p>")
        }
        for (m in messages) {
            val time = (if (now - m.timeMillis > 20 * 3600_000) day else fmt).format(Date(m.timeMillis))
            val ticks = when (m.delivery) {
                Delivery.QUEUED -> "sending"
                Delivery.SENT -> "✓"
                Delivery.DELIVERED -> "✓✓"
                Delivery.FAILED -> "<font color='#d03b3b'>not delivered (${htmlEscape(m.error ?: "error")})</font>"
                null -> ""
            }
            val meta = listOfNotNull(time, ticks.takeIf { m.outgoing && it.isNotEmpty() }, "from another device".takeIf { m.carbon }).joinToString(" · ")
            val align = if (m.outgoing) "right" else "left"
            body.append(
                "<table width='100%' cellspacing='0' cellpadding='2'><tr><td align='$align'>" +
                    "<table cellspacing='0' cellpadding='7' bgcolor='${if (m.outgoing) bubbleOut else bubbleIn}'><tr><td>" +
                    "<span style='color:$text'>${htmlEscape(m.body)}</span><br>" +
                    "<span style='color:$mutedColor; font-size:${(base.size * 0.8).toInt()}pt'>$meta</span>" +
                    "</td></tr></table></td></tr></table>",
            )
        }
        return "<html><body style='font-family:${base.family}; font-size:${base.size}pt; margin:8px'>$body</body></html>"
    }

    private fun loadHistory() {
        val jid = peer ?: return
        loadOlder.isEnabled = false
        uiScope.launch {
            runCatching { session.loadHistory(jid) }.onFailure { error(it) }
            loadOlder.isEnabled = true
            refreshLoadOlder()
        }
    }

    private fun sendCurrent() {
        val jid = peer ?: return
        val text = input.text.trim()
        if (text.isEmpty()) return
        typingJob?.cancel()
        composing = false // the message itself carries <active/>
        input.text = ""
        uiScope.launch { runCatching { session.sendMessage(jid, text) }.onFailure { error(it) } }
    }

    /** XEP-0085: "composing" when typing starts, "paused" after a few quiet seconds. */
    private fun typed() {
        val jid = peer ?: return
        if (input.text.isEmpty()) {
            stopTyping()
            return
        }
        if (!composing) {
            composing = true
            uiScope.launch { session.sendTyping(jid, ChatState.COMPOSING) }
        }
        typingJob?.cancel()
        typingJob = uiScope.launch {
            delay(4_000)
            composing = false
            session.sendTyping(jid, ChatState.PAUSED)
        }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        val jid = peer
        if (composing && jid != null) uiScope.launch { session.sendTyping(jid, ChatState.ACTIVE) }
        composing = false
    }

    private fun setComposerEnabled(enabled: Boolean) {
        input.isEnabled = enabled
        send.isEnabled = enabled
    }

    private fun maybeMenu(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val index = contacts.locationToIndex(e.point).takeIf { it >= 0 && contacts.getCellBounds(it, it).contains(e.point) } ?: return
        contacts.selectedIndex = index
        val row = model[index]
        val roster = session.im.roster
        val presences = session.im.presences
        JPopupMenu().apply {
            add(JMenuItem(if (row.item == null) "Add to contacts…" else "Rename…").apply {
                addActionListener {
                    val name = JOptionPane.showInputDialog(this@ChatsPanel, "Name for ${row.jid}", row.item?.name.orEmpty()) ?: return@addActionListener
                    uiScope.launch { runCatching { roster.set(row.jid, name.trim().ifEmpty { null }, row.item?.groups.orEmpty()) }.onFailure { error(it) } }
                }
            })
            add(JMenuItem("Ask to see their presence").apply {
                addActionListener { uiScope.launch { runCatching { presences.request(row.jid) }.onFailure { error(it) } } }
            })
            add(JMenuItem("Share my presence").apply {
                addActionListener { uiScope.launch { runCatching { presences.approve(row.jid) }.onFailure { error(it) } } }
            })
            add(JMenuItem("Stop sharing my presence").apply {
                addActionListener { uiScope.launch { runCatching { presences.deny(row.jid) }.onFailure { error(it) } } }
            })
            if (row.item != null) {
                addSeparator()
                add(JMenuItem("Remove contact").apply {
                    addActionListener {
                        val ok = JOptionPane.showConfirmDialog(this@ChatsPanel, "Remove ${row.label} from your contacts?", "Remove contact", JOptionPane.OK_CANCEL_OPTION)
                        if (ok == JOptionPane.OK_OPTION) uiScope.launch { runCatching { roster.remove(row.jid) }.onFailure { error(it) } }
                    }
                })
            }
        }.show(contacts, e.x, e.y)
    }

    private fun addContactDialog() {
        val jid = JTextField(22).apply { putClientProperty("JTextField.placeholderText", "bob@example.com") }
        val name = JTextField(22).apply { putClientProperty("JTextField.placeholderText", "Optional") }
        val group = JTextField(22).apply { putClientProperty("JTextField.placeholderText", "Optional") }
        val form = Form().apply {
            field("Address", jid)
            field("Name", name)
            field("Group", group)
        }
        val ok = JOptionPane.showConfirmDialog(this, form, "Add contact", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (ok != JOptionPane.OK_OPTION) return
        val parsed = runCatching { BareJid.parse(jid.text.trim()) }.getOrNull()
        if (parsed?.local == null) {
            JOptionPane.showMessageDialog(this, "Enter a full address, like bob@example.com.", "Add contact", JOptionPane.WARNING_MESSAGE)
            return
        }
        uiScope.launch {
            runCatching { session.addContact(parsed, name.text.trim().ifEmpty { null }, group.text.trim().ifEmpty { null }) }.onFailure { error(it) }
        }
    }

    private fun error(e: Throwable) {
        val online = session.client.state.value is ConnectionState.Online
        val message = e.message ?: e::class.simpleName
        JOptionPane.showMessageDialog(this, if (online) message else "$message\n\nYou're not connected right now.", "Something went wrong", JOptionPane.WARNING_MESSAGE)
    }

    private class ContactRenderer : JPanel(BorderLayout(8, 0)), ListCellRenderer<ContactRow> {
        private val dot = Dot(Palette.offline)
        private val title = JLabel().apply { icon = dot; iconTextGap = 8 }
        private val detail = JLabel()
        private val badge = JLabel()

        init {
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            add(JPanel(GridLayout(2, 1)).apply { isOpaque = false; add(title); add(detail) }, BorderLayout.CENTER)
            add(badge, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(list: JList<out ContactRow>, row: ContactRow, index: Int, selected: Boolean, focused: Boolean): Component {
            dot.color = presenceColor(row.best?.show, row.online)
            title.text = row.label
            title.font = list.font.deriveFont(if (row.unread > 0) Font.BOLD else Font.PLAIN)
            detail.text = when {
                row.online -> row.best?.status ?: row.best?.show?.wire ?: "online"
                row.item == null -> "not in contacts"
                row.item.pendingOut -> "request sent"
                row.item.subscription == Subscription.NONE || row.item.subscription == Subscription.FROM -> "presence not shared"
                else -> "offline"
            }.let { if (row.label != row.jid.toString()) "${row.jid} · $it" else it }
            detail.font = list.font.deriveFont(list.font.size2D - 1f)
            badge.text = if (row.unread > 0) " ${row.unread} " else ""
            background = if (selected) list.selectionBackground else list.background
            val fg = if (selected) list.selectionForeground else list.foreground
            title.foreground = fg
            detail.foreground = if (selected) fg else UIManager.getColor("Label.disabledForeground")
            badge.foreground = if (selected) fg else Palette.online
            badge.font = list.font.deriveFont(Font.BOLD)
            isOpaque = true
            return this
        }
    }
}
