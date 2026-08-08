package it.marcolipparini.sfide.server

import io.nayuki.qrcodegen.QrCode

/** Genera un QR code come SVG autonomo (nessuna immagine esterna, nessun cloud). */
object QrSvg {

    fun svg(text: String, border: Int = 2): String {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val dim = qr.size + border * 2
        val path = StringBuilder()
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    path.append("M${x + border},${y + border}h1v1h-1z")
                }
            }
        }
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $dim $dim" shape-rendering="crispEdges">
              <rect width="100%" height="100%" fill="#ffffff"/>
              <path d="$path" fill="#000000"/>
            </svg>
        """.trimIndent()
    }
}
