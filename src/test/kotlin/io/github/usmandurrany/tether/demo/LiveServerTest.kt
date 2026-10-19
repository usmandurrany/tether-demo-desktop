package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.ConnectResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

        fun settings(jid: String) = ConnectionSettings(
            jid = jid,
            host = env("TETHER_HOST").orEmpty(),
            port = env("TETHER_PORT").orEmpty(),
            rememberLogin = true,
            allowScramSha1 = env("TETHER_ALLOW_SCRAM_SHA1") == "true",
        )

        runBlocking {
            suspend fun signIn(jid: String, password: String): DemoSession {
                var s = settings(jid)
                repeat(2) {
                    val session = DemoSession.create(s, password, emptyList())
                    when (val result = session.connect()) {
                        is ConnectResult.Connected -> return session
                        is ConnectResult.Failed -> {
                            session.disconnect()
                            val pin = DemoSession.refusedCertificate(result.error) ?: throw result.error
                            s = s.copy(pinnedCertificate = pin)
                        }
                        ConnectResult.Cancelled -> error("cancelled")
                    }
                }
                error("could not sign in")
            }

            val a = signIn(aJid, env("TETHER_A_PASSWORD")!!)
            val b = signIn(env("TETHER_B_JID")!!, env("TETHER_B_PASSWORD")!!)
            val bJid = b.account
            val aBare = a.account
            try {
                // Start clean, so the test can run again on the same accounts.
                if (bJid in a.im.roster.items.value) a.im.roster.remove(bJid)
                if (aBare in b.im.roster.items.value) b.im.roster.remove(aBare)
                withTimeout(10_000) { a.im.roster.items.first { bJid !in it } }
                withTimeout(10_000) { b.im.roster.items.first { aBare !in it } }

                // Subscription handshake: A adds B, B accepts (and asks back), A accepts.
                a.addContact(bJid, "Bob", "Friends")
                withTimeout(10_000) { b.subscriptionRequests.first { aBare in it } }
                b.answerRequest(aBare, accept = true)
                withTimeout(10_000) { a.subscriptionRequests.first { bJid in it } }
                a.answerRequest(bJid, accept = true)
                withTimeout(10_000) { a.im.presences.contacts.first { bJid in it } }
                withTimeout(10_000) { a.im.roster.items.first { it[bJid]?.name == "Bob" } }

                // A message, its server ack and B's delivery receipt.
                val text = "hello from the demo test ${System.nanoTime()}"
                a.sendMessage(bJid, text)
                withTimeout(10_000) { b.conversations.all.first { m -> m[aBare]?.messages?.any { it.body == text } == true } }
                withTimeout(10_000) {
                    a.conversations.all.first { m -> m[bJid]?.messages?.any { it.body == text && it.delivery == Delivery.DELIVERED } == true }
                }

                // The archive returns it too, without a duplicate in the list.
                if (a.im.history.available.value) {
                    delay(500)
                    a.loadHistory(bJid)
                    val conv = assertNotNull(a.conversations.all.value[bJid])
                    assertTrue(conv.historyLoaded)
                    assertEquals(1, conv.messages.count { it.body == text })
                }
                println("Server features: " + a.client.session.value?.capabilities?.account?.features)
            } finally {
                a.disconnect()
                b.logOut()
            }

            // A remembered login reconnects without the password.
            val keys = DemoSession.savedKeys(BareJid.parse(aJid))
            assertTrue(keys.isNotEmpty(), "login keys were saved")
            val again = DemoSession.create(a.settings, "", keys)
            assertIs<ConnectResult.Connected>(again.connect())
            again.logOut()
            assertTrue(DemoSession.savedKeys(BareJid.parse(aJid)).isEmpty(), "log out forgot the keys")
        }
    }
}
