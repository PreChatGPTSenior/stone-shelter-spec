import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { getChatHistory, sendChatMessage } from './chatbotApi';

const request = { turnId: '8103df2b-dad2-4f4c-9701-5e88739a2dda', conversationId: '9960a79a-ae24-4c89-9f54-51103bcb00c9', message: '  A stone please  ', context: { stoneId: 3 } };
const response = { text: 'Here are some stones you might like.', stones: [{ id: 1, name: 'Mars' }, { id: 3, name: 'Luna' }] };

beforeEach(() => { sessionStorage.clear(); });

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.stubEnv('MODE', 'mock'); });

test('API mode posts only the new message and context through the configured transport', async () => {
  vi.stubEnv('MODE', 'api');
  vi.stubEnv('VITE_API_BASE_URL', 'https://backend.example/');
  const fetch = vi.fn().mockResolvedValue(Response.json(response));
  vi.stubGlobal('fetch', fetch);
  expect(await sendChatMessage(request)).toEqual(response);
  expect(fetch).toHaveBeenCalledExactlyOnceWith('https://backend.example/api/v1/chat/messages', {
    credentials: 'include', method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  });
});

test('mock mode returns an independent fixed response without fetch', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const first = await sendChatMessage(request);
  expect(first).toEqual(response);
  first.stones.reverse();
  expect(await sendChatMessage({ ...request, turnId: crypto.randomUUID(), message: 'Other topic' })).toEqual(response);
  expect(fetch).not.toHaveBeenCalled();
});

test('ordinary modes use the API and accept a text response without stone suggestions', async () => {
  const fetch = vi.fn().mockImplementation(async () => Response.json({ text: 'Hello', stones: [] }));
  vi.stubGlobal('fetch', fetch);
  for (const mode of ['development', 'production', 'api']) {
    vi.stubEnv('MODE', mode);
    expect(await sendChatMessage(request)).toEqual({ text: 'Hello', stones: [] });
  }
  expect(fetch).toHaveBeenCalledTimes(3);
});

test.each([
  null, {}, { text: ' ', stones: [] }, { text: 'Hi', stones: null },
  { text: 'Hi', stones: [null] }, { text: 'Hi', stones: [{ id: 0, name: 'Mars' }] },
  { text: 'Hi', stones: [{ id: Number.MAX_SAFE_INTEGER + 1, name: 'Mars' }] },
  { text: 'Hi', stones: [{ id: 1, name: ' ' }] },
])('rejects a malformed chatbot response: %j', async body => {
  vi.stubEnv('MODE', 'api');
  const fetch = vi.fn().mockResolvedValue(Response.json(body));
  vi.stubGlobal('fetch', fetch);
  await expect(sendChatMessage(request)).rejects.toMatchObject({ code: 'CHAT_UNAVAILABLE' });
  expect(fetch).toHaveBeenCalledTimes(1);
});

test('preserves ProblemDetail and normalizes network/non-JSON errors without mock fallback', async () => {
  vi.stubEnv('MODE', 'api');
  const fetch = vi.fn().mockResolvedValue(Response.json({ detail: 'Message rejected' }, { status: 400 }));
  vi.stubGlobal('fetch', fetch);
  await expect(sendChatMessage(request)).rejects.toMatchObject({ status: 400, message: 'Message rejected' });
  fetch.mockRejectedValueOnce(new TypeError('Network'));
  await expect(sendChatMessage(request)).rejects.toMatchObject({ status: 0 });
  fetch.mockResolvedValueOnce(new Response('<html>Error</html>', { status: 502 }));
  await expect(sendChatMessage(request)).rejects.toMatchObject({ status: 502 });
  expect(fetch).toHaveBeenCalledTimes(3);
});


test('mock reload restores pair IDs and exact ordered references; replay and reads do not extend retention', async () => {
  vi.stubEnv('MODE', 'mock');
  await sendChatMessage(request);
  const key = `stone-chat-mock:${request.conversationId}`;
  const original = sessionStorage.getItem(key);
  expect(await getChatHistory(request.conversationId)).toMatchObject({ messages: [
    { role: 'USER', text: request.message, turnId: request.turnId, context: request.context },
    { role: 'ASSISTANT', ...response, turnId: request.turnId, context: null },
  ] });
  await sendChatMessage(request);
  expect(sessionStorage.getItem(key)).toBe(original);
  await expect(sendChatMessage({ ...request, message: 'changed' })).rejects.toMatchObject({ status: 409, code: 'CHAT_TURN_CONFLICT' });
  const stored = JSON.parse(original!); stored.expiresAt = Date.now() - 1;
  sessionStorage.setItem(key, JSON.stringify(stored));
  expect((await getChatHistory(request.conversationId)).messages).toEqual([]);
});

