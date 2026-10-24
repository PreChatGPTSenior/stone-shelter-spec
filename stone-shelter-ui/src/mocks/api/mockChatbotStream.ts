import { ChatApiError } from '../../api/ChatApiError';
import type { StoneChatMessageRequest } from '../../api/dto/StoneChatMessageRequest';
import type { StoneChatStreamCompleted, StoneChatStreamEvent } from '../../api/dto/StoneChatStreamEvent';
import { ChatStreamEventType } from '../../enums/ChatStreamEventType';
import { ChatStreamMockScenario } from '../../enums/ChatStreamMockScenario';
import { chatStreamScenarios } from '../data/chatStreamScenarios';
import { getMockChatHistory, sendMockChatMessage } from './mockChatbotApi';

export async function sendMockChatStream(
  request: StoneChatMessageRequest, onEvent: (event: StoneChatStreamEvent) => void,
  signal?: AbortSignal, scenario = ChatStreamMockScenario.Success,
): Promise<StoneChatStreamCompleted> {
  const interrupted = () => { if (signal?.aborted) throw new ChatApiError(0, 'CHAT_STREAM_INTERRUPTED', 'Mock delivery was interrupted. Restore history manually.'); };
  interrupted();
  const history = await getMockChatHistory(request.conversationId);
  const replay = history.messages.some(message => message.turnId === request.turnId);
  const identity = { conversationId: request.conversationId, turnId: request.turnId };
  if (!replay && scenario === ChatStreamMockScenario.Error) {
    onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence: 1 } });
    const problem = { status: 503, code: 'CHAT_UNAVAILABLE', detail: 'Controlled mock processing failure.' };
    onEvent({ event: ChatStreamEventType.Error, data: { ...identity, sequence: 2, problem } });
    throw new ChatApiError(problem.status, problem.code, problem.detail);
  }
  // Existing adapter commits atomically first and rejects a changed retained payload.
  const saved = await sendMockChatMessage(request);
  let sequence = 1; onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence } });
  if (!replay) {
    const points = Array.from(saved.text); const size = chatStreamScenarios[scenario].chunkCodePoints;
    for (let offset = 0; offset < points.length; offset += size) {
      interrupted(); onEvent({ event: ChatStreamEventType.TextDelta, data: { ...identity, sequence: ++sequence, text: points.slice(offset, offset + size).join('') } });
      if (scenario === ChatStreamMockScenario.Interrupted) throw new ChatApiError(0, 'CHAT_STREAM_INTERRUPTED', 'Controlled incomplete mock delivery. Restore history manually.');
    }
  }
  interrupted();
  const completed = { ...identity, sequence: sequence + 1, text: saved.text, stones: structuredClone(saved.stones) };
  onEvent({ event: ChatStreamEventType.Completed, data: completed }); return structuredClone(completed);
}
