package it.marcolipparini.sfide.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.nayuki.qrcodegen.QrCode
import java.awt.image.BufferedImage

/** Genera il QR di join come immagine per la regia (mouse: inquadrabile dal telefono). */
fun qrBitmap(url: String, scale: Int = 6, quiet: Int = 3): ImageBitmap {
    val qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM)
    val n = qr.size
    val dim = (n + quiet * 2) * scale
    val img = BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, dim, dim)
    g.color = java.awt.Color.BLACK
    for (y in 0 until n) {
        for (x in 0 until n) {
            if (qr.getModule(x, y)) {
                g.fillRect((x + quiet) * scale, (y + quiet) * scale, scale, scale)
            }
        }
    }
    g.dispose()
    return img.toComposeImageBitmap()
}
