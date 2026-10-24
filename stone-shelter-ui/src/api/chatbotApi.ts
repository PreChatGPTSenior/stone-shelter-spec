import { ChatApiError } from './ChatApiError';
export { ChatApiError } from './ChatApiError';
import { chatStreamTimeout, chatProblemError, consumeChatStream } from './chatbotStream';
import type { StoneChatStreamEvent, StoneChatStreamCompleted } from './dto/StoneChatStreamEvent';
import type { StoneChatMessageRequest } from './dto/StoneChatMessageRequest';
import type { StoneChatMessageResponse } from './dto/StoneChatMessageResponse';
import type { StoneChatHistoryResponse } from './dto/StoneChatHistoryResponse';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function validAnswer(value: unknown): value is StoneChatMessageResponse {
  if (!value || typeof value !== 'object') return false;
  const response = value as StoneChatMessageResponse;
  return typeof response.text === 'string' && Boolean(response.text.trim()) && Array.isArray(response.stones)
    && response.stones.every(stone => stone && Number.isSafeInteger(stone.id) && stone.id > 0
      && typeof stone.name === 'string' && Boolean(stone.name.trim()));
}

async function request<T>(path: string, options: RequestInit, validate: (value: unknown) => value is T, bounded = false): Promise<T> {
  const controller = bounded ? new AbortController() : undefined;
  const timer = controller ? setTimeout(() => controller.abort(new ChatApiError(0, 'CHAT_STREAM_TIMEOUT', 'History request timed out. Restore history manually.')), chatStreamTimeout()) : undefined;
  try {
  let response: Response;
  try { const pending = fetch(`${(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')}${path}`, { ...options, credentials: 'include', ...(controller ? { signal: controller.signal } : {}) }); response = controller ? await abortable(pending, controller.signal) : await pending; }
  catch (error) { if (error instanceof ChatApiError) throw error; throw controller?.signal.aborted ? controller.signal.reason : new ChatApiError(0, 'CHAT_UNAVAILABLE', 'Chat could not be reached. Restore history manually.'); }
  let body: unknown;
  try { body = controller ? await abortable(response.json(), controller.signal) : await response.json(); }
  catch (error) { if (error instanceof ChatApiError) throw error; throw new ChatApiError(response.status, 'CHAT_UNAVAILABLE', 'Chat returned an unreadable response. Restore history manually.'); }
  if (!response.ok) {
    const problem = body as { code?: unknown; detail?: unknown } | null;
    const retryAfter = Number(response.headers.get('Retry-After'));
    if (response.status === 429 && (!Number.isInteger(retryAfter) || retryAfter <= 0))
      throw new ChatApiError(503, 'CHAT_UNAVAILABLE', 'Chat returned unknown admission state. Restore history manually.');
    throw new ChatApiError(response.status, typeof problem?.code === 'string' ? problem.code : `HTTP_${response.status}`,
      typeof problem?.detail === 'string' ? problem.detail : 'Chat request failed.', response.status === 429 ? retryAfter : 0);
  }
  if (!validate(body)) throw new ChatApiError(503, 'CHAT_UNAVAILABLE', 'Chat returned invalid history or response. Restore history manually.');
  return body;
  } finally { if (timer !== undefined) clearTimeout(timer); controller?.abort(); }
}

export async function sendChatMessage(requestBody: StoneChatMessageRequest): Promise<StoneChatMessageResponse> {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockChatbotApi')).sendMockChatMessage(requestBody);
  return request('/api/v1/chat/messages', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(requestBody) }, validAnswer);
}

