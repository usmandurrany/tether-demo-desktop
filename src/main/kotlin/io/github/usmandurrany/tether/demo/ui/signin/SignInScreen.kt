package io.github.usmandurrany.tether.demo.ui.signin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.app.AppViewModel
import io.github.usmandurrany.tether.demo.ui.components.BrandMark
import io.github.usmandurrany.tether.demo.ui.components.ToolButton
import io.github.usmandurrany.tether.demo.ui.components.onEnter
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.isDark

/**
 * The sign-in page. Address, password and Connect come first; everything
 * else (servers without DNS SRV records, self-signed certificates, old login
 * mechanisms, chat options and debug logging) sits in sections that open on
 * demand, and a section opens by itself when one of its settings is in use.
 * Wide windows get a welcome panel on the left.
 */
@Composable
fun SignInScreen(
    state: AppViewModel.SignInState,
    onEdit: (ConnectionSettings) -> Unit = {},
    onConnect: (ConnectionSettings, password: String) -> Unit = { _, _ -> },
    onToggleTheme: () -> Unit = {},
    onShowFiles: () -> Unit = {},
) {
    var password by remember { mutableStateOf("") }
    val form = state.settings
    val edit = onEdit
    val submit = {
        if (!state.busy) {
            onConnect(form, password)
            password = ""
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val wide = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Welcome(Modifier.width((maxWidth * 0.42f).coerceIn(360.dp, 560.dp)).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BrandMark(44.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sign in", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Use any XMPP account. Works with any standards-compliant server: ejabberd, Prosody, Openfire, MongooseIM and more.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Field("Address", form.jid, { edit(form.copy(jid = it)) }, placeholder = "alice@example.com", enabled = !state.busy, onEnter = submit)
                        PasswordField(password, { password = it }, enabled = !state.busy, onEnter = submit)
                        CheckRow(
                            "Remember me", form.rememberLogin, { edit(form.copy(rememberLogin = it)) },
                            "Keeps the login keys on this computer, never the password, and signs in by itself next time.",
                        )
                        Button(
                            onClick = submit,
                            enabled = !state.busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text("Connecting…")
                            } else {
                                Text("Connect")
                            }
                        }
                        state.notice?.let { Notice(it.text, it.error) }
                        Spacer(Modifier.height(8.dp))
                        Text("Advanced", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AdvancedOptions(form, edit)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    ToolButton(AppIcons.Folder, "Where are my files?", onShowFiles)
                    ToolButton(
                        if (MaterialTheme.colorScheme.isDark) AppIcons.Sun else AppIcons.Moon,
                        "Switch between light and dark",
                        onToggleTheme,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedOptions(form: ConnectionSettings, edit: (ConnectionSettings) -> Unit) {
    val defaults = remember { ConnectionSettings() }
    Column {
        Disclosure(
            "Server", "Host, port, resource and direct TLS",
            initiallyOpen = form.host.isNotBlank() || form.port.isNotBlank() || form.resource.isNotBlank() || form.directTls,
        ) {
            Field("Resource", form.resource, { edit(form.copy(resource = it)) }, placeholder = "Assigned by the server", hint = "A name for this device. Leave empty and the server picks one.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Host", form.host, { edit(form.copy(host = it)) }, placeholder = "Found through DNS SRV", modifier = Modifier.weight(3f))
                Field("Port", form.port, { edit(form.copy(port = it)) }, placeholder = "5222", modifier = Modifier.weight(1.2f))
            }
            Hint("Only if the domain has no DNS SRV records, or to reach a server by IP or name.")
            CheckRow("Direct TLS", form.directTls, { edit(form.copy(directTls = it)) }, "TLS from the first byte instead of STARTTLS, usually on port 5223.")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Disclosure(
            "Security", "Self-signed certificates and older login methods",
            initiallyOpen = form.pinnedCertificate.isNotBlank() || form.allowPlain || form.allowScramSha1,
        ) {
            Field(
                "Trusted certificate", form.pinnedCertificate, { edit(form.copy(pinnedCertificate = it)) }, placeholder = "SHA-256 fingerprint",
                hint = "For a self-signed server certificate. Connect offers to trust it and fills this in.",
            )
            CheckRow("Allow PLAIN login", form.allowPlain, { edit(form.copy(allowPlain = it)) }, "Still only sent over verified TLS.")
            CheckRow("Allow SCRAM-SHA-1", form.allowScramSha1, { edit(form.copy(allowScramSha1 = it)) }, "For older servers such as Prosody 0.12.")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Disclosure(
            "Chats", "Contact requests, history and profiles",
            initiallyOpen = form.autoAcceptContacts != defaults.autoAcceptContacts || form.keepMessages != defaults.keepMessages || form.profiles != defaults.profiles,
        ) {
            CheckRow(
                "Accept contact requests automatically", form.autoAcceptContacts, { edit(form.copy(autoAcceptContacts = it)) },
                "Anyone who adds you sees when you're online, and is added back. Off by default.",
            )
            CheckRow(
                "Keep chats on this computer", form.keepMessages, { edit(form.copy(keepMessages = it)) },
                "Ticks, read state, deletions and photos survive a restart. Off keeps them in memory only.",
            )
            CheckRow(
                "Profiles and pictures", form.profiles, { edit(form.copy(profiles = it)) },
                "Your name and picture for contacts, and theirs in your lists (vCard). On by default.",
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Disclosure(
            "Debug logging", "Terminal output and the log portal",
            initiallyOpen = form.logToConsole || form.redactBodies || form.portalUrl.isNotBlank(),
        ) {
            CheckRow("Print stanzas to the terminal", form.logToConsole, { edit(form.copy(logToConsole = it)) })
            CheckRow("Hide message bodies in logs", form.redactBodies, { edit(form.copy(redactBodies = it)) })
            Field(
                "Log portal URL", form.portalUrl, { edit(form.copy(portalUrl = it)) }, placeholder = "https://logs.example.com (optional)",
                hint = "A tether-debug log portal to upload logs to, for testing rounds.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Ingest key", form.portalKey, { edit(form.copy(portalKey = it)) }, secret = true, modifier = Modifier.weight(1f))
                Field("Label", form.portalLabel, { edit(form.copy(portalLabel = it)) }, placeholder = "e.g. beta-1", modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A section that opens and closes under a clickable header row. */
@Composable
private fun Disclosure(title: String, summary: String, initiallyOpen: Boolean, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                AppIcons.ChevronRight, contentDescription = if (open) "Close" else "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f),
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    secret: Boolean = false,
    onEnter: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { p -> { Text(p) } },
        supportingText = hint?.let { h -> { Text(h) } },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth().let { if (onEnter != null) it.onEnter(onEnter) else it },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, enabled: Boolean, onEnter: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text("Password") },
        placeholder = { Text("Leave empty to use the saved login") },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { ToolButton(if (shown) AppIcons.EyeOff else AppIcons.Eye, if (shown) "Hide password" else "Show password", { shown = !shown }) },
        modifier = Modifier.fillMaxWidth().onEnter(onEnter),
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked, onChange)
        Column(Modifier.padding(top = 12.dp, end = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Hint(it) }
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Notice(text: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Info, null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The left half of the page in wide windows: what the demo shows. */
@Composable
private fun Welcome(modifier: Modifier) {
    val points = listOf(
        "Chats with sent, delivered and read ticks, typing and history",
        "Photos, videos, locations and any custom data",
        "Contacts, presence, last seen and profile pictures",
        "Stream resumption, carbons and push, when the server has them",
    )
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF6E6CF0), Color(0xFF342F9C))))) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 0.dp).size(360.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        Box(Modifier.align(Alignment.BottomStart).size(300.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 48.dp).widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Chat, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Tether", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                "A lean XMPP SDK for every platform. This demo runs it on the desktop.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
            )
            Spacer(Modifier.height(8.dp))
            for (p in points) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Text(p, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }
}
