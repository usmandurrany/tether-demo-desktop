package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.Attachments
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.ContactPresence
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.im.Profile
import io.github.usmandurrany.tether.im.RosterItem
import io.github.usmandurrany.tether.im.Subscription
import kotlinx.coroutines.CoroutineScope
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
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import javax.swing.text.Document
import javax.swing.text.html.HTMLEditorKit
import java.awt.Desktop
import java.awt.Image
import java.awt.Toolkit
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Hashtable

/** One row of the contact list: a roster entry, or someone we only have a conversation with. */
data class ContactRow(
    val jid: BareJid,
    val item: RosterItem?,
    val presences: List<ContactPresence>,
    val unread: Int,
    /** Their vCard, once fetched: a name to fall back on, and a picture. */
    val profile: Profile? = null,
) {
    val label: String get() = item?.name?.takeIf { it.isNotBlank() } ?: profile?.displayName ?: jid.toString()
    val online: Boolean get() = presences.isNotEmpty()
    val best: ContactPresence? get() = presences.maxByOrNull { it.priority }
}

/** Contacts on the left, the open conversation on the right. */
class ChatsPanel(private val session: DemoSession, private val uiScope: CoroutineScope) : JPanel(BorderLayout()) {
    private val model = DefaultListModel<ContactRow>()
    private val contacts = JList(model)
    private val requests = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    private val titleAvatar = AvatarIcon(36)
    private val titleAvatarLabel = JLabel(titleAvatar).apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 10); isVisible = false }
    private val chatTitle = JLabel("Pick a contact").apply { font = font.deriveFont(Font.BOLD, font.size2D + 2f) }
    private val chatSubtitle = muted(" ")
    /** Photos load once and stay cached, so redrawing the chat does not reload them. */
    private val images = Hashtable<URL, Image>()
    private val transcript = JEditorPane().apply {
        editorKit = object : HTMLEditorKit() {
            override fun createDefaultDocument(): Document = super.createDefaultDocument().apply { putProperty("imageCache", images) }
        }
        isEditable = false
        background = UIManager.getColor("List.background")
    }
    private val transcriptScroll = JScrollPane(transcript)
    private val loadOlder = JButton("Load older messages").apply { isVisible = false }
    private val input = JTextField().apply { putClientProperty("JTextField.placeholderText", "Write a message") }
    private val send = JButton("Send")
    private val attach = JButton("Attach")

    private val chat = session.chat
    private var peer: BareJid? = null
    private var renderedMessages: Any? = null

    /** Chats whose whole archive is loaded. */
    private val historyComplete = HashSet<BareJid>()

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
        attach.addActionListener { attachMenu().let { it.show(attach, 0, -it.preferredSize.height) } }
        transcript.addHyperlinkListener { if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) clicked(it.description) }
        input.addActionListener { sendCurrent() }
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = typed()
            override fun removeUpdate(e: DocumentEvent) = typed()
            override fun changedUpdate(e: DocumentEvent) {}
        })

        val header = JPanel(BorderLayout()).apply {
            add(titleAvatarLabel, BorderLayout.WEST)
            add(JPanel(GridLayout(2, 1)).apply { isOpaque = false; add(chatTitle); add(chatSubtitle) }, BorderLayout.CENTER)
            add(loadOlder, BorderLayout.EAST)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 12, 8, 12),
            )
        }
        val composer = JPanel(BorderLayout(8, 0)).apply {
            add(attach, BorderLayout.WEST)
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
            combine(im.roster.items, im.presences.contacts, chat.conversations, chat.profiles) { roster, presences, convs, profiles ->
                val unread = convs.associate { it.peer to it.unreadCount }
                val jids = (roster.keys + unread.keys).filter { it != session.account }
                jids.map { ContactRow(it, roster[it], presences[it].orEmpty(), unread[it] ?: 0, profiles[it]) }
                    .sortedWith(compareBy<ContactRow> { !it.online }.thenBy { it.label.lowercase() })
            }.collect(::showContacts)
        }
        uiScope.launch { chat.allMessages.collect { all -> peer?.let { showConversation(all[it].orEmpty()) } } }
        uiScope.launch { chat.typingContacts.collect { refreshHeader() } }
        uiScope.launch { chat.contacts.collect { refreshHeader() } } // last seen arrives here
        uiScope.launch { combine(chat.contactRequests, session.requestNotes) { r, n -> r to n }.collect { (r, n) -> showRequests(r, n) } }
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

    private fun showRequests(pending: Set<BareJid>, notes: Map<BareJid, String>) {
        requests.removeAll()
        pending.forEach { jid ->
            val note = notes[jid]
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
        // The SDK marks an open chat read (and tells the contact) while the window has focus,
        // loads its newest history the first time, and stops our typing when it closes.
        peer?.let(chat::closeChat)
        peer = jid
        chat.openChat(jid)
        renderedMessages = null
        setComposerEnabled(true)
        input.text = ""
        refreshHeader()
        showConversation(chat.allMessages.value[jid].orEmpty())
        input.requestFocusInWindow()
    }

    private fun refreshHeader() {
        val jid = peer ?: return
        val row = (0 until model.size()).map(model::get).firstOrNull { it.jid == jid }
        chatTitle.text = row?.label ?: jid.toString()
        titleAvatar.avatar = row?.profile?.avatar
        titleAvatar.name = chatTitle.text
        titleAvatarLabel.isVisible = true
        titleAvatarLabel.repaint()
        chatSubtitle.text = when {
            jid in chat.typingContacts.value -> "typing…"
            row == null -> jid.toString()
            row.online -> listOfNotNull(row.best?.show?.wire ?: "online", row.best?.status).joinToString(" · ")
            row.item == null -> "not in your contacts"
            row.item.subscription == Subscription.NONE || row.item.subscription == Subscription.FROM ->
                if (row.item.pendingOut) "waiting for them to accept" else "you can't see their presence"
            else -> chat.contacts.value.firstOrNull { it.jid == jid }?.lastSeen?.let { "last seen ${lastSeenText(it)}" } ?: "offline"
        }
        refreshLoadOlder()
    }

    private fun refreshLoadOlder() {
        loadOlder.isVisible = peer != null && session.im.history.available.value && peer !in historyComplete
    }

    private fun showConversation(messages: List<Message>) {
        refreshHeader()
        if (renderedMessages == messages) return
        renderedMessages = messages
        val atBottom = transcriptScroll.verticalScrollBar.let { it.value + it.visibleAmount >= it.maximum - 24 }
        transcript.text = render(messages)
        if (atBottom || messages.lastOrNull()?.outgoing == true) {
            SwingUtilities.invokeLater { transcript.caretPosition = transcript.document.length }
        }
    }

    private fun render(messages: List<Message>): String {
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
        if (messages.isEmpty()) {
            body.append("<p style='color:$mutedColor; margin:16px'>No messages yet. Say hello.</p>")
        }
        for (m in messages) {
            val time = (if (now - m.timestamp > 20 * 3600_000) day else fmt).format(Date(m.timestamp))
            val ticks = when (m.status) {
                MessageStatus.SENDING -> "sending"
                MessageStatus.SENT -> "✓"
                MessageStatus.DELIVERED -> "✓✓"
                MessageStatus.READ -> "<font color='${Palette.online.hex()}'>✓✓ read</font>"
                MessageStatus.FAILED -> "<font color='#d03b3b'>not delivered (${htmlEscape(m.error ?: "error")})</font>"
                MessageStatus.UNREAD -> ""
            }
            // Delete links: for everyone on our own messages, for me on any.
            val key = URLEncoder.encode(m.id, "UTF-8")
            val dir = if (m.outgoing) "out" else "in"
            val actions = if (m.deleted) {
                "<a href='tether:me/$dir/$key'>remove</a>"
            } else {
                listOfNotNull(
                    "<a href='tether:everyone/$key'>delete for everyone</a>".takeIf { m.outgoing && m.status != MessageStatus.FAILED },
                    "<a href='tether:me/$dir/$key'>delete for me</a>",
                ).joinToString(" ")
            }
            val meta = listOfNotNull(time, ticks.takeIf { m.outgoing && it.isNotEmpty() }, "from another device".takeIf { m.fromOtherDevice }, actions).joinToString(" · ")
            val align = if (m.outgoing) "right" else "left"
            val content = if (m.deleted) {
                "<i style='color:$mutedColor'>This message was deleted</i>"
            } else {
                val parts = Attachments.of(m).map { renderContent(it, mutedColor) }
                // A caption that only repeats the link (sent by an app without one) is not shown twice.
                val caption = m.body.takeIf { it.isNotBlank() && Attachments.of(m).none { c -> m.body == fallbackOf(c) } }
                (parts + listOfNotNull(caption?.let { "<span style='color:$text'>${htmlEscape(it)}</span>" })).joinToString("<br>")
            }
            body.append(
                "<table width='100%' cellspacing='0' cellpadding='2'><tr><td align='$align'>" +
                    "<table cellspacing='0' cellpadding='7' bgcolor='${if (m.outgoing) bubbleOut else bubbleIn}'><tr><td>" +
                    "$content<br>" +
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
            runCatching { if (!chat.loadOlder(jid)) historyComplete += jid }.onFailure { error(it) }
            loadOlder.isEnabled = true
            refreshLoadOlder()
        }
    }

    private fun sendCurrent() {
        val jid = peer ?: return
        val text = input.text.trim()
        if (text.isEmpty()) return
        input.text = ""
        chat.send(jid, text) // shows at once as "sending"; works offline too
    }

    /** The SDK sends "typing", then "paused" after a few quiet seconds. */
    private fun typed() {
        val jid = peer ?: return
        if (input.text.isNotEmpty()) chat.typing(jid)
    }

    private fun setComposerEnabled(enabled: Boolean) {
        input.isEnabled = enabled
        send.isEnabled = enabled
        attach.isEnabled = enabled
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
        private val avatar = AvatarIcon(34)
        private val picture = JLabel(avatar)
        private val title = JLabel()
        private val detail = JLabel()
        private val badge = JLabel()

        init {
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            add(picture, BorderLayout.WEST)
            add(JPanel(GridLayout(2, 1)).apply { isOpaque = false; add(title); add(detail) }, BorderLayout.CENTER)
            add(badge, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(list: JList<out ContactRow>, row: ContactRow, index: Int, selected: Boolean, focused: Boolean): Component {
            avatar.avatar = row.profile?.avatar
            avatar.name = row.label
            avatar.dot = presenceColor(row.best?.show, row.online)
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

    // --- Custom data: photos, videos, locations and anything else ------------------------

    private fun fallbackOf(content: Attachments.Content): String? = when (content) {
        is Attachments.Photo -> content.url
        is Attachments.Video -> content.url
        is Attachments.Location -> Attachments.fallbackText(Attachments.location(content.latitude, content.longitude, null))
        is Attachments.Other -> null
    }

    private fun renderContent(content: Attachments.Content, mutedColor: String): String = when (content) {
        is Attachments.Photo -> {
            val url = runCatching { URI(content.url).toURL() }.getOrNull()?.takeIf { it.protocol == "https" || it.protocol == "http" }
            if (url == null) {
                "[photo with an unusable link]"
            } else {
                images.computeIfAbsent(url) { Toolkit.getDefaultToolkit().createImage(it) }
                // The sender's size lets the bubble take its shape before the photo loads.
                val w = content.width
                val h = content.height
                val size = if (w != null && h != null && w > 0 && h > 0) {
                    val scale = minOf(1.0, 260.0 / w, 260.0 / h)
                    " width='${(w * scale).toInt()}' height='${(h * scale).toInt()}'"
                } else {
                    ""
                }
                val info = listOfNotNull("Photo", if (w != null && h != null) "$w×$h" else null, Attachments.size(content.bytes)).joinToString(" · ")
                "<a href='${htmlEscape(content.url)}'><img src='${htmlEscape(content.url)}'$size border='0'></a><br>" +
                    "<span style='color:$mutedColor'>$info</span>"
            }
        }
        is Attachments.Video -> {
            val info = listOfNotNull(Attachments.duration(content.seconds), Attachments.size(content.bytes)).joinToString(" · ")
            "<a href='${htmlEscape(content.url)}'>▶ Play video</a>" + if (info.isNotEmpty()) " <span style='color:$mutedColor'>$info</span>" else ""
        }
        is Attachments.Location ->
            "<a href='${htmlEscape(content.mapUrl)}'>Location: ${htmlEscape(content.label ?: "open the map")}</a><br>" +
                "<span style='color:$mutedColor'>${"%.5f".format(content.latitude)}, ${"%.5f".format(content.longitude)}</span>"
        is Attachments.Other ->
            "<span style='color:$mutedColor'>Custom data <b>${htmlEscape(content.data.type)}</b>:</span><br>" +
                content.data.fields.entries.joinToString("<br>") { (k, v) -> "<tt>${htmlEscape(k)}</tt> = ${htmlEscape(v)}" }
    }

    private fun clicked(href: String?) {
        href ?: return
        if (href.startsWith("tether:")) {
            val parts = href.removePrefix("tether:").split("/")
            val messages = peer?.let { chat.allMessages.value[it] }.orEmpty()
            fun id(s: String) = URLDecoder.decode(s, "UTF-8")
            when (parts.firstOrNull()) {
                "everyone" -> messages.firstOrNull { it.outgoing && it.id == id(parts[1]) }?.let { m ->
                    val ok = JOptionPane.showConfirmDialog(this, "Delete this message for everyone?", "Delete", JOptionPane.OK_CANCEL_OPTION)
                    if (ok == JOptionPane.OK_OPTION && !chat.deleteForEveryone(m)) {
                        JOptionPane.showMessageDialog(this, "This message can't be deleted for everyone.")
                    }
                }
                "me" -> messages.firstOrNull { it.outgoing == (parts[1] == "out") && it.id == id(parts[2]) }?.let(chat::deleteForMe)
            }
            return
        }
        runCatching { Desktop.getDesktop().browse(URI(href)) }
            .onFailure { JOptionPane.showMessageDialog(this, "Couldn't open $href", "Open link", JOptionPane.WARNING_MESSAGE) }
    }

    private fun attachMenu(): JPopupMenu = JPopupMenu().apply {
        add(JMenuItem("Photo…").apply { addActionListener { photoDialog() } })
        add(JMenuItem("Video…").apply { addActionListener { videoDialog() } })
        add(JMenuItem("Location…").apply { addActionListener { locationDialog() } })
        addSeparator()
        add(JMenuItem("Custom data…").apply { addActionListener { customDialog() } })
    }

    /** Sends [data] with the caption as the body, or a link other XMPP apps can open when there is none. */
    private fun sendData(caption: String, data: MessageData) {
        val jid = peer ?: return
        runCatching { chat.send(jid, caption.ifBlank { Attachments.fallbackText(data) }, data) }
            .onFailure { JOptionPane.showMessageDialog(this, it.message, "Can't send", JOptionPane.WARNING_MESSAGE) }
    }

    private fun photoDialog() {
        val url = JTextField("https://picsum.photos/id/1015/800/533.jpg", 28)
        val caption = JTextField(28).apply { putClientProperty("JTextField.placeholderText", "Optional") }
        val form = Form().apply {
            field("Photo link", url, "A photo already online. The demo reads its size and dimensions to send with it.")
            field("Caption", caption)
        }
        if (JOptionPane.showConfirmDialog(this, form, "Send a photo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return
        val link = url.text.trim()
        if (!link.startsWith("https://") && !link.startsWith("http://")) return
        val text = caption.text.trim()
        uiScope.launch {
            val info = MediaProbe.photo(link)
            sendData(text, Attachments.photo(link, info?.width, info?.height, info?.bytes, info?.mime))
        }
    }

    private fun videoDialog() {
        val url = JTextField("https://download.samplelib.com/mp4/sample-5s.mp4", 28)
        val seconds = JTextField("5", 6)
        val caption = JTextField(28).apply { putClientProperty("JTextField.placeholderText", "Optional") }
        val form = Form().apply {
            field("Video link", url)
            field("Length (seconds)", seconds)
            field("Caption", caption)
        }
        if (JOptionPane.showConfirmDialog(this, form, "Send a video", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return
        val link = url.text.trim()
        if (!link.startsWith("https://") && !link.startsWith("http://")) return
        val text = caption.text.trim()
        uiScope.launch { sendData(text, Attachments.video(link, seconds.text.trim().toIntOrNull(), MediaProbe.size(link))) }
    }

    private fun locationDialog() {
        val lat = JTextField("51.50135", 12)
        val lon = JTextField("-0.14189", 12)
        val label = JTextField("Buckingham Palace", 24)
        val form = Form().apply {
            field("Latitude", lat)
            field("Longitude", lon)
            field("Place name", label)
        }
        if (JOptionPane.showConfirmDialog(this, form, "Share a location", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return
        val la = lat.text.trim().toDoubleOrNull()
        val lo = lon.text.trim().toDoubleOrNull()
        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
            JOptionPane.showMessageDialog(this, "Enter a latitude (-90 to 90) and longitude (-180 to 180).", "Share a location", JOptionPane.WARNING_MESSAGE)
            return
        }
        sendData("", Attachments.location(la, lo, label.text.trim().ifEmpty { null }))
    }

    /** Any type and fields: the receiver shows them as they arrive, whatever they are. */
    private fun customDialog() {
        val type = JTextField("contact-card", 20)
        val fields = JTextArea("name=Ada Lovelace\nphone=+44 20 7946 0000", 5, 28)
        val caption = JTextField(28).apply { putClientProperty("JTextField.placeholderText", "Optional") }
        val form = Form().apply {
            field("Type", type, "Letters, digits, - _ and . only.")
            field("Fields", JScrollPane(fields), "One key=value per line.")
            field("Text", caption)
        }
        if (JOptionPane.showConfirmDialog(this, form, "Send custom data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return
        val pairs = fields.text.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        runCatching { MessageData(type.text.trim(), pairs.toMap()) }
            .onSuccess { sendData(caption.text.trim(), it) }
            .onFailure { JOptionPane.showMessageDialog(this, it.message, "Send custom data", JOptionPane.WARNING_MESSAGE) }
    }

    private fun lastSeenText(at: Long): String {
        val ago = (System.currentTimeMillis() - at).coerceAtLeast(0) / 60_000
        return when {
            ago < 1 -> "just now"
            ago < 60 -> "$ago min ago"
            ago < 20 * 60 -> "at " + SimpleDateFormat("HH:mm").format(Date(at))
            else -> SimpleDateFormat("d MMM, HH:mm").format(Date(at))
        }
    }
}
