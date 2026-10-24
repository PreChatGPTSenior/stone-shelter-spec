import { StrictMode, type ReactNode } from 'react';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import * as chatbotApi from '../../../../api/chatbotApi';
import type { StoneChatMessageRequest } from '../../../../api/dto/StoneChatMessageRequest';
import type { StoneChatHistoryMessage } from '../../../../api/dto/StoneChatHistoryResponse';
import { ChatStreamEventType } from '../../../../enums/ChatStreamEventType';
import { ChatMessageDeliveryState } from '../../../../enums/ChatMessageDeliveryState';
import type { StoneChatMessageResponse } from '../../../../api/dto/StoneChatMessageResponse';
import { ChatSessionProvider, useChatSession } from './ChatSessionProvider';

const response = { text: 'A choice for you', stones: [{ id: 3, name: 'Luna' }, { id: 1, name: 'Mars' }] };
function deferred() {
  let resolve!: (response: StoneChatMessageResponse) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<StoneChatMessageResponse>((accept, fail) => { resolve = accept; reject = fail; });
  return { promise, resolve, reject };
}
let savedHistory: StoneChatHistoryMessage[];
function stubStream(reply: (request: StoneChatMessageRequest) => Promise<StoneChatMessageResponse>) {
  return vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation(async (request, onEvent) => {
    const identity = { conversationId: request.conversationId, turnId: request.turnId };
    onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence: 1 } });
    const answer = await reply(request);
    savedHistory = [...savedHistory.filter(message => message.turnId !== request.turnId),
      { role: 'USER', text: request.message, context: request.context ?? null, stones: [], turnId: request.turnId },
      { role: 'ASSISTANT', ...answer, context: null, turnId: request.turnId }];
    onEvent({ event: ChatStreamEventType.TextDelta, data: { ...identity, sequence: 2, text: answer.text } });
    const completed = { ...identity, sequence: 3, ...answer };
    onEvent({ event: ChatStreamEventType.Completed, data: completed });
    return completed;
  });
}
beforeEach(() => {
  savedHistory = [];
  sessionStorage.clear(); window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(chatbotApi, 'getChatHistory').mockImplementation(async id => ({ conversationId: id, messages: savedHistory }));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); });
async function mount() {
  const chat = renderHook(useChatSession, { wrapper: ChatSessionProvider });
  await waitFor(() => expect(chat.result.current.restoring).toBe(false)); return chat;
}

test('reload preserves tab UUID, restores committed history, starts collapsed and discards draft', async () => {
  const first = await mount(); const id = first.result.current.conversationId;
  act(() => { first.result.current.setOpen(true); first.result.current.setDraft('draft'); }); first.unmount();
  const turnId = crypto.randomUUID();
  vi.mocked(chatbotApi.getChatHistory).mockResolvedValueOnce({ conversationId: id, messages: [
    { role: 'USER', text: 'saved', stones: [], context: { stoneId: 3 }, turnId },
    { role: 'ASSISTANT', ...response, context: null, turnId },
  ] });
  const next = await mount();
  expect(next.result.current).toMatchObject({ conversationId: id, open: false, draft: '', pending: false });
  expect(next.result.current.messages).toHaveLength(2);
});

test('duplicate sends are blocked and original context and pair ID survive navigation/collapse', async () => {
  const pending = deferred(); const send = stubStream(() => pending.promise);
  window.history.replaceState(null, '', '/stone-shelter/stones/3'); const chat = await mount();
  act(() => { chat.result.current.setDraft('first'); });
  act(() => { chat.result.current.send(); chat.result.current.send('duplicate'); });
  const request = send.mock.calls[0][0];
  expect(request).toEqual({ conversationId: chat.result.current.conversationId, turnId: expect.any(String), message: 'first', context: { stoneId: 3 } });
  expect(send).toHaveBeenCalledTimes(1);
  window.history.replaceState(null, '', '/stone-shelter/catalog');
  act(() => { chat.result.current.setOpen(false); chat.result.current.setDraft('next'); });
  await act(async () => { pending.resolve(response); });
  expect(chat.result.current.messages).toEqual([{ role: 'USER', text: 'first', stones: [], turnId: request.turnId, delivery: ChatMessageDeliveryState.Confirmed },
    { role: 'ASSISTANT', ...response, turnId: request.turnId, delivery: ChatMessageDeliveryState.Confirmed }]);
  expect(chat.result.current.draft).toBe('next');
});

