package io.github.usmandurrany.tether.demo.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.ui.components.ScreenTitle
import io.github.usmandurrany.tether.demo.ui.components.ToolButton
import io.github.usmandurrany.tether.demo.ui.theme.AppIcons
import io.github.usmandurrany.tether.demo.ui.theme.MonoStyle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(session: DemoSession) {
    val vm = viewModel { LogViewModel(session) }
    val state by vm.state.collectAsState()
    val o = state.options
    val list = rememberLazyListState()
    LaunchedEffect(state.lines.size, o.follow) {
        if (o.follow && state.lines.isNotEmpty()) list.scrollToItem(state.lines.lastIndex)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Debug log", "Every stanza sent and received, with credentials always redacted.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            TextField(
                o.filter, { f -> vm.update { it.copy(filter = f) } },
                placeholder = { Text("Filter, e.g. roster or message") },
                leadingIcon = { Icon(AppIcons.Search, null, Modifier.size(18.dp)) },
                trailingIcon = if (o.filter.isNotEmpty()) ({ ToolButton(AppIcons.Close, "Clear", { vm.update { it.copy(filter = "") } }) }) else null,
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 240.dp, max = 360.dp).height(48.dp),
            )
            Chip("Stanzas only", o.stanzasOnly) { vm.update { it.copy(stanzasOnly = !it.stanzasOnly) } }
            Chip("Indent XML", o.pretty) { vm.update { it.copy(pretty = !it.pretty) } }
            Chip("Follow", o.follow) { vm.update { it.copy(follow = !it.follow) } }
            Action("Clear", AppIcons.Trash, vm::clear)
            Action("Copy all", AppIcons.Copy) { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(state.lines.joinToString("\n")), null) }
        }
        Text(
            state.portal ?: "Uploading to a log portal is off. Set a portal URL and key on the sign-in page, under Debug logging, to turn it on.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box {
                if (state.lines.isEmpty()) {
                    Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                }
                SelectionContainer {
                    LazyColumn(state = list, contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.lines) { _, line -> Text(line, style = MonoStyle, modifier = Modifier.padding(vertical = 1.dp)) }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected, onClick, label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(AppIcons.Check, null, Modifier.size(16.dp)) }) else null,
        shape = CircleShape,
    )
}

@Composable
private fun Action(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

