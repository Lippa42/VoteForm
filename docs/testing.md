# Guida al test dell'infrastruttura — Sfide

Questa guida ti fa provare **tutta** l'infrastruttura, dal più semplice al più
completo:

1. **Test automatici** (30 secondi)
2. **Desktop, tutto-in-uno** — tutte le modalità in un browser
3. **Multi-dispositivo su LAN** — telefoni come spettatori
4. **Remoto col relay** — spettatori su reti diverse (con media e musica)
5. **App Android** — il telefono come host (device reale o emulatore)

> I comandi usano `./gradlew` (già incluso nel repo). Su **Windows** usa
> `gradlew.bat` e imposta le variabili con `set VAR=valore` (cmd) o
> `$env:VAR="valore"` (PowerShell). Su macOS/Linux vanno come scritti.

---

## 0. Prerequisiti

- **JDK 21** — verifica con `java -version` (deve dire `21`). Se manca, installa
  Temurin/Adoptium 21.
- **git** e un **browser** (Chrome/Edge/Firefox).
- *(Opzionale)* **Android Studio** solo per l'Opzione 5 (app Android).

## 1. Preparazione (una volta)

```bash
git clone -b claude/virtual-challenge-app-architecture-elaqb5 https://github.com/lippa42/voteform.git
cd voteform/admin-android
```

## 2. Test automatici

```bash
./gradlew :engine:test :server:test
```
✅ **Cosa devi vedere:** `BUILD SUCCESSFUL`. Verifica il motore di gioco, il voto,
il tabellone, il timer, le sessioni (Quiz/Voti/Torneo/Questionario/Timeline) e il
resolver degli asset.

---

## 3. Test desktop (tutte le modalità) — il più veloce

Avvia una demo e apri **3 schede** del browser (bastano sullo stesso computer).

```bash
./gradlew :server:run          # Quiz (PIN 4291)
```
All'avvio la console stampa gli indirizzi. Apri:

- **Visualizzatore (la "TV")** → <http://localhost:8080/viewer/index.html>
- **Regia** → <http://localhost:8080/admin>
- **Spettatore** → <http://localhost:8080/spectator/index.html> → nome + **PIN**

Poi: dalla **Regia** premi *Avanti* per far scorrere le fasi, *Chiudi* per bloccare,
*Svela* per i risultati; dallo **Spettatore** rispondi/vota. Ferma con `Ctrl+C`.

Le altre modalità (ferma e rilancia cambiando `ROOM`):

| Comando | Cosa provi | PIN |
|---|---|---|
| `./gradlew :server:run` | Quiz (risposte, timer, punteggi) | `4291` |
| `ROOM=voting ./gradlew :server:run` | Sfida a voti (criteri pesati) | `7788` |
| `ROOM=knockout ./gradlew :server:run` | Torneo a eliminazione (tabellone) | `5555` |
| `ROOM=survey ./gradlew :server:run` | Questionario (distribuzione) | `2020` |
| `ROOM=show ./gradlew :server:run` | Timeline mista (titolo→quiz→classifica→voti→finale) | `9000` |
| `ROOM=showtour ./gradlew :server:run` | Timeline con fase Torneo | `7000` |
| `ROOM=showmedia SFIDE_MEDIA_PATH="$HOME/una-foto.jpg" ./gradlew :server:run` | Fase Media (immagine reale) | `8000` |
| `ROOM=showmusic SFIDE_MUSIC_DIR="$(pwd)/../assets/music" ./gradlew :server:run` | Musica di sottofondo | `9500` |

