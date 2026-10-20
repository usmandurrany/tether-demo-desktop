package io.github.usmandurrany.tether.demo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.ProfilePictures
import io.github.usmandurrany.tether.demo.ui.components.Avatar
import io.github.usmandurrany.tether.demo.ui.signin.Field
import io.github.usmandurrany.tether.im.Avatar
import io.github.usmandurrany.tether.im.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Edits your profile (vCard): name, nickname, email, phone, an "about" line
 * and a picture. Contacts see it, and your picture shows in their lists.
 */
@Composable
fun ProfileDialog(home: HomeViewModel, current: Profile?, fallbackName: String, onDismiss: () -> Unit) {
    val p = current ?: Profile()
    var fullName by remember { mutableStateOf(p.fullName.orEmpty()) }
    var nickname by remember { mutableStateOf(p.nickname.orEmpty()) }
    var email by remember { mutableStateOf(p.email.orEmpty()) }
    var phone by remember { mutableStateOf(p.phone.orEmpty()) }
    var note by remember { mutableStateOf(p.note.orEmpty()) }
    var avatar by remember { mutableStateOf<Avatar?>(p.avatar) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column(Modifier.width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Contacts see this, and your picture shows next to your name in their lists.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(avatar, fullName.ifBlank { fallbackName }, 80.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val file = pickImage() ?: return@launch
                                    val picked = withContext(Dispatchers.IO) { runCatching { ProfilePictures.fromFile(file) }.getOrNull() }
                                    if (picked == null) problem = "That file isn't an image this app can read." else { avatar = picked; problem = null }
                                }
                            }) { Text("Choose picture…") }
                            if (avatar != null) TextButton(onClick = { avatar = null }) { Text("Remove") }
                        }
                        Text(
                            avatar?.let { "JPEG, ${it.data.size / 1024} KB. Pictures are made 192×192 so they fit in one message." } ?: "No picture",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Field("Full name", fullName, { fullName = it })
                Field("Nickname", nickname, { nickname = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Email", email, { email = it }, modifier = Modifier.weight(1f))
                    Field("Phone", phone, { phone = it }, modifier = Modifier.weight(1f))
                }
                Field("About", note, { note = it })
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    fun String.value() = trim().ifEmpty { null }
                    home.saveProfile {
                        it.copy(fullName = fullName.value(), nickname = nickname.value(), email = email.value(), phone = phone.value(), note = note.value(), avatar = avatar)
                    }.onSuccess { onDismiss() }.onFailure { problem = "The server didn't save it: ${it.message}" }
                    saving = false
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The system's file picker, filtered to images. */
private fun pickImage(): File? {
    val dialog = FileDialog(null as Frame?, "Choose a picture", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in setOf("jpg", "jpeg", "png", "gif", "bmp") }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}