test('dependency failure blocks send/retry, preserves draft and uncommitted user, then permits only deliberate original retry after recovery', async () => {
  const send = stubStream(vi.fn().mockRejectedValueOnce(new chatbotApi.ChatApiError(503, 'CHAT_UNAVAILABLE', 'Unavailable')).mockResolvedValue(response));
  const chat = await mount(); act(() => { chat.result.current.send('original'); });
  await waitFor(() => expect(chat.result.current.unavailable).toBe(true));
  act(() => { chat.result.current.setDraft('next draft'); chat.result.current.retry(); chat.result.current.send('bypass'); });
  expect(send).toHaveBeenCalledTimes(1);
  await act(async () => { await chat.result.current.restore(); });
  expect(chat.result.current.messages).toHaveLength(1); expect(chat.result.current.draft).toBe('next draft');
  expect(send).toHaveBeenCalledTimes(1);
  act(() => { chat.result.current.retry(); chat.result.current.retry(); });
  await waitFor(() => expect(chat.result.current.pending).toBe(false));
  expect(send.mock.calls[1][0]).toEqual(send.mock.calls[0][0]); expect(chat.result.current.messages).toHaveLength(2);
});

test('lost acknowledgement is reconciled by committed pair ID without another POST', async () => {
  const send = stubStream(async () => { throw new chatbotApi.ChatApiError(503, 'CHAT_UNAVAILABLE', 'Unknown commit'); });
  const chat = await mount(); act(() => { chat.result.current.send('original'); });
  await waitFor(() => expect(chat.result.current.unavailable).toBe(true));
  const request = send.mock.calls[0][0];
  vi.mocked(chatbotApi.getChatHistory).mockResolvedValueOnce({ conversationId: request.conversationId, messages: [
    { role: 'USER', text: request.message, stones: [], context: request.context ?? null, turnId: request.turnId },
    { role: 'ASSISTANT', ...response, context: null, turnId: request.turnId },
  ] });
  await act(async () => { await chat.result.current.restore(); });
  act(() => { chat.result.current.retry(); });
  expect(send).toHaveBeenCalledTimes(1); expect(chat.result.current.messages).toHaveLength(2); expect(chat.result.current.error).toBeUndefined();
});

test('manual quota retry observes cooldown and uses one user entry and unchanged turn identity', async () => {
  const chat = await mount(); vi.useFakeTimers();
  const send = stubStream(vi.fn().mockRejectedValueOnce(new chatbotApi.ChatApiError(429, 'CHAT_RATE_LIMITED', 'Wait', 2)).mockResolvedValue(response));
  await act(async () => { chat.result.current.send('original'); });
  act(() => { chat.result.current.retry(); }); expect(send).toHaveBeenCalledTimes(1);
  await act(async () => { vi.advanceTimersByTime(2100); }); expect(send).toHaveBeenCalledTimes(1);
  await act(async () => { chat.result.current.retry(); });
  expect(send.mock.calls[1][0]).toEqual(send.mock.calls[0][0]); expect(chat.result.current.messages).toHaveLength(2);
});

