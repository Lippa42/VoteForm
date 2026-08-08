package it.marcolipparini.sfide.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Template di stanza salvato in locale. Il modello completo (RoomDefinition) è
 * serializzato in [json]; le altre colonne servono per elencarli e ordinarli.
 */
@Entity(tableName = "templates")
data class RoomTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String,
    val updatedAt: Long,
    val json: String,
)

/** Risultato di una partita salvato in locale (storico). */
@Entity(tableName = "history")
data class MatchResultEntity(
    @PrimaryKey val id: String,
    val roomTitle: String,
    val playedAtEpochMs: Long,
    val json: String,
)
