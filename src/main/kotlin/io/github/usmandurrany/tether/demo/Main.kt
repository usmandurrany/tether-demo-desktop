package io.github.usmandurrany.tether.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.usmandurrany.tether.demo.app.App
import io.github.usmandurrany.tether.demo.app.AppViewModel
import kotlinx.coroutines.launch
import java.awt.Dimension

fun main() {
    // macOS: the title bar follows the system's light or dark appearance.
    System.setProperty("apple.awt.application.appearance", "system")
    val appViewModels = Owner()
    application {
        CompositionLocalProvider(LocalViewModelStoreOwner provides appViewModels) {
            val app = viewModel { AppViewModel() }
            val session by app.session.collectAsState()
            var visible by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            val quit: () -> Unit = {
                visible = false
                scope.launch {
                    app.shutdown()
                    appViewModels.viewModelStore.clear()
                    exitApplication()
                }
            }
            Window(
                onCloseRequest = quit,
                visible = visible,
                title = session?.let { "Tether demo · ${it.account}" } ?: "Tether demo",
                state = rememberWindowState(size = DpSize(1180.dp, 780.dp)),
                onPreviewKeyEvent = { e ->
                    val quitKey = e.key == Key.Q && e.type == KeyEventType.KeyDown && (e.isCtrlPressed || e.isMetaPressed)
                    if (quitKey) quit()
                    quitKey
                },
            ) {
                LaunchedEffect(Unit) { window.minimumSize = Dimension(560, 520) }
                // Focus is the desktop's version of "foreground": the client reports
                // inactive to the server (XEP-0352) while the window is in the background.
                val focused = LocalWindowInfo.current.isWindowFocused
                LaunchedEffect(focused, session) { app.setForeground(focused) }
                App(app, onQuit = quit)
            }
        }
    }
}

/** Holds ViewModels for as long as something (the app, or one signed-in session) lives. */
class Owner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * Gives the screens inside it their own ViewModels, cleared when [key]
 * changes or the content leaves: one set per signed-in session.
 */
@Composable
fun ViewModelScope(key: Any, content: @Composable () -> Unit) {
    val owner = remember(key) { Owner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