test('initial restoration blocks sends and failed restoration never fabricates empty history', async () => {
  let reject!: (error: Error) => void;
  vi.mocked(chatbotApi.getChatHistory).mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
  const send = vi.spyOn(chatbotApi, 'sendChatMessageStream'); const chat = renderHook(useChatSession, { wrapper: ChatSessionProvider });
  act(() => { chat.result.current.send('blocked'); }); expect(send).not.toHaveBeenCalled();
  await act(async () => { reject(new Error('Restore failed')); });
  expect(chat.result.current.unavailable).toBe(true); expect(chat.result.current.restoreError).toBe('Restore failed');
});

test('history quota has a separate manual cooldown and never automatically retries restoration', async () => {
  const chat = await mount(); vi.useFakeTimers();
  vi.mocked(chatbotApi.getChatHistory).mockRejectedValueOnce(new chatbotApi.ChatApiError(429, 'CHAT_RATE_LIMITED', 'Wait to restore', 2));
  await act(async () => { await chat.result.current.restore(); });
  expect(chat.result.current.restoreCooldown).toBe(2);
  expect(chat.result.current.cooldown).toBe(0);
  expect(chat.result.current.unavailable).toBe(true);
  await act(async () => { await chat.result.current.restore(); });
  expect(chatbotApi.getChatHistory).toHaveBeenCalledTimes(2);
  await act(async () => { vi.advanceTimersByTime(2100); });
  expect(chatbotApi.getChatHistory).toHaveBeenCalledTimes(2);
  await act(async () => { await chat.result.current.restore(); });
  expect(chatbotApi.getChatHistory).toHaveBeenCalledTimes(3);
  expect(chat.result.current.unavailable).toBe(false);
});

test('reload during pending work does not reconstruct or resend the abandoned request', async () => {
  const pending = deferred(); const send = stubStream(() => pending.promise);
  const first = await mount(); act(() => { first.result.current.send('first'); }); first.unmount();
  const next = await mount(); await act(async () => { pending.resolve(response); });
  expect(next.result.current.messages).toEqual([]); expect(send).toHaveBeenCalledTimes(1);
});

test('partial delivery remains incomplete and unlinked until authoritative manual recovery', async () => {
  const send = vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation(async (request, event) => {
    event({ event: ChatStreamEventType.Started, data: { conversationId: request.conversationId, turnId: request.turnId, sequence: 1 } });
    event({ event: ChatStreamEventType.TextDelta, data: { conversationId: request.conversationId, turnId: request.turnId, sequence: 2, text: 'Literal 🪨 fragment' } });
    throw new chatbotApi.ChatApiError(0, 'CHAT_STREAM_INTERRUPTED', 'Incomplete delivery');
  });
  const chat = await mount(); await act(async () => { chat.result.current.send('original'); });
  expect(chat.result.current.messages.at(-1)).toMatchObject({ text: 'Literal 🪨 fragment', stones: [], delivery: ChatMessageDeliveryState.Incomplete });
  expect(chat.result.current.unavailable).toBe(true);
  await act(async () => { await chat.result.current.restore(); });
  expect(send).toHaveBeenCalledTimes(1);
  expect(chat.result.current.messages.at(-1)?.delivery).toBe(ChatMessageDeliveryState.Incomplete);
});

test('completed answer survives a failed history synchronization and cannot be retried', async () => {
  const send = stubStream(async () => response); const chat = await mount();
  vi.mocked(chatbotApi.getChatHistory).mockRejectedValueOnce(new Error('History unavailable'));
  await act(async () => { chat.result.current.send('original'); });
  expect(chat.result.current.messages.at(-1)).toMatchObject({ ...response, delivery: ChatMessageDeliveryState.Confirmed });
  expect(chat.result.current.restoreError).toBe('History unavailable'); expect(chat.result.current.canRetry).toBe(false);
  act(() => { chat.result.current.retry(); chat.result.current.send('another'); });
  expect(send).toHaveBeenCalledTimes(1);
  await act(async () => { await chat.result.current.restore(); });
  expect(chat.result.current.unavailable).toBe(false); expect(chat.result.current.messages).toHaveLength(2);
});

