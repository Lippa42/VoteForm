package it.marcolipparini.sfide.engine

import kotlinx.serialization.json.Json

/**
 * Configurazione JSON condivisa per serializzare le definizioni di stanza,
 * i messaggi di protocollo e lo storico. `classDiscriminator = "type"`
 * combacia con i client web in `web/shared`.
 */
val EngineJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    prettyPrint = false
}
