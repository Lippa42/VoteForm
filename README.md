# Stanze di sfida virtuale

Sistema per aprire **stanze di sfida virtuale** completamente personalizzabili
(quiz, sfide a voti, questionari, tornei) in tre ruoli, **senza cloud**.

## I tre ruoli

| Ruolo | Natura | Cosa fa |
|---|---|---|
| **Amministratore** | Host + autorità | Crea la stanza, definisce le regole, fa avanzare le fasi. È anche il **server**. |
| **Visualizzatore** | Client di presentazione | La TV/schermo: classifica scorrevole, domande, reveal, animazioni. |
| **Spettatore** | Client partecipante | I telefoni del pubblico: rispondono, votano, reagiscono. |

## Idea portante

**L'app Amministratore è anche il server.** Gira su Android e avvia al suo interno un
piccolo server locale (Ktor: HTTP + WebSocket). Visualizzatore e Spettatori sono
**pagine web servite dall'host**, aperte nel browser sulla stessa rete WiFi
(onboarding tramite QR code). Tutto lo stato vive sul dispositivo dell'admin.

Poiché i client si limitano a mostrare lo stato e a inviare *intenzioni*, **tutta la
logica di gioco, voto e punteggio vive in un unico posto** — il modulo `engine` in
Kotlin — affidabile, testabile e non manipolabile dai client.

## Scelte tecniche

- **Host / Admin**: Android (Kotlin, Jetpack Compose), `minSdk 26`, `compileSdk 35`.
- **Server embedded**: Ktor (HTTP statico + WebSocket).
- **Client web**: Svelte + TypeScript + Vite (PWA).
- **Persistenza**: Room DB (storico partite salvato in locale).
- **Rete**: LAN-only in v1 (0% cloud). Spettatori remoti + Spotify: fase 2.
- **Scala**: fino a 15 partecipanti.

## Struttura del repository

```
admin-android/        app Kotlin + server Ktor embedded
  engine/             modello dati + motore voto (Kotlin puro, testabile)  ← implementato
  server/             Ktor: HTTP statico + WebSocket + stato di gioco       ← fetta quiz
  persistence/        Room DB, template di stanza, storico                  (da fare)
  app/                Jetpack Compose: creazione stanza + regia             (da fare)
web/                  client (HTML/JS ora; Svelte + Vite in seguito)
  shared/             protocollo WS, tipi condivisi                         ← protocollo
  viewer/             vista TV                                              ← fetta quiz
  spectator/          vista telefono                                       ← fetta quiz
docs/
  architettura.md     proposta di architettura completa
```

## Provare la fetta end-to-end (quiz)

Serve un JDK 21. Dalla cartella `admin-android`:

```bash
./gradlew :server:run          # avvia l'host sulla porta 8080
```

All'avvio la console stampa gli indirizzi. Poi, sulla stessa rete:

- **Visualizzatore (TV)**: apri `http://<ip-host>:8080/viewer/index.html` — mostra PIN e QR.
- **Spettatore (telefono)**: inquadra il QR o apri `.../spectator/index.html`, entra col PIN `4291`.
- **Regia**: apri `http://<ip-host>:8080/admin` e usa i pulsanti *Prossima domanda / Chiudi / Svela*.

Test automatici:

```bash
./gradlew :engine:test :server:test
```

Lo stesso codice del server girerà in seguito embedded nell'app Android host.

## Stato

- **Fatto e verificato**: modello dati (`engine`) con test; **fetta quiz end-to-end**
  (`server` Ktor + client `viewer`/`spectator`) con test d'integrazione e smoke test
  su browser reale.
- **Prossimo**: app Android (Compose + Ktor embedded) come host, persistenza (Room),
  poi le altre modalità e la personalizzazione. Vedi
  [`docs/architettura.md`](docs/architettura.md).
