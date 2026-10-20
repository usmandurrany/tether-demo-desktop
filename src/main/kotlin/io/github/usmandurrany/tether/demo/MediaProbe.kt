package io.github.usmandurrany.tether.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO

/**
 * Reads what the demo sends along with a link: a photo's size in bytes and its
 * dimensions, a video's size. A real app knows these from the file it
 * uploaded; the demo only has a link, so it asks the web server.
 */
object MediaProbe {
    class PhotoInfo(val width: Int?, val height: Int?, val bytes: Long?, val mime: String?)

    private const val MAX_PHOTO_BYTES = 20_000_000

    suspend fun photo(url: String): PhotoInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, "GET")
            try {
                val mime = c.contentType?.substringBefore(';')?.trim()
                val bytes = c.inputStream.use { it.readNBytes(MAX_PHOTO_BYTES) }
                val image = ImageIO.read(ByteArrayInputStream(bytes))
                PhotoInfo(image?.width, image?.height, bytes.size.toLong(), mime)
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun size(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(url, "HEAD")
            try {
                c.contentLengthLong.takeIf { it > 0 }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TetherDemo/0.1")
        }
}
