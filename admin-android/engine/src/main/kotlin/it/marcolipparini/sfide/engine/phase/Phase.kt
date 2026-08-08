package it.marcolipparini.sfide.engine.phase

import kotlinx.serialization.Serializable

/**
 * Macchina a stati di alto livello della stanza. Lo stato è event-sourced:
 * ogni azione è un evento applicato allo stato, così un client che si
 * riconnette riceve uno snapshot e riallinea in un colpo.
 *
 * LOBBY → (SETUP → INPUT → LOCKED → COMPUTING → REVEAL) → STANDINGS → …loop… → FINISHED
 */
@Serializable
enum class RoomPhase {
    LOBBY,
    SETUP,
    INPUT,
    LOCKED,
    COMPUTING,
    REVEAL,
    STANDINGS,
    FINISHED,
}
