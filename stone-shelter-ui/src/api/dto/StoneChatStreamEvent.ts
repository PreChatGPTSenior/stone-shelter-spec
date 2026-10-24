import type { ChatStreamEventType } from '../../enums/ChatStreamEventType';
import type { StoneChatStone } from './StoneChatStone';

export interface StoneChatStreamIdentity {
  conversationId: string;
  turnId: string;
  sequence: number;
}
export interface StoneChatStreamTextDelta extends StoneChatStreamIdentity { text: string }
export interface StoneChatStreamCompleted extends StoneChatStreamIdentity { text: string; stones: StoneChatStone[] }
export interface StoneChatStreamProblem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  retryAfterSeconds?: number;
}
export interface StoneChatStreamError extends StoneChatStreamIdentity { problem: StoneChatStreamProblem }
export type StoneChatStreamEvent =
  | { event: ChatStreamEventType.Started; data: StoneChatStreamIdentity }
  | { event: ChatStreamEventType.TextDelta; data: StoneChatStreamTextDelta }
  | { event: ChatStreamEventType.Completed; data: StoneChatStreamCompleted }
  | { event: ChatStreamEventType.Error; data: StoneChatStreamError };
