# web — client Visualizzatore + Spettatore

Client web (Svelte + TypeScript + Vite, PWA) **serviti dall'host Android** sulla
rete locale. Non contengono logica di gioco: mostrano lo stato ricevuto via
WebSocket e inviano *intenti*.

- `viewer/` — vista TV: classifica scorrevole, domande, reveal, animazioni.
- `spectator/` — vista telefono: risposte, voti, buzzer, reazioni.
- `src/shared/` — tipi e protocollo condivisi. `protocol.ts` rispecchia i tipi
  Kotlin del modulo `engine`.

## Stato

Impostato il protocollo condiviso e la configurazione di build (due entry point).
Le viste `viewer` e `spectator` verranno costruite dopo aver validato il modello
dati e definito il server Ktor che le serve.

## Sviluppo

```bash
npm install
npm run dev
```
