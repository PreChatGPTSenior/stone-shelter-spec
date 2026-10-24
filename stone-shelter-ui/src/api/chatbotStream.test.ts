import { expect, test, vi } from 'vitest';
import { consumeChatStream } from './chatbotStream';
import { ChatStreamEventType } from '../enums/ChatStreamEventType';

const identity = { conversationId: '9960a79a-ae24-4c89-9f54-51103bcb00c9', turnId: '8103df2b-dad2-4f4c-9701-5e88739a2dda' };
const encode = new TextEncoder();
const frame = (event: string, sequence: number, rest = {}) => `event: ${event}\r\ndata: ${JSON.stringify({ ...identity, sequence, ...rest })}\r\n\r\n`;
function input(text: string, split = false, close = true) {
  const cancel = vi.fn(); const bytes = encode.encode(text);
  const stream = new ReadableStream<Uint8Array>({ start(controller) { if (split) for (const byte of bytes) controller.enqueue(Uint8Array.of(byte)); else controller.enqueue(bytes); if (close) controller.close(); }, cancel });
  return { stream, cancel, reader: stream.getReader() };
}
const signal = () => new AbortController().signal;

test('split UTF-8, CRLF, bare CR, comments and multiline JSON preserve exact text and release terminal reader', async () => {
  const text = '  🪨\r\n<&> '; const events: string[] = [];
  const started = `: keep-alive\r\nevent: started\r\ndata: {\r\ndata: "conversationId":"${identity.conversationId}",\r\ndata: "turnId":"${identity.turnId}","sequence":1}\r\n\r\n`;
  const source = input(started + frame('text-delta', 2, { text }).replaceAll('\r\n', '\r') + frame('completed', 3, { text, stones: [{ id: 7, name: 'Fixture stone' }] }), true, false);
  expect(await consumeChatStream(source.reader, identity, event => events.push(event.event), signal())).toMatchObject({ text, stones: [{ id: 7, name: 'Fixture stone' }] });
  expect(events).toEqual(['started', 'text-delta', 'completed']); expect(source.cancel).toHaveBeenCalledTimes(1); expect(source.stream.locked).toBe(false);
});

test('no-delta replay is valid and complete frames are processed instead of buffering the full response', async () => {
  const replay = input(frame('started', 1) + frame('completed', 2, { text: 'Retained.', stones: [] }));
  expect(await consumeChatStream(replay.reader, identity, () => {}, signal())).toMatchObject({ text: 'Retained.' });
  let body = frame('started', 1); let text = '';
  for (let sequence = 2; sequence < 22; sequence++) { text += 'hello'; body += frame('text-delta', sequence, { text: 'hello' }); }
  body += frame('completed', 22, { text, stones: [] }); expect(encode.encode(body).length).toBeGreaterThan(1024);
  expect(await consumeChatStream(input(body).reader, identity, () => {}, signal(), performance.now() + 1000, 1024)).toMatchObject({ text });
});

test.each([
  frame('text-delta', 1, { text: 'missing started' }),
  frame('started', 2),
  frame('started', 1) + frame('text-delta', 3, { text: 'gap' }),
  frame('started', 1) + frame('text-delta', 2, { text: 'one' }) + frame('text-delta', 2, { text: 'duplicate' }),
  frame('started', 1) + frame('completed', 2, { text: 'foreign', stones: [], turnId: '11111111-1111-1111-1111-111111111111' }),
  frame('started', 1) + frame('completed', 2, { text: 'extra', stones: [], source: 'private' }),
  frame('started', 1) + frame('completed', 2, { text: 'bad links', stones: [{ id: 0, name: 'Invalid' }] }),
  frame('started', 1) + frame('completed', 2, { text: 'bad links', stones: [{ id: 7, name: ' ' }] }),
  frame('started', 1) + frame('completed', 2, { text: 'bad links', stones: [{ id: 7, name: 'A' }, { id: 7, name: 'B' }] }),
  frame('started', 1) + frame('text-delta', 2, { text: '' }),
  frame('started', 1) + frame('text-delta', 2, { text: 'original' }) + frame('completed', 3, { text: 'changed', stones: [] }),
  'event: started\ndata: {\n\n',
  'event: started\n\n',
  frame('unknown', 1),
])('invalid named payload/identity/sequence/text requires recovery: %s', async body => {
  const source = input(body, true, false);
  await expect(consumeChatStream(source.reader, identity, () => {}, signal())).rejects.toMatchObject({ status: 0, code: 'CHAT_STREAM_PROTOCOL_ERROR' });
  expect(source.cancel).toHaveBeenCalledTimes(1); expect(source.stream.locked).toBe(false);
});

test('premature EOF and invalid UTF-8 cannot complete an answer', async () => {
  await expect(consumeChatStream(input(frame('started', 1) + frame('text-delta', 2, { text: 'partial' })).reader, identity, () => {}, signal())).rejects.toMatchObject({ code: 'CHAT_STREAM_PROTOCOL_ERROR' });
  const stream = new ReadableStream<Uint8Array>({ start(controller) { controller.enqueue(Uint8Array.of(0xff)); controller.close(); } });
  await expect(consumeChatStream(stream.getReader(), identity, () => {}, signal())).rejects.toMatchObject({ code: 'CHAT_STREAM_PROTOCOL_ERROR' }); expect(stream.locked).toBe(false);
});

test('frame and active-text bounds reject rather than truncate; error exposes server retry guidance', async () => {
  await expect(consumeChatStream(input(': ' + 'x'.repeat(1025)).reader, identity, () => {}, signal(), performance.now() + 1000, 1024)).rejects.toMatchObject({ code: 'CHAT_STREAM_PROTOCOL_ERROR' });
  let body = frame('started', 1); for (let sequence = 2; sequence < 22; sequence++) body += frame('text-delta', sequence, { text: 'x'.repeat(100) });
  await expect(consumeChatStream(input(body).reader, identity, () => {}, signal(), performance.now() + 1000, 1024)).rejects.toMatchObject({ code: 'CHAT_STREAM_PROTOCOL_ERROR' });
  const events: string[] = [];
  await expect(consumeChatStream(input(frame('started', 1) + frame('error', 2, { problem: { status: 503, code: 'CHAT_STREAM_CAPACITY_EXCEEDED', detail: 'Busy', retryAfterSeconds: 1 } })).reader, identity, event => events.push(event.event), signal())).rejects.toMatchObject({ status: 503, code: 'CHAT_STREAM_CAPACITY_EXCEEDED', retryAfter: 1 });
  expect(events).toEqual([ChatStreamEventType.Started, ChatStreamEventType.Error]);
});

test('abort interrupts a pending read and releases its reader without a server cancellation claim', async () => {
  const cancel = vi.fn(); const stream = new ReadableStream<Uint8Array>({ cancel }); const controller = new AbortController();
  const pending = consumeChatStream(stream.getReader(), identity, () => {}, controller.signal); controller.abort();
  await expect(pending).rejects.toMatchObject({ status: 0, code: 'CHAT_STREAM_INTERRUPTED' }); expect(cancel).toHaveBeenCalledTimes(1); expect(stream.locked).toBe(false);
});
