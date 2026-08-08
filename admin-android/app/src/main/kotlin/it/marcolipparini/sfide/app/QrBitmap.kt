package it.marcolipparini.sfide.app

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode
import it.marcolipparini.sfide.server.joinUrl

/** Genera il QR di join come Bitmap, da mostrare nella regia dell'host. */
object QrBitmap {

    fun forJoin(port: Int, scale: Int = 8, border: Int = 2): Bitmap? = runCatching {
        val qr = QrCode.encodeText(joinUrl(port), QrCode.Ecc.MEDIUM)
        val dim = (qr.size + border * 2) * scale
        val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val mx = x / scale - border
                val my = y / scale - border
                val dark = mx in 0 until qr.size && my in 0 until qr.size && qr.getModule(mx, my)
                bmp.setPixel(x, y, if (dark) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}
