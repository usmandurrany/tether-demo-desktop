package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.MessageStatus
import io.github.usmandurrany.tether.im.Profile
import io.github.usmandurrany.tether.im.data
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end check of [DemoSession] against a real server with two accounts.
 * Skipped unless these are set (in the environment or as -D system properties):
 *
 *   TETHER_A_JID, TETHER_A_PASSWORD, TETHER_B_JID, TETHER_B_PASSWORD
 *   optional: TETHER_HOST, TETHER_PORT (when the domain has no DNS SRV records),
 *   TETHER_ALLOW_SCRAM_SHA1=true (servers such as Prosody 0.12 that offer nothing newer)
 *
 * A self-signed certificate is trusted the same way the app does it: the first
 * attempt reports the fingerprint, the second pins it.
 */
class LiveServerTest {
    private fun env(name: String): String? = System.getProperty(name) ?: System.getenv(name)

    @Test
    fun twoAccountsChat() {
        val aJid = env("TETHER_A_JID") ?: return println("LiveServerTest skipped: TETHER_A_JID not set")
        val home = Files.createTempDirectory("tether-demo-test")
        System.setProperty("tether.demo.home", home.toString())

        fun settings(jid: String, autoAccept: Boolean = false) = ConnectionSettings(
            jid = jid,
            host = env("TETHER_HOST").orEmpty(),
            port = env("TETHER_PORT").orEmpty(),
            rememberLogin = true,
            autoAcceptContacts = autoAccept,
            allowScramSha1 = env("TETHER_ALLOW_SCRAM_SHA1") == "true",
        )

        runBlocking {
            suspend fun signIn(jid: String, password: String, autoAccept: Boolean = false): DemoSession {
                var s = settings(jid, autoAccept)
                repeat(2) {
                    val session = DemoSession.create(s, password, emptyList())
                    when (val result = session.connect()) {
                        is ConnectResult.Connected -> return session
                        is ConnectResult.Failed -> {
                            session.disconnect()
                            val pin = result.untrustedCertificate ?: throw result.error
                            s = s.copy(pinnedCertificate = pin)
                        }
                        ConnectResult.Cancelled -> error("cancelled")
                    }
                }
                error("could not sign in")
            }

            val a = signIn(aJid, env("TETHER_A_PASSWORD")!!)
            // B accepts contact requests automatically; A answers them by hand.
            val b = signIn(env("TETHER_B_JID")!!, env("TETHER_B_PASSWORD")!!, autoAccept = true)
            var bSignedIn = true
            val text = "hello from the demo test ${System.nanoTime()}"
            val bJid = b.account
            val aBare = a.account
            try {
                // Start clean, so the test can run again on the same accounts.
                if (bJid in a.im.roster.items.value) a.im.roster.remove(bJid)
                if (aBare in b.im.roster.items.value) b.im.roster.remove(aBare)
                withTimeout(10_000) { a.im.roster.items.first { bJid !in it } }
                withTimeout(10_000) { b.im.roster.items.first { aBare !in it } }

                // Subscription handshake: A adds B, B accepts automatically (and asks back), A accepts.
                a.addContact(bJid, "Bob", "Friends")
                withTimeout(10_000) { a.chat.contactRequests.first { bJid in it } }
                assertTrue(b.chat.contactRequests.value.isEmpty(), "auto-accept leaves no request waiting")
                a.answerRequest(bJid, accept = true)
                withTimeout(10_000) { a.im.presences.contacts.first { bJid in it } }
                withTimeout(10_000) { a.im.roster.items.first { it[bJid]?.name == "Bob" } }

                // A message, its server ack, B's delivery receipt, and B's read marker once B opens the chat.
                a.chat.send(bJid, text)
                withTimeout(10_000) { b.chat.messages(aBare).first { list -> list.any { it.body == text } } }
                withTimeout(10_000) { a.chat.messages(bJid).first { list -> list.any { it.body == text && it.status == MessageStatus.DELIVERED } } }
                b.chat.openChat(aBare)
                withTimeout(10_000) { a.chat.messages(bJid).first { list -> list.any { it.body == text && it.status == MessageStatus.READ } } }

                // The archive returns it too, without a duplicate in the list.
                if (a.im.history.available.value) {
                    delay(500)
                    a.chat.loadOlder(bJid)
                    assertEquals(1, a.chat.messages(bJid).value.count { it.body == text })
                }
 
                // Custom data: a photo's link, size and dimensions travel as their own tags, with the caption as the body.
                val photo = Attachments.photo("https://example.com/sunset.jpg", 800, 533, 48_213, "image/jpeg")
                val sent = a.chat.send(bJid, "Sunset", photo)
                val got = withTimeout(10_000) { b.chat.messages(aBare).first { list -> list.any { it.body == "Sunset" } } }.first { it.body == "Sunset" }
                assertEquals(photo, got.data(Attachments.PHOTO))
                assertEquals(listOf(Attachments.Photo("https://example.com/sunset.jpg", 800, 533, 48_213)), Attachments.of(got))
                val place = Attachments.location(51.50135, -0.14189, "Buckingham Palace")
                a.chat.send(bJid, "", place)
                val there = withTimeout(10_000) { b.chat.messages(aBare).first { list -> list.any { it.data(Attachments.LOCATION) != null } } }
                assertEquals(place, there.first { it.data(Attachments.LOCATION) != null }.data(Attachments.LOCATION))

                // Delete for everyone: B keeps a placeholder with no text or data.
                withTimeout(10_000) { a.chat.messages(bJid).first { list -> list.any { it.id == sent.id && it.status != MessageStatus.SENDING } } }
                assertTrue(a.chat.deleteForEveryone(a.chat.messages(bJid).value.first { it.id == sent.id }))
                val gone = withTimeout(10_000) { b.chat.messages(aBare).first { list -> list.any { it.deleted } } }.first { it.deleted }
                assertTrue(gone.body.isEmpty() && gone.payloads.isEmpty())

                // Delete for me: only B's copy goes.
                b.chat.deleteForMe(b.chat.messages(aBare).value.first { it.body == text })
                assertTrue(b.chat.messages(aBare).value.none { it.body == text })
                assertTrue(a.chat.messages(bJid).value.any { it.body == text })

                // Profiles: B publishes a name and picture; A's contact list shows the picture
                // (advertised in B's presence), and the profile has the rest.
                val picture = Avatar("image/png", ByteArray(2_000) { it.toByte() })
                b.chat.updateMyProfile(Profile(fullName = "Bob Builder", nickname = "Bobby", avatar = picture))
                assertEquals("Bobby", b.chat.myProfile.value?.nickname)
                withTimeout(10_000) { a.chat.contacts.first { list -> list.firstOrNull { it.jid == bJid }?.avatar == picture } }
                assertEquals("Bob Builder", a.chat.profile(bJid)?.fullName)

                // Last seen: A sees when B went offline.
                b.disconnect()
                bSignedIn = false
                withTimeout(10_000) { a.chat.contacts.first { list -> list.firstOrNull { it.jid == bJid }?.lastSeen != null } }

                println("Server features: " + a.client.session.value?.capabilities?.account?.features)
            } finally {
                a.disconnect()
                if (bSignedIn) b.logOut()
            }

            // A remembered login reconnects without the password.
            val keys = DemoSession.savedKeys(BareJid.parse(aJid))
            assertTrue(keys.isNotEmpty(), "login keys were saved")
            val again = DemoSession.create(a.settings, "", keys)
            // The chat is back from this computer's message store before connecting.
            withTimeout(10_000) { again.chat.messages(bJid).first { list -> list.any { it.body == text && it.status == MessageStatus.READ } } }
            assertIs<ConnectResult.Connected>(again.connect())
            again.logOut()
            assertTrue(DemoSession.savedKeys(BareJid.parse(aJid)).isEmpty(), "log out forgot the keys")
        }
    }
}
