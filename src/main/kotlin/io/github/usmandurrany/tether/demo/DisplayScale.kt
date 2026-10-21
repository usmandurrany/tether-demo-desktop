package io.github.usmandurrany.tether.demo

import java.util.concurrent.TimeUnit

/**
 * Java on Linux doesn't follow the desktop's display scaling, so on a HiDPI
 * screen the whole UI comes out tiny. Windows and macOS report their scale to
 * Java themselves. This reads the scale the way GTK apps do and hands it to
 * Java through `sun.java2d.uiScale`, which Compose then uses for its density.
 *
 * Call it first thing in `main`, before any window or AWT class is touched.
 * Passing `-Dsun.java2d.uiScale=…` yourself always wins.
 */
object DisplayScale {
    fun applyOnLinux() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Linux")) return
        if (System.getProperty("sun.java2d.uiScale") != null) return
        val scale = detect() ?: return
        // Quarter steps, as desktops offer them; anything near 1 stays at 1.
        val rounded = Math.round(scale * 4) / 4.0
        if (rounded > 1.0) System.setProperty("sun.java2d.uiScale", rounded.toString())
    }

    private fun detect(): Double? {
        // Set by the person or the session, and what GTK itself reads.
        System.getenv("GDK_SCALE")?.toDoubleOrNull()?.let { scale ->
            return scale * (System.getenv("GDK_DPI_SCALE")?.toDoubleOrNull() ?: 1.0)
        }
        System.getenv("QT_SCALE_FACTOR")?.toDoubleOrNull()?.let { return it }
        // GNOME, KDE and most others publish the scale as Xft.dpi (96 is 100%).
        run("xrdb", "-query")?.lineSequence()
            ?.firstOrNull { it.startsWith("Xft.dpi:") }
            ?.substringAfter(':')?.trim()?.toDoubleOrNull()
            ?.let { return it / 96.0 }
        // GNOME's own setting, as "uint32 2", when there is no X resource database.
        val factor = run("gsettings", "get", "org.gnome.desktop.interface", "scaling-factor")
            ?.trim()?.substringAfterLast(' ')?.toDoubleOrNull()?.takeIf { it > 0 }
        val text = run("gsettings", "get", "org.gnome.desktop.interface", "text-scaling-factor")
            ?.trim()?.toDoubleOrNull()
        return if (factor == null && text == null) null else (factor ?: 1.0) * (text ?: 1.0)
    }

    /** A command's output, or null when it's missing, fails or takes too long. */
    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroy()
            return null
        }
        if (process.exitValue() != 0) null else process.inputStream.bufferedReader().readText()
    }.getOrNull()
}
