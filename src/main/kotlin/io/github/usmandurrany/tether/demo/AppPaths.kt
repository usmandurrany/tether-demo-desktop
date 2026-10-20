package io.github.usmandurrany.tether.demo

import java.nio.file.Path
import java.nio.file.Paths

/** Where the demo keeps its files, following each OS's convention. */
object AppPaths {
    val dataDir: Path by lazy {
        System.getProperty("tether.demo.home")?.let { return@lazy Paths.get(it) }
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> Paths.get(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "TetherDemo")
            os.contains("mac") -> Paths.get(home, "Library", "Application Support", "TetherDemo")
            else -> Paths.get(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config", "tether-demo")
        }
    }

    /** Connection settings typed on the settings page. Never holds the password. */
    val settingsFile: Path get() = dataDir.resolve("settings.properties")

    /** Tether's own state: stream resumption, unsent messages, keepalive timing, saved login keys, roster. */
    val tetherStateFile: Path get() = dataDir.resolve("tether-state.properties")

    /** One account's chats, kept by the SDK's FileMessageStore. */
    fun messagesFile(account: String): Path = dataDir.resolve("messages").resolve(account.replace(Regex("[^A-Za-z0-9@._-]"), "_") + ".messages")
}
