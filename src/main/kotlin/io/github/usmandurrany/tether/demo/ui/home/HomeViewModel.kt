package io.github.usmandurrany.tether.demo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectionState
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.im.Profile
import io.github.usmandurrany.tether.im.Show
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What we tell contacts about ourselves. */
enum class Presence(val label: String, val show: Show?) {
    AVAILABLE("Available", null), CHAT("Free to chat", Show.CHAT), AWAY("Away", Show.AWAY),
    XA("Away for long", Show.XA), DND("Do not disturb", Show.DND), INVISIBLE("Invisible", null),
}

/** You, the connection, and the unread total: what the navigation rail and account menu show. */
class HomeViewModel(private val session: DemoSession) : ViewModel() {
    data class State(
        val connection: ConnectionState = ConnectionState.Idle,
        val profile: Profile? = null,
        val presence: Presence = Presence.AVAILABLE,
        val statusMessage: String? = null,
        val unread: Int = 0,
    )

    private val mine = MutableStateFlow(Presence.AVAILABLE to (null as String?))

    val account = session.account
    val profilesEnabled = session.chat.features.profiles

    val state: StateFlow<State> = combine(
        session.client.state,
        session.chat.myProfile,
        mine,
        session.chat.conversations.map { list -> list.sumOf { it.unreadCount } },
    ) { connection, profile, (presence, status), unread ->
        State(connection, profile, presence, status, unread)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun setPresence(presence: Presence) = apply(presence, mine.value.second)

    fun setStatusMessage(text: String) = apply(mine.value.first, text.trim().ifEmpty { null })

    private fun apply(presence: Presence, status: String?) {
        mine.update { presence to status }
        viewModelScope.launch {
            runCatching { session.chat.setPresence(presence.show, status, invisible = presence == Presence.INVISIBLE) }
        }
    }

    /** Saves your profile (vCard); copies the current one so fields the form doesn't show are kept. */
    suspend fun saveProfile(edit: (Profile) -> Profile): Result<Unit> =
        runCatching { session.chat.updateMyProfile(edit(session.chat.myProfile.value ?: Profile())) }
}
