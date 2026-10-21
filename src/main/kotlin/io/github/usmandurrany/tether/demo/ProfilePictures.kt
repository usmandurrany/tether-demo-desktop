package io.github.usmandurrany.tether.demo

import io.github.usmandurrany.tether.im.Avatar
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Turns a picked image file into a profile picture that fits in one stanza. */
object ProfilePictures {
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
