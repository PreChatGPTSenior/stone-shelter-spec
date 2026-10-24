import { ChatApiError } from './ChatApiError';
import { ChatStreamEventType } from '../enums/ChatStreamEventType';
import type { StoneChatStreamCompleted, StoneChatStreamEvent, StoneChatStreamProblem } from './dto/StoneChatStreamEvent';

export const CHAT_STREAM_BUFFER_BYTES = 1024 * 1024;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const encoder = new TextEncoder();
const rejectionCodes = new Set(['CHAT_RATE_LIMITED', 'CHAT_CONVERSATION_BUSY', 'CHAT_TOKEN_BUDGET_EXCEEDED', 'CHAT_CAPACITY_EXCEEDED', 'CHAT_STREAM_CAPACITY_EXCEEDED']);
export function chatStreamTimeout(): number {
  const value = Number(import.meta.env.VITE_CHAT_STREAM_TIMEOUT_MS ?? 135000);
  if (!Number.isSafeInteger(value) || value <= 0 || value > 2147483647) throw new Error('VITE_CHAT_STREAM_TIMEOUT_MS must be a positive browser timer integer.');
  return value;
}
export function chatProblemError(status: number, value: unknown, headerRetry?: string | null): ChatApiError {
  const problem = value && typeof value === 'object' ? value as Partial<StoneChatStreamProblem> : {};
  const code = typeof problem.code === 'string' && problem.code ? problem.code : `HTTP_${status}`;
  const retryAfter = headerRetry == null ? problem.retryAfterSeconds ?? 0 : Number(headerRetry);
  if (rejectionCodes.has(code) && (!Number.isSafeInteger(retryAfter) || retryAfter <= 0))
    return new ChatApiError(0, 'CHAT_STREAM_PROTOCOL_ERROR', 'Chat returned unknown retry guidance. Restore history manually.');
  return new ChatApiError(status, code, typeof problem.detail === 'string' ? problem.detail : 'Chat request failed.', rejectionCodes.has(code) ? retryAfter : 0);
}
function invalid(message = 'Chat returned an invalid stream. Restore history manually.'): never {
  throw new ChatApiError(0, 'CHAT_STREAM_PROTOCOL_ERROR', message);
}
function fields(value: Record<string, unknown>, expected: string[]): boolean {
  return Object.keys(value).length === expected.length && expected.every(key => Object.hasOwn(value, key));
}

