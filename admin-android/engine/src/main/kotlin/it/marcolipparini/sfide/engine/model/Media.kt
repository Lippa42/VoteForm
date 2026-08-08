package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaConfig(
    val assets: List<MediaAsset> = emptyList(),
    val music: List<MusicTrack> = emptyList(),
    val defaultImageAnimation: ImageAnimation = ImageAnimation.FADE,
)

@Serializable
data class MediaAsset(
    val id: String,
    /** Percorso locale sul dispositivo host: nessun media sul cloud. */
    val localPath: String,
    val animation: ImageAnimation = ImageAnimation.FADE,
)

@Serializable
enum class ImageAnimation { NONE, FADE, SLIDE, ZOOM, FLIP, KEN_BURNS }

@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val localPath: String,
    /** Origine dichiarata, per rispettare le licenze royalty-free. */
    val source: String = "",
)
