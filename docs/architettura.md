# Architettura — Stanze di sfida virtuale

> Proposta v0.1, da iterare. Versione navigabile con diagrammi pubblicata come
> artifact separato.

## Idea portante

**L'app Amministratore è anche il server.** Gira su Android e avvia al suo interno
un piccolo server locale (Ktor: HTTP + WebSocket). Visualizzatore e Spettatori sono
pagine web servite dall'host, aperte nel browser sulla stessa rete WiFi (onboarding
via QR). Tutto lo stato vive sul dispositivo dell'admin: **0% cloud** in v1.

Poiché i client mostrano lo stato e inviano solo *intenti*, tutta la logica di
gioco/voto/punteggio vive in un unico posto — il modulo `engine` in Kotlin —
affidabile, testabile, non manipolabile dai client.

## Topologia

```
                 ┌─────────────────────────────┐
                 │  AMMINISTRATORE · ANDROID    │
                 │  Server Ktor (HTTP + WS)     │
                 │  Motore di gioco · voto      │
                 │  Stato = fonte della verità  │
                 └──────────────┬──────────────┘
              LAN / WiFi — 0% cloud (WebSocket ⇅)
        ┌───────────────┬───────┴───────┬───────────────┐
   Visualizzatore   Spettatori ×N              Onboarding
   TV (browser)     telefoni (browser)         QR → URL / mDNS
```

Accortezze host Android: foreground service (server vivo a schermo bloccato),
WiFi/wake lock, discovery via QR + mDNS, piano B hotspot se la rete isola i client.

## Il modello a schema: la Stanza

Ogni sfida è **dati**, non codice separato. La `RoomDefinition` (vedi
`engine/model/RoomDefinition.kt`) contiene: `meta`, `mode` (modalità), `participants`,
`format`, `scoring`, `theme`, `media`, `interaction`. È anche l'unità di
salvataggio/export come template.

## Modalità come moduli

Ogni modalità è una variante di `GameModeConfig` (sealed): definisce cosa accade in
Setup/Input/Calcolo. In v1: **Quiz**, **Sfida a voti**, **Questionario**. Idee da
innestare dopo: indovina-il-numero, buzzer, disegna-e-indovina, scommesse a punti,
"chi lo ha detto", testa a testa.

## Motore di voto e punteggio

Regole **componibili** (`ScoringRules`): media/mediana/pesata, scarta estremi, voti
speciali con moltiplicatore, normalizzazione per giudice, reveal progressivo, tie-break
in ordine. Inoltre:

- **Elettorato** (`Electorate`): gruppi di votanti personalizzabili con peso (giuria,
  pubblico, chef ospite…) e modalità di assegnazione (admin / self-select / tutti pubblico).
- **Criteri di voto** (`VoteCriterion` su `VotingPrompt`): voto su più assi pesati
  (es. Gusto ×2, Presentazione, Originalità).
- **Eleggibilità** (`EligibilityRules`): matrice votante→bersaglio con peso, dal
  "no auto-voto" fino a "il gruppo A non vota affatto il gruppo B" (peso 0.0).

## Formati e sorteggio (v1)

`FormatConfig` (sealed): eliminazione (torneo), campionato (girone), tutti-contro-tutti,
2vs2. Sorteggio configurabile (casuale/teste di serie, evita rivincite) con
animazione su schermo. La modalità produce un risultato di match, il formato propone
il match successivo.

## Flusso delle fasi

`RoomPhase`: `LOBBY → (SETUP → INPUT → LOCKED → COMPUTING → REVEAL) → STANDINGS → … → FINISHED`.
Stato event-sourced: ogni azione è un evento; alla connessione il client riceve uno
`snapshot` e riallinea.

## Protocollo

Vedi `engine/protocol/Protocol.kt` (Kotlin) e `web/src/shared/protocol.ts` (mirror TS).

- **Client → Host** (`ClientIntent`): `join`, `submit_answer`, `cast_vote`, `buzz`, `reaction`.
- **Host → Client** (`ServerState`): `snapshot`, `turn`, `reveal`, `bracket`.

## Personalizzazione

Temi (palette/font/sfondo/logo), animazioni immagini (fade/slide/zoom/flip/ken-burns),
suoni, musica royalty-free (Pixabay, FMA, Incompetech, YouTube Audio Library, Uppbeat).
Spotify: solo controllo playback via SDK, in fase 2 (non è possibile ridistribuire i brani).

## Scope v1 vs fasi successive

- **v1**: host + onboarding, le 3 modalità, motore voto completo, formati + sorteggio,
  tema/media/musica RF, storico locale (Room DB).
- **Fase 2**: spettatori remoti (relay cloud leggero), controllo playback Spotify.

## Stato del codice

Implementato e verificato: modulo `engine` (modello dati + protocollo + esempi + test
che passano). Da fare: `server` (Ktor), `persistence` (Room), `app` (Compose), client
web `viewer`/`spectator`.
