package io.github.usmandurrany.tether.demo.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.debug.LogFormatter
import io.github.usmandurrany.tether.demo.DemoSession
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The in-app log: every stanza sent and received (credentials always redacted
 * by tether-debug) plus connection changes, from a [io.github.usmandurrany.tether.debug.MemorySink].
 */
@OptIn(FlowPreview::class)
class LogViewModel(private val session: DemoSession) : ViewModel() {
    data class Options(val filter: String = "", val stanzasOnly: Boolean = false, val pretty: Boolean = false, val follow: Boolean = true)

    data class State(val lines: List<String> = emptyList(), val options: Options = Options(), val portal: String? = null)

    private val options = MutableStateFlow(Options())

    private val portal = session.remoteLogs?.let { sink ->
        sink.stats.map { s ->
            "Log portal: ${s.uploaded} uploaded, ${s.queued} queued, ${s.dropped} dropped" +
                (s.lastError?.let { " · last error: $it" } ?: "") + " · session ${sink.sessionId}"
        }
    } ?: flowOf(null)

    val state: StateFlow<State> = combine(session.logs.entries.sample(250), options, portal) { entries, o, p ->
        val formatter = LogFormatter(pretty = o.pretty)
        val needle = o.filter.trim().lowercase()
        val lines = entries.asSequence()
            .filter { !o.stanzasOnly || it.isStanza }
            .map(formatter::format)
            .filter { needle.isEmpty() || it.lowercase().contains(needle) }
            .toList()
        State(lines, o, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun update(change: (Options) -> Options) = options.update(change)

    fun clear() = session.logs.clear()
}
