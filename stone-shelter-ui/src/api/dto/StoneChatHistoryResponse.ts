import type { ChatMessageRole } from '../../enums/ChatMessageRole';
import type { StoneChatContext } from './StoneChatContext';
import type { StoneChatStone } from './StoneChatStone';

export interface StoneChatHistoryMessage {
  role: ChatMessageRole;
  text: string;
  stones: StoneChatStone[];
  context: StoneChatContext | null;
  turnId: string;
}

export interface StoneChatHistoryResponse {
  conversationId: string;
  messages: StoneChatHistoryMessage[];
}