test('stream capacity rejection remains a healthy manual retry with no automatic POST', async () => {
  const send = stubStream(vi.fn().mockRejectedValueOnce(new chatbotApi.ChatApiError(503, 'CHAT_STREAM_CAPACITY_EXCEEDED', 'Transport full', 1)).mockResolvedValue(response));
  const chat = await mount(); await act(async () => { chat.result.current.send('original'); });
  expect(chat.result.current.unavailable).toBe(false); expect(chat.result.current.canRetry).toBe(true);
  expect(chat.result.current.cooldown).toBe(1); expect(send).toHaveBeenCalledTimes(1);
});


test('StrictMode restores once and never duplicates an intentional POST', async () => {
  const send = stubStream(async () => response);
  const chat = renderHook(useChatSession, { wrapper: ({ children }: { children: ReactNode }) => <StrictMode><ChatSessionProvider>{children}</ChatSessionProvider></StrictMode> });
  await waitFor(() => expect(chat.result.current.restoring).toBe(false));
  expect(chatbotApi.getChatHistory).toHaveBeenCalledTimes(1);
  await act(async () => { chat.result.current.send('original'); });
  expect(send).toHaveBeenCalledTimes(1); expect(chat.result.current.messages).toHaveLength(2);
});

test('callbacks from a closed attempt cannot reopen or alter the confirmed message', async () => {
  let emit!: Parameters<typeof chatbotApi.sendChatMessageStream>[1];
  let original!: StoneChatMessageRequest;
  vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation(async (request, onEvent) => {
    emit = onEvent; original = request;
    const identity = { conversationId: request.conversationId, turnId: request.turnId };
    savedHistory = [{ role: 'USER', text: request.message, stones: [], context: request.context ?? null, turnId: request.turnId },
      { role: 'ASSISTANT', ...response, context: null, turnId: request.turnId }];
    onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence: 1 } });
    const completed = { ...identity, ...response, sequence: 2 };
    onEvent({ event: ChatStreamEventType.Completed, data: completed }); return completed;
  });
  const chat = await mount(); await act(async () => { chat.result.current.send('original'); });
  act(() => { emit({ event: ChatStreamEventType.TextDelta, data: { conversationId: original.conversationId, turnId: original.turnId, sequence: 3, text: 'Stale fragment' } }); });
  expect(chat.result.current.pending).toBe(false); expect(chat.result.current.messages.at(-1)).toMatchObject({ ...response, delivery: ChatMessageDeliveryState.Confirmed });
});


test('UUID letter case preserves restored conversation and accepted stream identity', async () => {
  const conversationId = 'abcdefab-1234-5678-9012-abcdefabcdef';
  sessionStorage.setItem('stone-chat-conversation', conversationId.toUpperCase());
  const send = vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation(async (request, onEvent) => {
    const identity = { conversationId: request.conversationId.toUpperCase(), turnId: request.turnId.toUpperCase() };
    onEvent({ event: ChatStreamEventType.Started, data: { ...identity, sequence: 1 } });
    savedHistory = [
      { role: 'USER', text: request.message, context: request.context ?? null, stones: [], turnId: request.turnId },
      { role: 'ASSISTANT', ...response, context: null, turnId: request.turnId },
    ];
    const completed = { ...identity, sequence: 2, ...response };
    onEvent({ event: ChatStreamEventType.Completed, data: completed });
    return completed;
  });
  const chat = await mount();
  expect(chat.result.current.conversationId).toBe(conversationId);
  await act(async () => { chat.result.current.send('original'); });
  expect(send).toHaveBeenCalledTimes(1);
  expect(chat.result.current.unavailable).toBe(false);
  expect(chat.result.current.messages).toHaveLength(2);
  expect(chat.result.current.messages.at(-1)).toMatchObject({ ...response, delivery: ChatMessageDeliveryState.Confirmed });
});