✅ **Cosa devi vedere, per modalità:**
- **Quiz:** domanda con timer ⏱ che scorre; a *Svela* la risposta giusta in verde e la classifica.
- **Voti:** sul telefono uno **slider per criterio**; sulla TV "sta votando X" e il punteggio della prova.
- **Torneo:** il **tabellone** con i vincitori che avanzano, fino al 🏆 campione.
- **Questionario:** a *Svela*, i risultati **a barre** ("il pubblico ha detto").
- **Timeline:** le fasi si susseguono; la **Classifica** e la **Finale** mostrano lo scoreboard.
- **Media:** la TV mostra **l'immagine** indicata in `SFIDE_MEDIA_PATH`.
- **Musica:** clicca **una volta ▶** in alto sul Visualizzatore (i browser bloccano
  l'autoplay); la musica parte e **continua tra le fasi**; pausa/salta dalla Regia.

---

## 4. Test multi-dispositivo su LAN (telefoni veri)

Come l'Opzione 3, ma lo **Spettatore è un telefono** sulla stessa WiFi:

1. Avvia una demo (`./gradlew :server:run`).
2. Apri il **Visualizzatore** su un PC/TV: `http://localhost:8080/viewer/index.html`
   (mostra **PIN** e **QR**).
3. Sul telefono, sulla **stessa WiFi**, **inquadra il QR** (oppure apri
   `http://<IP-del-computer>:8080/spectator/index.html`, con l'IP stampato in console).

✅ **Cosa devi vedere:** il telefono entra col PIN e interagisce in tempo reale.

⚠️ Se il telefono non si collega: controlla di essere sulla **stessa rete**, che il
router **non** abbia l'*isolamento client* (AP isolation) attivo, e che il
**firewall del computer** consenta la porta 8080.

---

## 5. Test del relay (spettatori da remoto)

Fa collegare i client **da reti diverse** dall'host. In locale si prova con **due
terminali** + browser.

**Terminale 1 — il relay** (ponte + serve i client web):
```bash
PORT=9090 ./gradlew :relay:run
```

**Terminale 2 — l'host collegato al relay:**
```bash
PORT=8080 ROOM=show RELAY_URL=ws://localhost:9090 ROOM_CODE=demo ./gradlew :server:run
```

**Browser — i client passano dal relay** (nota il `?room=demo`):
- Visualizzatore → <http://localhost:9090/viewer/index.html?room=demo>
- Spettatore → <http://localhost:9090/spectator/index.html?room=demo> (PIN `9000`)
- Regia (sull'host) → <http://localhost:8080/admin>

✅ **Cosa devi vedere:** guidi la partita dalla Regia dell'host e i client aperti
**dal relay** si aggiornano; le risposte/voti tornano all'host. Tutto passa per il
relay, non tocca l'host direttamente.

**Media e musica da remoto** (il relay fa da proxy per gli asset):
```bash
# Terminale 2, in alternativa:
ROOM=showmedia SFIDE_MEDIA_PATH="$HOME/una-foto.jpg" RELAY_URL=ws://localhost:9090 ROOM_CODE=demo PORT=8080 ./gradlew :server:run   # PIN 8000
ROOM=showmusic SFIDE_MUSIC_DIR="$(pwd)/../assets/music" RELAY_URL=ws://localhost:9090 ROOM_CODE=demo PORT=8080 ./gradlew :server:run  # PIN 9500
```
✅ Aprendo il Visualizzatore dal relay (`?room=demo`), **l'immagine si vede** e **la
musica si sente**, pur risiedendo sull'host.

**In produzione:** `./gradlew :relay:installDist` crea un eseguibile
(`relay/build/install/relay/bin/relay`) da mettere su un servizio economico (VPS,
Fly.io, Railway). Poi nell'app Android metti l'URL del relay (`wss://…`) nel campo
in Home; gli spettatori useranno `https://tuo-relay/spectator/index.html?room=<PIN>`.

---

## 6. App Android (il telefono è l'host)

### Device reale (esperienza completa)
1. Android Studio → *Open* → seleziona la cartella **`admin-android`** → attendi il
   *Gradle sync*.
2. Collega un telefono (debug USB) → scegli la configurazione **`app`** → **Run ▶**.
3. Nell'app **Sfide**: **"Crea nuova stanza"** (timeline con drag-and-drop) o
   **"Avvia … d'esempio"**. Il telefono diventa host e mostra **PIN + QR + Regia**.
4. Sulla stessa WiFi apri il **Visualizzatore** su una TV/PC e collega gli spettatori
   col **QR**. Per il **remoto**, incolla l'URL del relay nel campo in Home.

### Emulatore
L'emulatore ha una rete isolata: usa `adb` per raggiungere il server.
1. Avvia l'app nell'emulatore e fai partire una stanza (annota il PIN).
2. Sul computer: `adb forward tcp:8080 tcp:8080`
   (`adb` è in `<SDK>/platform-tools`).
3. Apri i client sul **computer**: `http://localhost:8080/viewer/index.html` e
   `.../spectator/index.html`.
   **Ignora QR/IP** mostrati dall'app (l'IP dell'emulatore non è raggiungibile).

---

## 7. Checklist di verifica

- [ ] Test automatici verdi (`:engine:test :server:test`)
- [ ] Quiz: timer che scorre, auto-lock, risposta giusta evidenziata, classifica
- [ ] Voti: slider per criterio, punteggio pesato, reveal
- [ ] Torneo: tabellone che avanza, campione
- [ ] Questionario: distribuzione a barre
- [ ] Timeline: fasi in sequenza, Classifica e Finale
- [ ] Media: immagine mostrata sulla TV
- [ ] Musica: parte (dopo il click ▶) e continua tra le fasi; pausa/salta
- [ ] Tema: il colore accent della stanza applicato ai client
- [ ] Multi-dispositivo: un telefono entra via QR sulla LAN
- [ ] Relay: client remoti collegati; media e musica visibili via relay
- [ ] App Android: crea/avvia stanza, regia, storico partite

---

## 8. Troubleshooting

| Problema | Soluzione |
|---|---|
| `java -version` non è 21 | Installa **JDK 21** (Temurin/Adoptium). |
| "Address already in use" (porta occupata) | Cambia `PORT` o libera la porta: `fuser -k 8080/tcp` (Linux/mac) / chiudi il processo. |
| Il telefono non vede l'host (LAN) | Stessa WiFi; disattiva l'**AP isolation** del router; consenti la porta nel **firewall** del computer. |
| La musica non parte | Clicca **una volta ▶** sul Visualizzatore (autoplay bloccato dal browser). |
| Relay: "host non disponibile" | Avvia **prima il relay**, poi l'host, con lo **stesso** `ROOM_CODE`. |
| Media/musica non caricano da remoto | Assicurati che l'host abbia `RELAY_URL`+`ROOM_CODE` e che l'URL del client abbia `?room=<CODE>`. |
| Emulatore: i client non si collegano | Serve `adb forward tcp:8080 tcp:8080`; usa `localhost`, non il QR. |
