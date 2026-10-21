package io.github.usmandurrany.tether.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.core.FullJid
import io.github.usmandurrany.tether.demo.app.AppViewModel
import io.github.usmandurrany.tether.demo.ui.chats.ChatItem
import io.github.usmandurrany.tether.demo.ui.chats.ChatsActions
import io.github.usmandurrany.tether.demo.ui.chats.ChatsScreen
import io.github.usmandurrany.tether.demo.ui.chats.ChatsViewModel
import io.github.usmandurrany.tether.demo.ui.chats.ContactRequest
import io.github.usmandurrany.tether.demo.ui.components.Images
import io.github.usmandurrany.tether.demo.ui.home.Destination
import io.github.usmandurrany.tether.demo.ui.home.HomeActions
import io.github.usmandurrany.tether.demo.ui.home.HomeScreen
import io.github.usmandurrany.tether.demo.ui.home.HomeViewModel
import io.github.usmandurrany.tether.demo.ui.log.LogScreen
import io.github.usmandurrany.tether.demo.ui.log.LogViewModel
import io.github.usmandurrany.tether.demo.ui.server.ServerScreen
import io.github.usmandurrany.tether.demo.ui.server.ServerViewModel
import io.github.usmandurrany.tether.demo.ui.signin.SignInScreen
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme
import io.github.usmandurrany.tether.im.Message
import io.github.usmandurrany.tether.im.MessageData
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.im.Profile
import io.github.usmandurrany.tether.im.PushState
import io.github.usmandurrany.tether.im.Show
import org.jetbrains.skia.EncodedImageFormat
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Draws every screen with sample data into build/screenshots, like the
 * Android demo's screenshot tests. No server or display is needed:
 * the stateless screens get their state directly.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val noChatActions = object : ChatsActions {}

    // --- Sample data ----------------------------------------------------------------

    private val me = BareJid.parse("bob@demo.test")
    private val alice = BareJid.parse("alice@demo.test")
    private val carol = BareJid.parse("carol@demo.test")
    private val dan = BareJid.parse("dan@demo.test")
    private val erin = BareJid.parse("erin@demo.test")
    private val photoUrl = "https://photos.example.com/office-view.jpg"

    /** Today (or [daysAgo] days back) at a fixed time, so the pictures don't change with the clock. */
    private fun at(hour: Int, minute: Int, daysAgo: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private var nextId = 0
    private fun msg(peer: BareJid, outgoing: Boolean, body: String, time: Long, status: MessageStatus = MessageStatus.READ, data: MessageData? = null) =
        Message(
            id = "m${nextId++}", peer = peer, outgoing = outgoing, body = body, timestamp = time, status = status,
            payloads = listOfNotNull(data?.toElement()),
        )

    private val location = Attachments.location(51.50135, -0.14189, "Buckingham Palace")

    private val aliceMessages = listOf(
        msg(alice, false, "Hey Bob! Are we still on for the demo tomorrow?", at(18, 42, daysAgo = 1)),
        msg(alice, true, "Yes! I just finished the new desktop build. It finally resizes properly, so nothing gets cut off when the window is small.", at(18, 45, daysAgo = 1)),
        msg(alice, false, "Amazing, can't wait to see it", at(18, 46, daysAgo = 1)),
        msg(alice, false, "The view from the office today", at(9, 12), data = Attachments.photo(photoUrl, 800, 533, 19_240, "image/jpeg")),
        msg(alice, true, "Wow, that's beautiful", at(9, 14)),
        msg(alice, false, Attachments.fallbackText(Attachments.location(51.50135, -0.14189, null)), at(9, 20), data = location),
        msg(alice, false, "Meet here at 10?", at(9, 20)),
        msg(alice, true, "Perfect, see you there.", at(9, 21), status = MessageStatus.DELIVERED),
    )

    private fun item(jid: BareJid, name: String, online: Boolean, show: Show?, presence: String, last: Message?, unread: Int = 0, typing: Boolean = false) =
        ChatItem(jid, name, null, online, show, presence, last, unread, typing, contact = null)

    private val chats = listOf(
        item(alice, "Alice Moreau", true, null, "Working from the studio", aliceMessages.last()),
        item(carol, "Carol Nguyen", true, Show.AWAY, "In a meeting", msg(carol, false, "No rush, end of day is fine.", at(8, 55)), unread = 2),
        item(dan, "Dan Brooks", false, null, "Last seen yesterday at 21:10", msg(dan, false, "Thanks, that fixed it!", at(16, 3, daysAgo = 2))),
        item(erin, "Erin Walsh", true, Show.DND, "Do not disturb", msg(erin, true, "Sent you the slides for Thursday", at(11, 30, daysAgo = 5)), typing = true),
    )

    private val request = ContactRequest(BareJid.parse("dave@demo.test"), "Hi Bob, it's Dave from the XMPP meetup")

    private fun chatsState(open: BareJid?) = ChatsViewModel.State(
        chats = chats,
        anyChats = true,
        requests = listOf(request),
        open = chats.firstOrNull { it.jid == open },
        openJid = open,
        messages = if (open == alice) aliceMessages else emptyList(),
    )

    private val home = HomeViewModel.State(
        account = me,
        connection = ConnectionState.Online(FullJid.parse("bob@demo.test/desktop")),
        profile = Profile(fullName = "Bob Carter"),
        statusMessage = "Polishing the desktop app",
        unread = 2,
    )

    // --- Screens --------------------------------------------------------------------

    @Composable
    private fun Home(destination: Destination = Destination.CHATS, dark: Boolean = false, accountMenu: Boolean = false, content: @Composable () -> Unit) {
        HomeScreen(home, destination, dark, HomeActions(), accountMenuOpen = accountMenu) { content() }
    }

    @Test
    fun chats() {
        shoot("chat-wide", 1100, 720) { Home { ChatsScreen(chatsState(alice), noChatActions) } }
        shoot("chat-wide-dark", 1100, 720, dark = true) { Home(dark = true) { ChatsScreen(chatsState(alice), noChatActions) } }
        shoot("chat-medium", 820, 560) { Home { ChatsScreen(chatsState(alice), noChatActions) } }
        shoot("list-narrow", 600, 640) { Home { ChatsScreen(chatsState(null), noChatActions) } }
        shoot("chat-narrow", 600, 640) { Home { ChatsScreen(chatsState(alice), noChatActions) } }
        shoot("nothing-open", 1100, 720) { Home { ChatsScreen(chatsState(null), noChatActions) } }
        shoot("message-menu", 1100, 720) { Home { ChatsScreen(chatsState(alice), noChatActions, menuOpenFor = aliceMessages[4].id) } }
        shoot("account-menu", 1100, 720) { Home(accountMenu = true) { ChatsScreen(chatsState(alice), noChatActions) } }
    }

    @Test
    fun signIn() {
        val state = AppViewModel.SignInState(ConnectionSettings(jid = "bob@demo.test"))
        shoot("sign-in-wide", 1100, 720) { SignInScreen(state) }
        shoot("sign-in-narrow", 600, 720) { SignInScreen(state) }
        shoot("sign-in-dark", 1100, 720, dark = true) { SignInScreen(state) }
    }

    @Test
    fun server() {
        val features = listOf(
            "Stream features",
            "  sm  (urn:xmpp:sm:3)",
            "  csi  (urn:xmpp:csi:0)",
            "  ver  (urn:xmpp:features:rosterver)",
            "",
            "Server (disco#info)",
            "  http://jabber.org/protocol/disco#info",
            "  urn:xmpp:carbons:2",
            "  urn:xmpp:mam:2",
            "  urn:xmpp:ping",
            "  vcard-temp",
        ).joinToString("\n")
        val state = ServerViewModel.State(
            session = ServerViewModel.SessionInfo("bob@demo.test/desktop", "Prosody server/im", resumed = false, streamManagement = true, csi = true, features = features),
            connection = home.connection,
            carbons = true,
            history = true,
            push = PushState.Off,
        )
        shoot("server", 1100, 720) { Home(Destination.SERVER) { ServerScreen(state) } }
    }

    @Test
    fun log() {
        val day = java.time.LocalDate.now()
        val lines = listOf(
            "${day}T09:20:58.104Z D xmpp bob@demo.test: Online as bob@demo.test/desktop",
            "${day}T09:21:02.311Z D xmpp bob@demo.test ← <message from='alice@demo.test/phone' to='bob@demo.test' type='chat' id='a91f'><body>Meet here at 10?</body><markable xmlns='urn:xmpp:chat-markers:0'/></message>",
            "${day}T09:21:02.315Z D xmpp bob@demo.test → <r xmlns='urn:xmpp:sm:3'/>",
            "${day}T09:21:02.402Z D xmpp bob@demo.test ← <a xmlns='urn:xmpp:sm:3' h='42'/>",
            "${day}T09:21:03.020Z D xmpp bob@demo.test → <message to='alice@demo.test' type='chat' id='b1c7'><displayed xmlns='urn:xmpp:chat-markers:0' id='a91f'/></message>",
            "${day}T09:21:04.512Z D xmpp bob@demo.test → <message to='alice@demo.test' type='chat' id='b1c8'><body>Perfect, see you there.</body><request xmlns='urn:xmpp:receipts'/><markable xmlns='urn:xmpp:chat-markers:0'/></message>",
            "${day}T09:21:04.733Z D xmpp bob@demo.test ← <message from='alice@demo.test/phone' to='bob@demo.test/desktop' id='a920'><received xmlns='urn:xmpp:receipts' id='b1c8'/></message>",
            "${day}T09:21:30.001Z D xmpp bob@demo.test → <iq type='get' id='p17'><ping xmlns='urn:xmpp:ping'/></iq>",
            "${day}T09:21:30.054Z D xmpp bob@demo.test ← <iq type='result' id='p17' from='demo.test'/>",
        )
        shoot("log", 1100, 720) { Home(Destination.LOG) { LogScreen(LogViewModel.State(lines)) } }
    }

    // --- Rendering ------------------------------------------------------------------

    /** Renders [content] at [width]×[height] window points, at twice the pixels for sharp text. */
    private fun shoot(name: String, width: Int, height: Int, dark: Boolean = false, content: @Composable () -> Unit) {
        Images.preload(photoUrl, samplePhoto())
        val scale = 2f
        val scene = ImageComposeScene((width * scale).toInt(), (height * scale).toInt(), Density(scale)) {
            TetherTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        try {
            // A few frames a second apart, so effects run and animations settle.
            var image = scene.render(0)
            for (frame in 1..5) image = scene.render(frame * 1_000_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("Couldn't encode $name")
            File(out, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
    }

    /** A made-up landscape for the photo message. */
    private fun samplePhoto(): ByteArray {
        val w = 800
        val h = 533
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, Color(0xFFB37A), 0f, h * 0.7f, Color(0x6E7FD8))
        g.fillRect(0, 0, w, h)
        g.color = Color(0xFFE7B0)
        g.fillOval(520, 150, 120, 120)
        g.color = Color(0x4B4F9E)
        g.fillPolygon(Polygon(intArrayOf(0, 180, 330, 480, 640, 800, 800, 0), intArrayOf(390, 250, 340, 230, 330, 270, h, h), 8))
        g.color = Color(0x2F3270)
        g.fillPolygon(Polygon(intArrayOf(0, 140, 300, 470, 620, 800, 800, 0), intArrayOf(450, 380, 440, 360, 430, 400, h, h), 8))
        g.color = Color(255, 255, 255, 90)
        g.stroke = BasicStroke(3f)
        g.drawArc(150, 110, 60, 30, 20, 140)
        g.drawArc(200, 90, 50, 26, 20, 140)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }
}
