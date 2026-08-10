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
  persistence/        Room DB: template di stanza + storico partite         ← implementato
  app/                app Android host: creazione stanza + regia Compose    ← implementato
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
./gradlew :server:run                 # Quiz (default) sulla porta 8080
ROOM=voting ./gradlew :server:run     # Sfida a voti
```

All'avvio la console stampa gli indirizzi. Poi, sulla stessa rete:

- **Visualizzatore (TV)**: apri `http://<ip-host>:8080/viewer/index.html` — mostra PIN e QR.
- **Spettatore (telefono)**: inquadra il QR o apri `.../spectator/index.html`, entra col PIN `4291`.
- **Regia**: apri `http://<ip-host>:8080/admin` e usa i pulsanti *Prossima domanda / Chiudi / Svela*.

Test automatici:

```bash
./gradlew :engine:test :server:test
```

Lo stesso codice del server gira anche **embedded nell'app Android host**.

## App Android host

L'app `:app` avvia il server embedded (Ktor CIO) dentro un **foreground service**,
serve i client web dagli **asset**, e offre la **regia in Compose** (PIN, QR,
pulsanti Prossima/Chiudi/Svela). Richiede l'Android SDK (`compileSdk 35`,
`build-tools 35.0.0`) e un `local.properties` con `sdk.dir`.

```bash
./gradlew :app:assembleDebug   # produce app/build/outputs/apk/debug/app-debug.apk
```

## Spettatori da remoto (relay)

Per far collegare Visualizzatore e Spettatori **da reti diverse** (non la WiFi
dell'host), c'è un **relay** leggero: host e client si connettono entrambi **in
uscita** verso il relay (quindi il NAT del telefono non è un problema); il relay
**inoltra solo i messaggi**, tutta la logica resta sull'host. Serve anche i client
web.

Prova in locale (relay + host + browser sulla stessa macchina):

```bash
# 1) avvia il relay (serve i client web e fa da ponte)
PORT=9090 ./gradlew :relay:run
# 2) in un altro terminale, avvia l'host collegato al relay
PORT=8080 ROOM=show RELAY_URL=ws://localhost:9090 ROOM_CODE=demo ./gradlew :server:run
# 3) apri i client PASSANDO dal relay:
#    http://localhost:9090/viewer/index.html?room=demo
#    http://localhost:9090/spectator/index.html?room=demo   (PIN della stanza)
#    regia sull'host: http://localhost:8080/admin
```

Deploy del relay (una volta): impacchetta ed esegui su un servizio economico
(VPS, Fly.io, Railway…):

```bash
./gradlew :relay:installDist         # crea relay/build/install/relay (avviabile con bin/relay)
```

Poi nell'app Android inserisci l'**URL del relay** (`wss://tuo-relay…`) nel campo
in Home: l'host si collegherà al relay e gli spettatori useranno
`https://tuo-relay/spectator/index.html?room=<PIN>`.

## Stato

- **Fatto e verificato**:
  - modello dati (`engine`) con test;
  - **Quiz** e **Sfida a voti** end-to-end (`server` Ktor, interfaccia comune
    `GameSession`, client `viewer`/`spectator` bimodali) con test d'integrazione e
    smoke test su browser reale;
  - **app Android host** (`app`) che compila in APK, avvia il server embedded in
    foreground service, e offre in Compose: **composizione della stanza come
    timeline** (fasi **trascinabili**: Titolo, Media, Classifica, Quiz, Votazione,
    Torneo, Questionario, Finale), **template salvati** (Room DB) e **regia**.
  - **persistenza** (`persistence`, Room DB): salvataggio dei template di stanza e
    dello storico partite in locale.
  - **timer**: countdown su TV e telefono con chiusura automatica allo scadere.
  - **torneo a eliminazione** (`engine/bracket` + `KnockoutSession`): sorteggio,
    incontri con avanzamento del vincitore e tabellone sulla TV.
  - **tema personalizzabile**: palette scelta nel Builder e applicata ai client web
    (colori via variabili CSS dal tema della stanza).
  - **animazioni**: entrate/transizioni di domande, opzioni, classifica e tabellone
    (rispettando `prefers-reduced-motion`).
  - **modalità Questionario** con sessione propria: nessuna risposta giusta, alla
    rivelazione mostra la distribuzione ("il pubblico ha detto").
  - **storico automatico**: a fine partita il risultato (con vincitore) è salvato in
    locale; l'app ha una schermata **Storico partite**.
  - **media reali**: la fase Media mostra davvero l'immagine/audio/video servito
    dall'host (rotta `/asset/{id}`); nel builder l'admin **carica il file dal
    dispositivo** (copiato nella storage interna dell'app).
  - **musica di sottofondo** che **continua tra le fasi**: playlist royalty-free di
    default (tracce generate per Sfide, libere da copyright, in `assets/music/`),
    controlli di regia pausa/riprendi/salta. Non sono inclusi brani protetti da
    copyright; puoi aggiungere le tue tracce (su cui hai i diritti) col caricamento file.
  - **spettatori da remoto** (`relay`): un ponte WebSocket leggero (host e client
    connessi in uscita, oltre il NAT) inoltra i messaggi senza logica di gioco;
    percorso remoto verificato con smoke su browser reale.
- **Stato**: funzioni complete. Spunti futuri in
  [`docs/architettura.md`](docs/architettura.md) (spettatori da remoto, Spotify).
