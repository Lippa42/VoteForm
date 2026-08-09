package it.marcolipparini.sfide.server

import it.marcolipparini.sfide.engine.model.RoomDefinition
import java.io.File

/** Dati di un asset pronti da servire. */
class AssetData(val bytes: ByteArray, val contentType: String)

/** Fornisce i byte di un asset dato il suo id. Implementazione locale al dispositivo host. */
interface AssetResolver {
    fun open(id: String): AssetData?
}

/**
 * Risolve gli asset (immagini/audio/video) leggendo i file locali dichiarati nella
 * stanza. Funziona identico su desktop (percorsi del filesystem) e su Android
 * (file copiati nella storage interna dell'app): nessun asset lascia il dispositivo.
 */
class RoomAssetResolver(room: RoomDefinition) : AssetResolver {

    private val paths: Map<String, String> = buildMap {
        room.media.assets.forEach { put(it.id, it.localPath) }
        room.media.music.forEach { putIfAbsent(it.id, it.localPath) }
    }

    override fun open(id: String): AssetData? {
        val path = paths[id] ?: return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return AssetData(file.readBytes(), contentTypeForPath(path))
    }
}

fun contentTypeForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "m4a", "aac" -> "audio/mp4"
    "wav" -> "audio/wav"
    "ogg", "oga" -> "audio/ogg"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "application/octet-stream"
}
