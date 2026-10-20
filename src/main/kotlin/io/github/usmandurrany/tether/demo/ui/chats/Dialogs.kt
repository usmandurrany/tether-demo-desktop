package io.github.usmandurrany.tether.demo.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.usmandurrany.tether.demo.ui.signin.Field
import io.github.usmandurrany.tether.demo.ui.theme.MonoStyle

/** A dialog with a form, a main action and Cancel. [onConfirm] returns a problem to show, or null to close. */
@Composable
private fun FormDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> String?, content: @Composable () -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                problem = onConfirm()
                if (problem == null) onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun isLink(s: String) = s.startsWith("https://") || s.startsWith("http://")

@Composable
fun AddContactDialog(vm: ChatsViewModel, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    FormDialog("Add a contact", "Add", onDismiss, onConfirm = { vm.addContact(address, name, group) }) {
        Field("Address", address, { address = it }, placeholder = "bob@example.com")
        Field("Name", name, { name = it }, placeholder = "Optional", hint = "How they show in your list. Leave empty to use the name in their profile.")
        Field("Group", group, { group = it }, placeholder = "Optional")
    }
}

@Composable
fun PhotoDialog(vm: ChatsViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("https://picsum.photos/id/1015/800/533.jpg") }
    var caption by remember { mutableStateOf("") }
    FormDialog("Send a photo", "Send", onDismiss, onConfirm = {
        if (!isLink(url.trim())) "Enter a link that starts with https:// or http://." else null.also { vm.sendPhoto(url.trim(), caption.trim()) }
    }) {
        Field("Photo link", url, { url = it }, hint = "A photo already online. The demo reads its size and dimensions to send with it.")
        Field("Caption", caption, { caption = it }, placeholder = "Optional")
    }
}

@Composable
fun VideoDialog(vm: ChatsViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("https://download.samplelib.com/mp4/sample-5s.mp4") }
    var seconds by remember { mutableStateOf("5") }
    var caption by remember { mutableStateOf("") }
    FormDialog("Send a video", "Send", onDismiss, onConfirm = {
        if (!isLink(url.trim())) "Enter a link that starts with https:// or http://." else null.also { vm.sendVideo(url.trim(), seconds.trim().toIntOrNull(), caption.trim()) }
    }) {
        Field("Video link", url, { url = it })
        Field("Length in seconds", seconds, { seconds = it })
        Field("Caption", caption, { caption = it }, placeholder = "Optional")
    }
}

@Composable
fun LocationDialog(vm: ChatsViewModel, onDismiss: () -> Unit) {
    var lat by remember { mutableStateOf("51.50135") }
    var lon by remember { mutableStateOf("-0.14189") }
    var label by remember { mutableStateOf("Buckingham Palace") }
    FormDialog("Share a location", "Share", onDismiss, onConfirm = {
        val la = lat.trim().toDoubleOrNull()
        val lo = lon.trim().toDoubleOrNull()
        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
            "Enter a latitude (-90 to 90) and longitude (-180 to 180)."
        } else {
            null.also { vm.sendLocation(la, lo, label.trim().ifEmpty { null }) }
        }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Latitude", lat, { lat = it }, modifier = Modifier.weight(1f))
            Field("Longitude", lon, { lon = it }, modifier = Modifier.weight(1f))
        }
        Field("Place name", label, { label = it })
    }
}

/** Any type and fields: the receiver shows them as they arrive, whatever they are. */
@Composable
fun CustomDataDialog(vm: ChatsViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("contact-card") }
    var fields by remember { mutableStateOf("name=Ada Lovelace\nphone=+44 20 7946 0000") }
    var caption by remember { mutableStateOf("") }
    FormDialog("Send custom data", "Send", onDismiss, onConfirm = { vm.sendCustom(type, fields, caption) }) {
        Field("Type", type, { type = it }, hint = "Letters, digits, - _ and . only.")
        OutlinedTextField(
            fields, { fields = it },
            label = { Text("Fields") },
            supportingText = { Text("One key=value per line.") },
            textStyle = MonoStyle,
            minLines = 3, maxLines = 6,
            modifier = Modifier.width(420.dp),
        )
        Field("Text", caption, { caption = it }, placeholder = "Optional")
    }
}
