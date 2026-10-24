import type { StoneChatMessageRequest } from '../../api/dto/StoneChatMessageRequest';
import type { StoneChatMessageResponse } from '../../api/dto/StoneChatMessageResponse';
import type { StoneChatHistoryResponse } from '../../api/dto/StoneChatHistoryResponse';
import { ChatApiError } from '../../api/chatbotApi';

interface StoredConversation { expiresAt: number; requests: StoneChatMessageRequest[]; history: StoneChatHistoryResponse }
const storageKey = (id: string) => `stone-chat-mock:${id}`;
function read(id: string): StoredConversation {
  const value = sessionStorage.getItem(storageKey(id));
  if (value) {
    const stored = JSON.parse(value) as StoredConversation;
    if (stored.expiresAt > Date.now()) return stored;
    sessionStorage.removeItem(storageKey(id));
  }
  return { expiresAt: 0, requests: [], history: { conversationId: id, messages: [] } };
}
export async function getMockChatHistory(id: string): Promise<StoneChatHistoryResponse> { return structuredClone(read(id).history); }
export async function sendMockChatMessage(request: StoneChatMessageRequest): Promise<StoneChatMessageResponse> {
  const stored = read(request.conversationId);
  const existing = stored.requests.findIndex(turn => turn.turnId === request.turnId);
  if (existing >= 0) {
    const original = stored.requests[existing];
    if (original.message !== request.message || (original.context === null || original.context === undefined) !== (request.context === null || request.context === undefined)
      || original.context?.stoneId !== request.context?.stoneId)
      throw new ChatApiError(409, 'CHAT_TURN_CONFLICT', 'Turn identity was committed with a different payload.');
    const assistant = stored.history.messages[existing * 2 + 1];
    return { text: assistant.text, stones: structuredClone(assistant.stones) };
  }
  const response = { text: 'Here are some stones you might like.', stones: [{ id: 1, name: 'Mars' }, { id: 3, name: 'Luna' }] };
  stored.requests.push(structuredClone(request));
  stored.history.messages.push({ role: 'USER', text: request.message, stones: [], context: request.context ?? null, turnId: request.turnId },
    { role: 'ASSISTANT', ...response, context: null, turnId: request.turnId });
  stored.expiresAt = Date.now() + 24 * 60 * 60 * 1000;
  sessionStorage.setItem(storageKey(request.conversationId), JSON.stringify(stored));
  return structuredClone(response);
}
