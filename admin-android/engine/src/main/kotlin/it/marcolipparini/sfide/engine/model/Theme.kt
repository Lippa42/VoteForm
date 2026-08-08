package it.marcolipparini.sfide.engine.model

import kotlinx.serialization.Serializable

/** Aspetto della stanza: salvabile e riusabile come i template. */
@Serializable
data class Theme(
    val paletteName: String = "default",
    val primaryColor: String = "#E23744",
    val backgroundColor: String = "#0F1117",
    val fontFamily: String = "system",
    val logoAssetId: String? = null,
)
