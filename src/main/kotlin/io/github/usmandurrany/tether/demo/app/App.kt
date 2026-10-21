package io.github.usmandurrany.tether.demo.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.AppPaths
import io.github.usmandurrany.tether.demo.ViewModelScope
import io.github.usmandurrany.tether.demo.ui.home.HomeRoute
import io.github.usmandurrany.tether.demo.ui.signin.SignInScreen
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.MonoStyle
import io.github.usmandurrany.tether.demo.ui.theme.TetherTheme

/** Picks the sign-in page or the signed-in screens, and shows app-wide dialogs. */
@Composable
fun App(app: AppViewModel, onQuit: () -> Unit) {
    val dark by app.darkTheme.collectAsState()
    val session by app.session.collectAsState()
    val trust by app.trustQuestion.collectAsState()
    var showFiles by remember { mutableStateOf(false) }

    TetherTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = session
            if (current == null) {
                val signIn by app.signIn.collectAsState()
                SignInScreen(
                    signIn,
                    onEdit = app::edit,
                    onConnect = app::connect,
                    onToggleTheme = app::toggleTheme,
                    onShowFiles = { showFiles = true },
                )
            } else {
                // One set of ViewModels per signed-in session, cleared when it ends.
                ViewModelScope(current) {
                    HomeRoute(
                        session = current,
                        dark = dark,
                        onToggleTheme = app::toggleTheme,
                        onDisconnect = { app.signOut(forget = false) },
                        onLogOut = { app.signOut(forget = true) },
                        onReconnect = app::reconnect,
                        onShowFiles = { showFiles = true },
                        onQuit = onQuit,
                    )
                }
            }
        }
        trust?.let { question -> TrustDialog(question.fingerprint, app::answerTrust) }
        if (showFiles) FilesDialog { showFiles = false }
    }
}

@Composable
private fun TrustDialog(fingerprint: String, answer: (Boolean) -> Unit) {
    val pretty = fingerprint.chunked(2).joinToString(":").uppercase()
    AlertDialog(
        onDismissRequest = { answer(false) },
        icon = { Icon(AppIcons.Info, null) },
        title = { Text("Trust this server's certificate?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The certificate isn't signed by an authority this computer trusts. That's normal for a self-signed test server. " +
                        "Only trust it if you recognise this SHA-256 fingerprint:",
                )
                SelectionContainer { Text(pretty, style = MonoStyle, modifier = Modifier) }
            }
        },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text("Trust and connect") } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun FilesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Folder, null) },
        title = { Text("Where your files are") },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Settings", style = MaterialTheme.typography.titleSmall)
                    Text(AppPaths.settingsFile.toString(), style = MonoStyle)
                    Text("Tether state and saved login", style = MaterialTheme.typography.titleSmall)
                    Text(AppPaths.tetherStateFile.toString(), style = MonoStyle)
                    Text("Chats kept on this computer", style = MaterialTheme.typography.titleSmall)
                    Text(AppPaths.dataDir.resolve("messages").toString(), style = MonoStyle)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