export async function consumeChatStream(
  reader: ReadableStreamDefaultReader<Uint8Array>, expected: { conversationId: string; turnId: string },
  onEvent: (event: StoneChatStreamEvent) => void, signal: AbortSignal,
  deadline = performance.now() + chatStreamTimeout(), limit = CHAT_STREAM_BUFFER_BYTES,
): Promise<StoneChatStreamCompleted> {
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let line = ''; let event = ''; let data: string[] = []; let frameBytes = 0;
  let skipLf = false; let endedAtCr = false; let sequence = 0; let started = false; let text = ''; let textBytes = 0; let deltas = false;
  let completed: StoneChatStreamCompleted | undefined;
  let terminal = false;
  const abortError = () => signal.reason instanceof ChatApiError ? signal.reason : new ChatApiError(0, 'CHAT_STREAM_INTERRUPTED', 'Chat delivery was interrupted. Restore history manually.');
  let rejectAbort: (reason: unknown) => void = () => {};
  const aborted = new Promise<never>((_, reject) => { rejectAbort = reject; });
  const abort = () => rejectAbort(abortError());
  signal.addEventListener('abort', abort, { once: true });
  const check = () => {
    if (signal.aborted) throw abortError();
    if (performance.now() >= deadline) throw new ChatApiError(0, 'CHAT_STREAM_TIMEOUT', 'Chat delivery timed out. Restore history manually.');
  };
  function dispatch() {
    if (!data.length) { if (event) invalid(); event = ''; return; }
    let parsed: unknown;
    try { parsed = JSON.parse(data.join('\n')); } catch { invalid(); }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) invalid();
    const payload = parsed as Record<string, unknown>;
    if (typeof payload.conversationId !== 'string' || typeof payload.turnId !== 'string' || !uuid.test(payload.conversationId) || !uuid.test(payload.turnId)
      || payload.conversationId.toLowerCase() !== expected.conversationId.toLowerCase() || payload.turnId.toLowerCase() !== expected.turnId.toLowerCase()
      || !Number.isSafeInteger(payload.sequence) || payload.sequence !== sequence + 1) invalid();
    sequence++;
    const identity = ['conversationId', 'turnId', 'sequence'];
    let validated: StoneChatStreamEvent;
    if (event === ChatStreamEventType.Started) {
      if (started || sequence !== 1 || !fields(payload, identity)) invalid();
      started = true; validated = { event: ChatStreamEventType.Started, data: payload as unknown as StoneChatStreamEvent['data'] & { sequence: number } };
    } else {
      if (!started) invalid();
      if (event === ChatStreamEventType.TextDelta) {
        if (!fields(payload, [...identity, 'text']) || typeof payload.text !== 'string' || payload.text.length === 0) invalid();
        textBytes += encoder.encode(payload.text).length; if (textBytes > limit) invalid('Chat answer exceeds the client bound. Restore history manually.');
        text += payload.text; deltas = true;
        validated = { event: ChatStreamEventType.TextDelta, data: payload as unknown as Extract<StoneChatStreamEvent, { event: ChatStreamEventType.TextDelta }>['data'] };
      } else if (event === ChatStreamEventType.Completed) {
        if (!fields(payload, [...identity, 'text', 'stones']) || typeof payload.text !== 'string' || !Array.isArray(payload.stones) || payload.stones.length > 5
          || payload.stones.some(stone => !stone || typeof stone !== 'object' || !fields(stone, ['id', 'name']) || !Number.isSafeInteger(stone.id) || stone.id <= 0 || typeof stone.name !== 'string' || !stone.name.trim())
          || new Set(payload.stones.map(stone => stone.id)).size !== payload.stones.length || encoder.encode(payload.text).length > limit || deltas && payload.text !== text) invalid();
        completed = payload as unknown as StoneChatStreamCompleted; terminal = true; validated = { event: ChatStreamEventType.Completed, data: completed };
      } else if (event === ChatStreamEventType.Error) {
        if (!fields(payload, [...identity, 'problem']) || !payload.problem || typeof payload.problem !== 'object' || Array.isArray(payload.problem)) invalid();
        const problem = payload.problem as StoneChatStreamProblem;
        if (!Number.isSafeInteger(problem.status) || problem.status < 400 || problem.status > 599 || typeof problem.code !== 'string' || !problem.code
          || problem.retryAfterSeconds !== undefined && (!Number.isSafeInteger(problem.retryAfterSeconds) || problem.retryAfterSeconds <= 0)) invalid();
        terminal = true; validated = { event: ChatStreamEventType.Error, data: payload as unknown as Extract<StoneChatStreamEvent, { event: ChatStreamEventType.Error }>['data'] };
        onEvent(validated); throw chatProblemError(problem.status, problem);
      } else invalid();
    }
    onEvent(validated); event = ''; data = [];
  }
  function finishLine() {
    if (!line) { dispatch(); frameBytes = 0; return; }
    if (line.startsWith(':')) return;
    const colon = line.indexOf(':'); const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1); if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
    // Other SSE fields never enable reconnect or fragment resumption.
  }
  try {
    while (!terminal) {
      check(); const chunk = await Promise.race([reader.read(), aborted]); check();
      if (chunk.done) { decoder.decode(); invalid('Chat delivery ended before completion. Restore history manually.'); }
      let offset = 0;
      while (offset < chunk.value.length && !terminal) {
        check();
        if (skipLf) {
          skipLf = false;
          if (chunk.value[offset] === 10) { if (!endedAtCr && ++frameBytes > limit) invalid(); offset++; continue; }
        }
        let end = offset; while (end < chunk.value.length && chunk.value[end] !== 10 && chunk.value[end] !== 13) end++;
        const newline = end < chunk.value.length;
        const size = end - offset + (newline ? 1 : 0); frameBytes += size; if (frameBytes > limit) invalid('Chat frame exceeds the client bound. Restore history manually.');
        const decoded = decoder.decode(chunk.value.subarray(offset, end + (newline ? 1 : 0)), { stream: true });
        line += newline ? decoded.slice(0, -1) : decoded;
        if (newline) { endedAtCr = line.length === 0; finishLine(); line = ''; skipLf = chunk.value[end] === 13; }
        offset = end + (newline ? 1 : 0);
      }
    }
    if (!completed) invalid();
    return completed;
  } catch (error) {
    if (error instanceof ChatApiError) throw error;
    invalid('Chat delivery is unreadable. Restore history manually.');
  } finally {
    signal.removeEventListener('abort', abort);
    void reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
