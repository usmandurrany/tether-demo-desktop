package io.github.usmandurrany.tether.demo.ui

import io.github.usmandurrany.tether.im.Avatar
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.swing.Icon
import javax.swing.UIManager

/** Decodes profile pictures once per picture and turns picked files into small JPEGs. */
object Avatars {
    private val decoded = ConcurrentHashMap<String, BufferedImage>()

    fun image(avatar: Avatar): BufferedImage? =
        decoded[avatar.hash] ?: runCatching { ImageIO.read(ByteArrayInputStream(avatar.data)) }.getOrNull()?.also { decoded[avatar.hash] = it }

    /**
     * Reads an image file and makes it a square JPEG of at most [size] pixels:
     * small enough to travel in one stanza (servers cap stanza size).
     */
    fun fromFile(file: File, size: Int = 192): Avatar? {
        val source = ImageIO.read(file) ?: return null
        val side = minOf(source.width, source.height)
        val crop = source.getSubimage((source.width - side) / 2, (source.height - side) / 2, side, side)
        val target = minOf(size, side)
        val out = BufferedImage(target, target, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(crop, 0, 0, target, target, null)
            dispose()
        }
        val bytes = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        ImageIO.createImageOutputStream(bytes).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.85f }
            writer.write(null, IIOImage(out, null, null), params)
            writer.dispose()
        }
        return Avatar("image/jpeg", bytes.toByteArray())
    }
}

/**
 * A round profile picture, or the name's initial on a colour picked from the
 * name when there is none. [dot] adds a presence dot in the corner.
 */
class AvatarIcon(private val size: Int) : Icon {
    var avatar: Avatar? = null
    var name: String = ""
    var dot: Color? = null

    override fun getIconWidth() = size
    override fun getIconHeight() = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val circle = Ellipse2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat())
        val image = avatar?.let(Avatars::image)
        if (image != null) {
            g2.clip(circle)
            g2.drawImage(image, x, y, size, size, null)
            g2.clip = null
        } else {
            val hue = (name.lowercase().hashCode() and 0xffff) / 65535f
            g2.color = Color.getHSBColor(hue, 0.45f, 0.75f)
            g2.fill(circle)
            g2.color = Color.WHITE
            g2.font = (UIManager.getFont("Label.font") ?: g2.font).deriveFont(Font.BOLD, size * 0.45f)
            val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
            val fm = g2.fontMetrics
            g2.drawString(initial, x + (size - fm.stringWidth(initial)) / 2, y + (size - fm.height) / 2 + fm.ascent)
        }
        dot?.let {
            val d = maxOf(8, size / 4)
            g2.color = UIManager.getColor("List.background") ?: Color.WHITE
            g2.fillOval(x + size - d - 1, y + size - d - 1, d + 2, d + 2)
            g2.color = it
            g2.fillOval(x + size - d, y + size - d, d, d)
        }
        g2.dispose()
    }
}
