import { vi } from 'vitest';
import * as chatbotApi from '../../../../api/chatbotApi';
import type { StoneChatMessageResponse } from '../../../../api/dto/StoneChatMessageResponse';
import type { StoneChatHistoryMessage } from '../../../../api/dto/StoneChatHistoryResponse';
import { ChatStreamEventType } from '../../../../enums/ChatStreamEventType';

export function stubChatStream(reply: () => Promise<StoneChatMessageResponse>) {
  let messages: StoneChatHistoryMessage[] = [];
  vi.spyOn(chatbotApi, 'getChatHistory').mockImplementation(async conversationId => ({ conversationId, messages }));
  return vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation(async (request, onEvent) => {
    const identity = { conversationId: request.conversationId, turnId: request.turnId };
    onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence: 1 } });
    const answer = await reply();
    messages = [...messages.filter(message => message.turnId !== request.turnId),
      { role: 'USER', text: request.message, context: request.context ?? null, stones: [], turnId: request.turnId },
      { role: 'ASSISTANT', ...answer, context: null, turnId: request.turnId }];
    onEvent({ event: ChatStreamEventType.TextDelta, data: { ...identity, sequence: 2, text: answer.text } });
    const completed = { ...identity, sequence: 3, ...answer };
    onEvent({ event: ChatStreamEventType.Completed, data: completed }); return completed;
  });
}
