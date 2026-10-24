import { createContext, useContext, useEffect, useReducer, useRef, useState, type ReactNode } from 'react';
import { ChatApiError, getChatHistory, sendChatMessageStream } from '../../../../api/chatbotApi';
import type { StoneChatMessageRequest } from '../../../../api/dto/StoneChatMessageRequest';
import { ChatStreamEventType } from '../../../../enums/ChatStreamEventType';
import { ChatStreamPhase } from '../../../../enums/ChatStreamPhase';
import { ChatSessionActionType } from '../../../../enums/ChatSessionActionType';
import { resolvePageRoute } from '../../../../App/navigation/pageRoutes';
import { chatSessionReducer, historyMatchesConfirmed, initialChatSessionState, type ChatSessionAction } from './chatSessionState';

const healthyRejections = new Set(['CHAT_RATE_LIMITED', 'CHAT_CONVERSATION_BUSY', 'CHAT_TOKEN_BUDGET_EXCEEDED', 'CHAT_CAPACITY_EXCEEDED', 'CHAT_STREAM_CAPACITY_EXCEEDED', 'CHAT_INVALID_MODEL_OUTPUT', 'CHAT_SEMANTIC_LIMIT_EXCEEDED', 'CHAT_RAG_LIMIT_EXCEEDED']);
function useChatSessionState() {
  const [conversationId] = useState(() => {
    const saved = sessionStorage.getItem('stone-chat-conversation');
    if (saved && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(saved)) return saved.toLowerCase();
    const id = crypto.randomUUID(); sessionStorage.setItem('stone-chat-conversation', id); return id;
  });
  const [state, dispatchReact] = useReducer(chatSessionReducer, undefined, initialChatSessionState);
  const current = useRef(initialChatSessionState());
  const [open, setOpen] = useState(false); const [draft, setDraftValue] = useState(''); const [inputError, setInputError] = useState<string>();
  const [cooldown, setCooldown] = useState(0); const cooldownUntil = useRef(0);
  const [restoreCooldown, setRestoreCooldown] = useState(0); const restoreCooldownUntil = useRef(0);
  const mounted = useRef(false); const hydrationStarted = useRef(false); const busy = useRef(false); const recovering = useRef(false);
  const attempt = useRef(0); const activeTransport = useRef<AbortController | null>(null);
  function dispatch(action: ChatSessionAction) { current.current = chatSessionReducer(current.current, action); dispatchReact(action); }
  useEffect(() => {
    mounted.current = true;
    if (!hydrationStarted.current) { hydrationStarted.current = true; void restore(); }
    const pagehide = () => activeTransport.current?.abort(); window.addEventListener('pagehide', pagehide);
    return () => { mounted.current = false; window.removeEventListener('pagehide', pagehide); };
  }, []);
  useEffect(() => {
    if (!cooldown && !restoreCooldown) return;
    const timer = window.setInterval(() => { setCooldown(Math.max(0, Math.ceil((cooldownUntil.current - Date.now()) / 1000))); setRestoreCooldown(Math.max(0, Math.ceil((restoreCooldownUntil.current - Date.now()) / 1000))); }, 250);
    return () => window.clearInterval(timer);
  }, [cooldown, restoreCooldown]);
  async function loadHistory() {
    const history = await getChatHistory(conversationId);
    if (!mounted.current) return;
    if (!historyMatchesConfirmed(current.current, history)) throw new ChatApiError(0, 'CHAT_HISTORY_MISMATCH', 'Saved history does not match the confirmed answer. Restore history manually.');
    dispatch({ type: ChatSessionActionType.Restored, history });
  }
  async function restore() {
    if (recovering.current || busy.current || restoreCooldownUntil.current > Date.now()) return;
    recovering.current = true; dispatch({ type: ChatSessionActionType.RestoreStarted });
    try { await loadHistory(); }
    catch (failure) {
      if (!mounted.current) return;
      if (failure instanceof ChatApiError && failure.retryAfter > 0) { restoreCooldownUntil.current = Date.now() + failure.retryAfter * 1000; setRestoreCooldown(failure.retryAfter); }
      dispatch({ type: ChatSessionActionType.RestoreFailed, message: failure instanceof Error ? failure.message : 'History could not be restored.' });
    } finally { recovering.current = false; }
  }
  function setDraft(value: string) { setDraftValue(value); setInputError(undefined); }
  async function execute(request: StoneChatMessageRequest) {
    busy.current = true; const identity = ++attempt.current; const controller = new AbortController(); activeTransport.current = controller;
    dispatch({ type: ChatSessionActionType.Begin, request }); let completed = false;
    try {
      await sendChatMessageStream(request, event => {
        if (!mounted.current || identity !== attempt.current || completed) return;
        if (event.data.conversationId.toLowerCase() !== request.conversationId.toLowerCase() || event.data.turnId.toLowerCase() !== request.turnId.toLowerCase()) throw new ChatApiError(0, 'CHAT_STREAM_PROTOCOL_ERROR', 'Chat returned another turn. Restore history manually.');
        if (event.event === ChatStreamEventType.TextDelta) dispatch({ type: ChatSessionActionType.Delta, turnId: request.turnId, text: event.data.text });
        if (event.event === ChatStreamEventType.Completed) { completed = true; dispatch({ type: ChatSessionActionType.Completed, turnId: request.turnId, answer: { text: event.data.text, stones: event.data.stones } }); }
      }, controller.signal);
      if (!mounted.current || identity !== attempt.current) return;
      if (!completed) throw new ChatApiError(0, 'CHAT_STREAM_PROTOCOL_ERROR', 'Chat delivery ended without completion. Restore history manually.');
      await loadHistory();
    } catch (failure) {
      if (!mounted.current || identity !== attempt.current) return;
      if (failure instanceof ChatApiError && failure.retryAfter > 0) {
        if (completed) { restoreCooldownUntil.current = Date.now() + failure.retryAfter * 1000; setRestoreCooldown(failure.retryAfter); }
        else { cooldownUntil.current = Date.now() + failure.retryAfter * 1000; setCooldown(failure.retryAfter); }
      }
      const recovery = completed || !(failure instanceof ChatApiError) || failure.status === 0 || failure.code === 'CHAT_UNAVAILABLE' || failure.status >= 500 && !healthyRejections.has(failure.code);
      dispatch({ type: ChatSessionActionType.Failed, recovery, message: failure instanceof Error && failure.message.trim() ? failure.message : 'The message could not be sent. Please try again.' });
    } finally { if (identity === attempt.current) { busy.current = false; activeTransport.current = null; attempt.current++; } }
  }
  function send(message = draft) {
    if (busy.current || recovering.current || current.current.failedRequest || current.current.phase !== ChatStreamPhase.Idle || cooldownUntil.current > Date.now()) return;
    if (!message.trim() || message.length > 2000) { setInputError(message.trim() ? 'Use at most 2000 characters.' : 'Enter a message.'); return; }
    const pageRoute = resolvePageRoute(window.location.pathname);
    const request = Object.freeze({ conversationId, turnId: crypto.randomUUID(), message, context: Object.freeze({ stoneId: pageRoute.page === 'stone-details' ? pageRoute.id ?? null : null }) });
    setDraftValue(''); setInputError(undefined); void execute(request);
  }
  function retry() {
    if (busy.current || recovering.current || !current.current.failedRequest || current.current.phase !== ChatStreamPhase.Idle || cooldownUntil.current > Date.now()) return;
    void execute(current.current.failedRequest);
  }
  const pending = state.phase === ChatStreamPhase.Waiting || state.phase === ChatStreamPhase.Receiving || state.phase === ChatStreamPhase.Synchronizing;
  const restoring = state.phase === ChatStreamPhase.Restoring;
  return { conversationId, messages: state.messages, phase: state.phase, announcement: state.announcement, isMock: import.meta.env.MODE === 'mock', open, setOpen, draft, setDraft, pending,
    error: state.error, inputError, restoring, restoreError: state.recoveryError, unavailable: state.phase === ChatStreamPhase.RecoveryRequired, cooldown, restoreCooldown, canRetry: Boolean(state.failedRequest) && !state.confirmedRequest,
    restore, send, retry };
}
const ChatSessionContext = createContext<ReturnType<typeof useChatSessionState> | null>(null);
export function ChatSessionProvider({ children }: { children: ReactNode }) { return <ChatSessionContext.Provider value={useChatSessionState()}>{children}</ChatSessionContext.Provider>; }
export function useChatSession() { const session = useContext(ChatSessionContext); if (!session) throw new Error('Chat must be inside ChatSessionProvider.'); return session; }
