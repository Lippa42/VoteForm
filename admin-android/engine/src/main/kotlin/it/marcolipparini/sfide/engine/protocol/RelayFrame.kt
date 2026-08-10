package it.marcolipparini.sfide.engine.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope di multiplexing tra **host** e **relay** (una sola connessione WebSocket
 * porta i messaggi di tutti i client). Il relay non conosce il protocollo di gioco:
 * inoltra soltanto stringhe grezze, taggate col client (`c`).
 */
@Serializable
sealed interface RelayFrame {
    /** Relay → Host: un nuovo client si è collegato. */
    @Serializable @SerialName("join")
    data class Join(val c: String) : RelayFrame

    /** Relay → Host: un client si è scollegato. */
    @Serializable @SerialName("leave")
    data class Leave(val c: String) : RelayFrame

    /** Relay → Host: messaggio grezzo inviato dal client `c`. */
    @Serializable @SerialName("msg")
    data class Msg(val c: String, val d: String) : RelayFrame

    /** Host → Relay: invia il messaggio grezzo `d` al client `c`. */
    @Serializable @SerialName("to")
    data class To(val c: String, val d: String) : RelayFrame
}
