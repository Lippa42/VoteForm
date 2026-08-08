// Protocollo WebSocket condiviso dai client web.
//
// Rispecchia 1:1 i tipi Kotlin in
// admin-android/engine/.../protocol/Protocol.kt.
// Il campo discriminante è "type" (vedi EngineJson.classDiscriminator).

export type ClientRole = "VIEWER" | "SPECTATOR";

// --- Client → Host: intenzioni (validate dall'autorità) ---
export type ClientIntent =
  | { type: "join"; pin: string; name?: string; role: ClientRole }
  | { type: "submit_answer"; turnId: string; optionIds: string[] }
  | {
      type: "cast_vote";
      turnId: string;
      targetId: string;
      // Voto per ciascun criterio: criterionId → valore.
      values: Record<string, number>;
      specialVoteId?: string;
    }
  | { type: "buzz"; turnId: string }
  | { type: "reaction"; emoji: string };

// --- Host → Client: stato (unica verità) ---
export type RoomPhase =
  | "LOBBY"
  | "SETUP"
  | "INPUT"
  | "LOCKED"
  | "COMPUTING"
  | "REVEAL"
  | "STANDINGS"
  | "FINISHED";

export interface Standing {
  competitorId: string;
  points: number;
  rank: number;
}

export interface Match {
  id: string;
  competitorIds: string[];
  round: number;
}

export type ServerState =
  | { type: "snapshot"; phase: RoomPhase; room: unknown; standings: Standing[] }
  | {
      type: "turn";
      turnId: string;
      phase: RoomPhase;
      timerSeconds?: number;
      locked?: boolean;
    }
  | { type: "reveal"; turnId: string; standings: Standing[] }
  | { type: "bracket"; matches: Match[]; nextMatchId?: string };

export function encodeIntent(intent: ClientIntent): string {
  return JSON.stringify(intent);
}

export function decodeState(raw: string): ServerState {
  return JSON.parse(raw) as ServerState;
}
