package io.github.usmandurrany.tether.demo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.usmandurrany.tether.core.ConnectResult
import io.github.usmandurrany.tether.demo.ConnectionSettings
import io.github.usmandurrany.tether.demo.DemoSession
import io.github.usmandurrany.tether.demo.ui.describe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's top level: signed out (the sign-in page) or signed in with a
 * [DemoSession], plus the theme. It runs the first connection attempt,
 * explains failures, and asks before trusting a self-signed certificate.
 * After a successful login the SDK reconnects on its own.
 */
class AppViewModel(initial: ConnectionSettings = ConnectionSettings.load()) : ViewModel() {
    /** What the sign-in page shows under the Connect button. */
    data class Notice(val text: String, val error: Boolean)

    data class SignInState(val settings: ConnectionSettings, val busy: Boolean = false, val notice: Notice? = null)

    /** A refused certificate waiting for the person's answer. */
    class TrustQuestion(val fingerprint: String, internal val answer: CompletableDeferred<Boolean>)

    private val _signIn = MutableStateFlow(SignInState(initial))
    val signIn: StateFlow<SignInState> = _signIn.asStateFlow()

    private val _session = MutableStateFlow<DemoSession?>(null)

    /** The signed-in account, or null on the sign-in page. */
    val session: StateFlow<DemoSession?> = _session.asStateFlow()

    private val _darkTheme = MutableStateFlow(initial.darkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _trust = MutableStateFlow<TrustQuestion?>(null)
    val trustQuestion: StateFlow<TrustQuestion?> = _trust.asStateFlow()

    init {
        autoConnect()
    }

    /** With "remember me" and saved login keys, sign in straight away. */
    private fun autoConnect() {
        val settings = _signIn.value.settings
        if (!settings.rememberLogin || settings.validate() != null) return
        viewModelScope.launch {
            if (DemoSession.savedKeys(settings.account()).isNotEmpty()) connect(settings, "")
        }
    }

    fun connect(settings: ConnectionSettings, password: String) {
        settings.validate()?.let { problem ->
            _signIn.update { it.copy(settings = settings, notice = Notice(problem, error = true)) }
            return
        }
        val saved = settings.copy(darkTheme = _darkTheme.value)
        runCatching { saved.save() }
        _signIn.value = SignInState(saved, busy = true)
        viewModelScope.launch { attempt(saved, password) }
    }

    private suspend fun attempt(settings: ConnectionSettings, password: String) {
        fun fail(text: String) = _signIn.update { it.copy(busy = false, notice = Notice(text, error = true)) }
        val keys = DemoSession.savedKeys(settings.account())
        if (password.isEmpty() && keys.isEmpty()) return fail("Enter the password. There's no saved login for ${settings.account()} yet.")
        val created = runCatching { DemoSession.create(settings, password, keys) }.getOrElse { return fail(describe(it)) }
        when (val result = created.connect()) {
            is ConnectResult.Connected -> {
                _signIn.update { it.copy(busy = false, notice = null) }
                _session.value = created
            }
            is ConnectResult.Failed -> {
                // The client would keep retrying on its own; on the sign-in page it's clearer to stop and say why.
                created.disconnect()
                val fingerprint = result.untrustedCertificate
                if (fingerprint != null && settings.normalizedPin() != fingerprint && askToTrust(fingerprint)) {
                    val pinned = settings.copy(pinnedCertificate = fingerprint)
                    runCatching { pinned.save() }
                    _signIn.update { it.copy(settings = pinned) }
                    return attempt(pinned, password)
                }
                fail(describe(result.error) + if (password.isEmpty()) " The saved login was used; type the password to sign in afresh." else "")
            }
            ConnectResult.Cancelled -> {
                created.disconnect()
                _signIn.update { it.copy(busy = false, notice = Notice("Cancelled.", error = false)) }
            }
        }
    }

    private suspend fun askToTrust(fingerprint: String): Boolean {
        val question = TrustQuestion(fingerprint, CompletableDeferred())
        _trust.value = question
        return question.answer.await().also { _trust.value = null }
    }

    fun answerTrust(trust: Boolean) {
        _trust.value?.answer?.complete(trust)
    }

    /** Signs out. [forget] also deletes the saved login keys and this account's chats on this computer. */
    fun signOut(forget: Boolean) {
        val current = _session.value ?: return
        viewModelScope.launch {
            _session.value = null
            if (forget) current.logOut() else current.disconnect()
            _signIn.update { it.copy(notice = Notice(if (forget) "Logged out. The saved login was removed." else "Disconnected.", error = false)) }
        }
    }

    fun reconnect() {
        val current = _session.value ?: return
        viewModelScope.launch { current.connect() }
    }

    /** Window focus is the desktop's version of "foreground" (XEP-0352, read markers). */
    fun setForeground(foreground: Boolean) {
        _session.value?.client?.setForeground(foreground)
    }

    fun toggleTheme() {
        val dark = !_darkTheme.value
        _darkTheme.value = dark
        _signIn.update { it.copy(settings = it.settings.copy(darkTheme = dark)) }
        runCatching { ConnectionSettings.load().copy(darkTheme = dark).save() }
    }

    /** Remembers what was typed on the sign-in page, so it survives a theme switch or a failed attempt. */
    fun edit(settings: ConnectionSettings) {
        _signIn.update { it.copy(settings = settings) }
    }

    /** A clean close lets the server mark us offline at once; Tether saves its state on the way. */
    suspend fun shutdown() {
        val current = _session.value ?: return
        withTimeoutOrNull(5_000) { current.disconnect() }
    }
}