export async function getChatHistory(conversationId: string): Promise<StoneChatHistoryResponse> {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockChatbotApi')).getMockChatHistory(conversationId);
  return request(`/api/v1/chat/conversations/${encodeURIComponent(conversationId)}/messages`, { method: 'GET' },
    (value): value is StoneChatHistoryResponse => {
      if (!value || typeof value !== 'object') return false;
      const history = value as StoneChatHistoryResponse;
      if (history.conversationId !== conversationId || !Array.isArray(history.messages) || history.messages.length % 2 !== 0) return false;
      const turnIds = new Set<string>();
      for (let index = 0; index < history.messages.length; index += 2) {
        const user = history.messages[index]; const assistant = history.messages[index + 1];
        if (!user || !assistant || user.role !== 'USER' || assistant.role !== 'ASSISTANT' || !uuid.test(user.turnId)
          || user.turnId !== assistant.turnId || turnIds.has(user.turnId) || typeof user.text !== 'string'
          || !Array.isArray(user.stones) || user.stones.length !== 0 || assistant.context !== null || !validAnswer(assistant)
          || (user.context !== null && (!user.context || typeof user.context !== 'object'
            || !(user.context.stoneId === null || Number.isSafeInteger(user.context.stoneId) && (user.context.stoneId ?? 0) > 0)))) return false;
        turnIds.add(user.turnId);
      }
      return true;
    }, true);
}


function abortable<T>(work: Promise<T>, signal: AbortSignal): Promise<T> {
  if (signal.aborted) return Promise.reject(signal.reason);
  return new Promise<T>((resolve, reject) => {
    const abort = () => reject(signal.reason);
    signal.addEventListener('abort', abort, { once: true });
    work.then(resolve, reject).finally(() => signal.removeEventListener('abort', abort));
  });
}

/** API-only transport; explicit mock dispatch belongs to the mode boundary. */
export async function streamApiChatMessage(requestBody: StoneChatMessageRequest, onEvent: (event: StoneChatStreamEvent) => void, signal?: AbortSignal): Promise<StoneChatStreamCompleted> {
  const timeout = chatStreamTimeout(); const deadline = performance.now() + timeout;
  const controller = new AbortController();
  const externalAbort = () => controller.abort(new ChatApiError(0, 'CHAT_STREAM_INTERRUPTED', 'Chat delivery was interrupted. Restore history manually.'));
  signal?.addEventListener('abort', externalAbort, { once: true }); if (signal?.aborted) externalAbort();
  const timer = setTimeout(() => controller.abort(new ChatApiError(0, 'CHAT_STREAM_TIMEOUT', 'Chat delivery timed out. Restore history manually.')), timeout);
  try {
    const response = await abortable(fetch(`${(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')}/api/v1/chat/messages/stream`, {
      credentials: 'include', method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, body: JSON.stringify(requestBody), signal: controller.signal,
    }), controller.signal);
    if (!response.ok) {
      let problem: unknown; try { problem = await abortable(response.json(), controller.signal); }
      catch (error) { if (error instanceof ChatApiError) throw error; throw new ChatApiError(response.status, 'CHAT_UNAVAILABLE', 'Chat returned an unreadable problem. Restore history manually.'); }
      throw chatProblemError(response.status, problem, response.headers.get('Retry-After'));
    }
    if (response.status !== 200 || !/^text\/event-stream(?:\s*;|$)/i.test(response.headers.get('Content-Type') ?? '') || !response.body)
      throw new ChatApiError(0, 'CHAT_STREAM_PROTOCOL_ERROR', 'Chat did not return an event stream. Restore history manually.');
    return await consumeChatStream(response.body.getReader(), requestBody, onEvent, controller.signal, deadline);
  } catch (error) {
    if (error instanceof ChatApiError) throw error;
    throw new ChatApiError(0, 'CHAT_UNAVAILABLE', 'Chat delivery could not be reached. Restore history manually.');
  } finally {
    clearTimeout(timer); signal?.removeEventListener('abort', externalAbort); controller.abort();
  }
}

/** Mode selection happens once before network work; real failures never choose mocks. */
export async function sendChatMessageStream(requestBody: StoneChatMessageRequest, onEvent: (event: StoneChatStreamEvent) => void, signal?: AbortSignal): Promise<StoneChatStreamCompleted> {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockChatbotStream')).sendMockChatStream(requestBody, onEvent, signal);
  return streamApiChatMessage(requestBody, onEvent, signal);
}
