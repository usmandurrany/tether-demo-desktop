package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.core.BareJid
import io.github.usmandurrany.tether.core.XmppConfig
import io.github.usmandurrany.tether.sasl.SaslConfig
import io.github.usmandurrany.tether.sasl.ScramKeys
import io.github.usmandurrany.tether.transport.TlsConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

/**
 * Everything on the connection settings page. Works with any standards-compliant
 * XMPP server: only the address is required, the rest is optional.
 *
 * The password is deliberately not part of this class, so it can never be saved.
 */
data class ConnectionSettings(
    /** Your address, such as `alice@example.com`. */
    val jid: String = "",
    /** Leave empty to let the server pick one. */
    val resource: String = "",
    /** Leave empty to find the server through DNS SRV records of the domain. */
    val host: String = "",
    val port: String = "",
    /** TLS from the first byte (usually port 5223) instead of STARTTLS. */
    val directTls: Boolean = false,
    /** SHA-256 fingerprint of a self-signed certificate to trust. Colons and case don't matter. */
    val pinnedCertificate: String = "",
    val allowPlain: Boolean = false,
    val allowScramSha1: Boolean = false,
    /** Keep the SCRAM login keys (never the password) so the next start needs no password. */
    val rememberLogin: Boolean = true,
    /** Print every stanza (credentials redacted) to the terminal. */
    val logToConsole: Boolean = false,
    /** Replace message bodies with their length in every log. */
    val redactBodies: Boolean = false,
    /** Optional Tether log portal (github.com/usmandurrany/tether-debug). */
    val portalUrl: String = "",
    val portalKey: String = "",
    val portalLabel: String = "",
    val darkTheme: Boolean = false,
    /** Random, created once per install; the log portal groups uploads by it. */
    val installId: String = UUID.randomUUID().toString(),
) {
    /** Checks the form. Returns a message for the first problem, or null when it can connect. */
    fun validate(): String? {
        val parsed = runCatching { BareJid.parse(jid.trim()) }.getOrNull()
        if (parsed?.local == null) return "Enter your full address, like alice@example.com."
        if (port.isNotBlank()) {
            val p = port.trim().toIntOrNull() ?: return "The port must be a number."
            if (p !in 1..65535) return "The port must be between 1 and 65535."
            if (host.isBlank()) return "A port needs a host too."
        }
        if (directTls && host.isBlank()) return "Direct TLS needs a host."
        if (pinnedCertificate.isNotBlank() && normalizedPin() == null) {
            return "The certificate fingerprint must be 64 hex characters (SHA-256)."
        }
        if (portalUrl.isNotBlank()) {
            if (!portalUrl.startsWith("http://") && !portalUrl.startsWith("https://")) return "The log portal URL must start with http:// or https://."
            if (portalKey.isBlank()) return "The log portal needs its ingest key."
        }
        return null
    }

    fun account(): BareJid = BareJid.parse(jid.trim())

    fun normalizedPin(): String? =
        pinnedCertificate.trim().lowercase().replace(":", "").replace(" ", "").takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    /** Turns the form into the SDK's configuration. */
    fun toXmppConfig(password: String?, savedKeys: List<ScramKeys>): XmppConfig = XmppConfig(
        jid = account(),
        password = password?.takeIf { it.isNotEmpty() },
        // A freshly typed password wins; otherwise log in with the keys saved last time.
        scramKeys = if (password.isNullOrEmpty()) savedKeys else emptyList(),
        resource = resource.trim().ifEmpty { null },
        host = host.trim().ifEmpty { null },
        port = port.trim().toIntOrNull(),
        directTls = directTls,
        tls = TlsConfig(pinnedCertificateSha256 = setOfNotNull(normalizedPin())),
        sasl = SaslConfig(allowPlain = allowPlain, allowScramSha1 = allowScramSha1),
    )

    fun save(file: Path = AppPaths.settingsFile) {
        val p = Properties()
        p["jid"] = jid.trim()
        p["resource"] = resource.trim()
        p["host"] = host.trim()
        p["port"] = port.trim()
        p["directTls"] = directTls.toString()
        p["pinnedCertificate"] = pinnedCertificate.trim()
        p["allowPlain"] = allowPlain.toString()
        p["allowScramSha1"] = allowScramSha1.toString()
        p["rememberLogin"] = rememberLogin.toString()
        p["logToConsole"] = logToConsole.toString()
        p["redactBodies"] = redactBodies.toString()
        p["portalUrl"] = portalUrl.trim()
        p["portalKey"] = portalKey.trim()
        p["portalLabel"] = portalLabel.trim()
        p["darkTheme"] = darkTheme.toString()
        p["installId"] = installId
        Files.createDirectories(file.toAbsolutePath().parent)
        Files.newOutputStream(file).use { p.store(it, "Tether demo settings (no password is ever stored here)") }
    }

    companion object {
        fun load(file: Path = AppPaths.settingsFile): ConnectionSettings {
            if (!Files.exists(file)) return ConnectionSettings()
            val p = Properties().apply { runCatching { Files.newInputStream(file).use { load(it) } } }
            fun s(key: String) = p.getProperty(key).orEmpty()
            fun b(key: String, default: Boolean) = p.getProperty(key)?.toBooleanStrictOrNull() ?: default
            val defaults = ConnectionSettings()
            return ConnectionSettings(
                jid = s("jid"),
                resource = s("resource"),
                host = s("host"),
                port = s("port"),
                directTls = b("directTls", false),
                pinnedCertificate = s("pinnedCertificate"),
                allowPlain = b("allowPlain", false),
                allowScramSha1 = b("allowScramSha1", false),
                rememberLogin = b("rememberLogin", true),
                logToConsole = b("logToConsole", false),
                redactBodies = b("redactBodies", false),
                portalUrl = s("portalUrl"),
                portalKey = s("portalKey"),
                portalLabel = s("portalLabel"),
                darkTheme = b("darkTheme", false),
                installId = s("installId").ifEmpty { defaults.installId },
            )
        }
    }
}
