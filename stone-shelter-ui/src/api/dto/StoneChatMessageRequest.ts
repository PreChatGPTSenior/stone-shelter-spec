import type { StoneChatContext } from './StoneChatContext';

export interface StoneChatMessageRequest {
  conversationId: string;
  turnId: string;
  message: string;
  context?: StoneChatContext | null;
}
