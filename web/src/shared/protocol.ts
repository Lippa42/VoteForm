// Protocollo WebSocket condiviso dai client web.
//
// Rispecchia i tipi Kotlin in
// admin-android/engine/.../protocol/Protocol.kt.
// Il campo discriminante è "type" (vedi EngineJson.classDiscriminator).

export type ClientRole = "VIEWER" | "SPECTATOR";
export type SelectionType = "SINGLE" | "MULTIPLE";

export type RoomPhase =
  | "LOBBY" | "SETUP" | "INPUT" | "LOCKED" | "COMPUTING" | "REVEAL" | "STANDINGS" | "FINISHED";

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

// --- Tipi di supporto ---
export interface PublicOption { id: string; text: string }
export interface PublicPrompt {
  title: string;
  imageAssetId?: string;
  options: PublicOption[];
  selection: SelectionType;
}
export interface PlayerInfo { id: string; name: string; score: number }
export interface VoteCriterionView { id: string; label: string; weight: number }
export interface Standing { competitorId: string; points: number; rank: number }
export interface Match { id: string; competitorIds: string[]; round: number }

// --- Host → Client: stato (unica verità) ---
export type ServerState =
  | { type: "snapshot"; phase: RoomPhase; room: unknown; standings: Standing[] }
  | {
      type: "turn";
      turnId: string;
      phase: RoomPhase;
      prompt?: PublicPrompt;
      index?: number;
      total?: number;
      timerSeconds?: number;
      locked?: boolean;
    }
  | {
      type: "vote_turn";
      turnId: string;
      phase: RoomPhase;
      promptTitle: string;
      competitorId: string;
      competitorName: string;
      criteria: VoteCriterionView[];
      scaleMin: number;
      scaleMax: number;
      scaleStep: number;
      index: number;
      total: number;
      locked?: boolean;
    }
  | { type: "progress"; turnId: string; answered: number; total: number }
  | { type: "players"; players: PlayerInfo[] }
  | {
      type: "reveal";
      turnId: string;
      standings: Standing[];
      correctOptionIds?: string[];
      subjectId?: string;
      subjectScore?: number;
    }
  | { type: "bracket"; matches: Match[]; nextMatchId?: string };

export function encodeIntent(intent: ClientIntent): string {
  return JSON.stringify(intent);
}

export function decodeState(raw: string): ServerState {
  return JSON.parse(raw) as ServerState;
}
