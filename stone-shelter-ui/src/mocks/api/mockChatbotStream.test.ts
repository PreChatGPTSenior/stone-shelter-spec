import { beforeEach, expect, test, vi } from 'vitest';
import { sendMockChatStream } from './mockChatbotStream';
import { getMockChatHistory } from './mockChatbotApi';
import { ChatStreamMockScenario } from '../../enums/ChatStreamMockScenario';
import { ChatStreamEventType } from '../../enums/ChatStreamEventType';
import type { StoneChatStreamEvent } from '../../api/dto/StoneChatStreamEvent';

const request = { conversationId: '9960a79a-ae24-4c89-9f54-51103bcb00c9', turnId: '8103df2b-dad2-4f4c-9701-5e88739a2dda', message: 'Literal request.', context: { stoneId: 3 } };
beforeEach(() => sessionStorage.clear());

test('default mock commits before fragments and replay does not duplicate or renew retention', async () => {
  const events: StoneChatStreamEvent[] = []; let committed = false;
  const completed = await sendMockChatStream(request, event => { events.push(event); if (event.event === ChatStreamEventType.TextDelta) committed = Boolean(sessionStorage.getItem(`stone-chat-mock:${request.conversationId}`)); });
  expect(committed).toBe(true); expect(events.filter(event => event.event === ChatStreamEventType.TextDelta).map(event => event.data.text).join('')).toBe(completed.text);
  expect(events.map(event => event.data.sequence)).toEqual(events.map((_, index) => index + 1)); expect(completed.stones.map(stone => stone.id)).toEqual([1, 3]);
  const stored = sessionStorage.getItem(`stone-chat-mock:${request.conversationId}`); const replay: StoneChatStreamEvent[] = [];
  await sendMockChatStream(request, event => replay.push(event)); expect(replay.map(event => event.event)).toEqual(['started', 'completed']); expect(sessionStorage.getItem(`stone-chat-mock:${request.conversationId}`)).toBe(stored);
  await expect(sendMockChatStream({ ...request, message: 'Changed.' }, () => {})).rejects.toMatchObject({ code: 'CHAT_TURN_CONFLICT' });
  expect((await getMockChatHistory(request.conversationId)).messages).toHaveLength(2);
});

test('interrupted delivery leaves a saved pair for manual restore; processing error leaves none', async () => {
  const events: StoneChatStreamEvent[] = [];
  await expect(sendMockChatStream(request, event => events.push(event), undefined, ChatStreamMockScenario.Interrupted)).rejects.toMatchObject({ code: 'CHAT_STREAM_INTERRUPTED' });
  expect(events.map(event => event.event)).toEqual(['started', 'text-delta']); expect((await getMockChatHistory(request.conversationId)).messages).toHaveLength(2);
  sessionStorage.clear(); const errors: StoneChatStreamEvent[] = [];
  await expect(sendMockChatStream(request, event => errors.push(event), undefined, ChatStreamMockScenario.Error)).rejects.toMatchObject({ code: 'CHAT_UNAVAILABLE' });
  expect(errors.map(event => event.event)).toEqual(['started', 'error']); expect((await getMockChatHistory(request.conversationId)).messages).toEqual([]);
});

test('mock operation has no network/cookie dependency and transport abort never deletes a saved pair', async () => {
  const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
  try {
    const controller = new AbortController();
    await expect(sendMockChatStream(request, event => { if (event.event === ChatStreamEventType.Started) controller.abort(); }, controller.signal)).rejects.toMatchObject({ code: 'CHAT_STREAM_INTERRUPTED' });
    expect((await getMockChatHistory(request.conversationId)).messages).toHaveLength(2); expect(fetch).not.toHaveBeenCalled();
  } finally { vi.unstubAllGlobals(); }
});