test('API metadata preserves Retry-After and malformed history fails closed without fallback', async () => {
  vi.stubEnv('MODE', 'api');
  const fetch = vi.fn().mockResolvedValue(Response.json({ code: 'CHAT_RATE_LIMITED', detail: 'Wait' }, { status: 429, headers: { 'Retry-After': '6' } }));
  vi.stubGlobal('fetch', fetch);
  await expect(sendChatMessage(request)).rejects.toMatchObject({ status: 429, code: 'CHAT_RATE_LIMITED', retryAfter: 6 });
  fetch.mockResolvedValueOnce(Response.json({ conversationId: request.conversationId, messages: [{ role: 'USER' }] }));
  await expect(getChatHistory(request.conversationId)).rejects.toMatchObject({ code: 'CHAT_UNAVAILABLE' });
});


test('native API streaming uses exactly one credentialed POST and never JSON or mock fallback', async () => {
  vi.stubEnv('MODE', 'api');
  const { streamApiChatMessage } = await import('./chatbotApi');
  const event = (name: string, sequence: number, fields = {}) => `event: ${name}\ndata: ${JSON.stringify({ conversationId: request.conversationId, turnId: request.turnId, sequence, ...fields })}\n\n`;
  const fetch = vi.fn().mockResolvedValue(new Response(event('started', 1) + event('completed', 2, response), { headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' } })); vi.stubGlobal('fetch', fetch);
  const onEvent = vi.fn(); expect(await streamApiChatMessage(request, onEvent)).toMatchObject(response);
  expect(fetch).toHaveBeenCalledTimes(1); expect(fetch.mock.calls[0][0]).toContain('/api/v1/chat/messages/stream'); expect(fetch.mock.calls[0][1]).toMatchObject({ method: 'POST', credentials: 'include', body: JSON.stringify(request) }); expect(onEvent).toHaveBeenCalledTimes(2);
  fetch.mockResolvedValueOnce(Response.json({ code: 'CHAT_STREAM_CAPACITY_EXCEEDED', detail: 'Busy' }, { status: 503, headers: { 'Retry-After': '1' } }));
  await expect(streamApiChatMessage(request, onEvent)).rejects.toMatchObject({ code: 'CHAT_STREAM_CAPACITY_EXCEEDED', status: 503, retryAfter: 1 }); expect(fetch).toHaveBeenCalledTimes(2);
  fetch.mockResolvedValueOnce(Response.json(response)); await expect(streamApiChatMessage(request, onEvent)).rejects.toMatchObject({ status: 0, code: 'CHAT_STREAM_PROTOCOL_ERROR' }); expect(fetch).toHaveBeenCalledTimes(3);
});

test('whole-attempt stream and history timeouts are finite even when fetch ignores abort', async () => {
  vi.stubEnv('MODE', 'api'); vi.stubEnv('VITE_CHAT_STREAM_TIMEOUT_MS', '10'); vi.useFakeTimers();
  try {
    const { streamApiChatMessage } = await import('./chatbotApi'); const fetch = vi.fn().mockImplementation(() => new Promise(() => {})); vi.stubGlobal('fetch', fetch);
    const stream = streamApiChatMessage(request, () => {}); const streamCheck = expect(stream).rejects.toMatchObject({ status: 0, code: 'CHAT_STREAM_TIMEOUT' }); await vi.advanceTimersByTimeAsync(10); await streamCheck;
    const history = getChatHistory(request.conversationId); const historyCheck = expect(history).rejects.toMatchObject({ status: 0, code: 'CHAT_STREAM_TIMEOUT' }); await vi.advanceTimersByTimeAsync(10); await historyCheck;
    expect(fetch).toHaveBeenCalledTimes(2);
  } finally { vi.useRealTimers(); }
});


test('explicit mock stream/history use no backend and real transport failure cannot select mock data', async () => {
  const { sendChatMessageStream } = await import('./chatbotApi'); const fetch = vi.fn().mockRejectedValue(new TypeError('Offline')); vi.stubGlobal('fetch', fetch);
  vi.stubEnv('MODE', 'mock'); const events = vi.fn(); expect(await sendChatMessageStream(request, events)).toMatchObject(response); expect(fetch).not.toHaveBeenCalled();
  expect((await getChatHistory(request.conversationId)).messages).toHaveLength(2); expect(fetch).not.toHaveBeenCalled();
  vi.stubEnv('MODE', 'api'); await expect(sendChatMessageStream(request, events)).rejects.toMatchObject({ code: 'CHAT_UNAVAILABLE' }); expect(fetch).toHaveBeenCalledTimes(1);
});
