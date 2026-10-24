import type { StoneChatStone } from './StoneChatStone';

export interface StoneChatMessageResponse {
  text: string;
  stones: StoneChatStone[];
}
