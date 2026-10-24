import { ChatSessionActionType } from '../../../../enums/ChatSessionActionType';
import { ChatMessageDeliveryState } from '../../../../enums/ChatMessageDeliveryState';
import { ChatStreamPhase } from '../../../../enums/ChatStreamPhase';
import type { ChatMessageRole } from '../../../../enums/ChatMessageRole';
import type { StoneChatMessageRequest } from '../../../../api/dto/StoneChatMessageRequest';
import type { StoneChatMessageResponse } from '../../../../api/dto/StoneChatMessageResponse';
import type { StoneChatHistoryResponse } from '../../../../api/dto/StoneChatHistoryResponse';

export interface ChatMessage extends StoneChatMessageResponse {
  role: ChatMessageRole;
  turnId: string;
  delivery: ChatMessageDeliveryState;
}
export interface ChatSessionState {
  messages: ChatMessage[];
  phase: ChatStreamPhase;
  request?: StoneChatMessageRequest;
  failedRequest?: StoneChatMessageRequest;
  confirmedRequest?: StoneChatMessageRequest;
  streamText: string;
  error?: string;
  recoveryError?: string;
  announcement: string;
  announcedTurns: string[];
  waitingTurns: string[];
}
export type ChatSessionAction =
  | { type: ChatSessionActionType.Begin; request: StoneChatMessageRequest }
  | { type: ChatSessionActionType.Delta; turnId: string; text: string }
  | { type: ChatSessionActionType.Completed; turnId: string; answer: StoneChatMessageResponse }
  | { type: ChatSessionActionType.Failed; message: string; recovery: boolean }
  | { type: ChatSessionActionType.RestoreStarted }
  | { type: ChatSessionActionType.Restored; history: StoneChatHistoryResponse }
  | { type: ChatSessionActionType.RestoreFailed; message: string };
export function initialChatSessionState(): ChatSessionState {
  return { messages: [], phase: ChatStreamPhase.Restoring, streamText: '', announcement: '', announcedTurns: [], waitingTurns: [] };
}
function put(messages: ChatMessage[], message: ChatMessage): ChatMessage[] {
  const found = messages.findIndex(value => value.turnId === message.turnId && value.role === message.role);
  return found < 0 ? [...messages, message] : messages.map((value, index) => index === found ? message : value);
}
export function historyMatchesConfirmed(state: ChatSessionState, history: StoneChatHistoryResponse): boolean {
  const request = state.confirmedRequest;
  if (!request) return true;
  const user = history.messages.find(message => message.turnId === request.turnId && message.role === 'USER');
  const assistant = history.messages.find(message => message.turnId === request.turnId && message.role === 'ASSISTANT');
  const confirmed = state.messages.find(message => message.turnId === request.turnId && message.role === 'ASSISTANT');
  return Boolean(user && assistant && confirmed && user.text === request.message
    && (user.context == null) === (request.context == null) && user.context?.stoneId === request.context?.stoneId
    && assistant.text === confirmed.text && JSON.stringify(assistant.stones) === JSON.stringify(confirmed.stones));
}
export function chatSessionReducer(state: ChatSessionState, action: ChatSessionAction): ChatSessionState {
  switch (action.type) {
    case ChatSessionActionType.Begin:
      return { ...state, phase: ChatStreamPhase.Waiting, request: action.request, confirmedRequest: undefined, streamText: '', error: undefined, recoveryError: undefined,
        announcement: state.waitingTurns.includes(action.request.turnId) ? '' : 'Waiting for a reply…',
        waitingTurns: state.waitingTurns.includes(action.request.turnId) ? state.waitingTurns : [...state.waitingTurns, action.request.turnId], messages: put(state.messages, { role: 'USER', turnId: action.request.turnId, text: action.request.message, stones: [], delivery: ChatMessageDeliveryState.Pending }) };
    case ChatSessionActionType.Delta: {
      if (state.request?.turnId !== action.turnId) return state;
      const text = state.streamText + action.text;
      return { ...state, phase: ChatStreamPhase.Receiving, streamText: text,
        messages: put(state.messages, { role: 'ASSISTANT', turnId: action.turnId, text, stones: [], delivery: ChatMessageDeliveryState.Streaming }) };
    }
    case ChatSessionActionType.Completed: {
      if (state.request?.turnId !== action.turnId) return state;
      const fresh = !state.announcedTurns.includes(action.turnId);
      return { ...state, phase: ChatStreamPhase.Synchronizing, failedRequest: undefined, confirmedRequest: state.request,
        error: undefined, recoveryError: undefined, streamText: action.answer.text,
        messages: put(state.messages.map(message => message.turnId === action.turnId ? { ...message, delivery: ChatMessageDeliveryState.Confirmed } : message),
          { role: 'ASSISTANT', turnId: action.turnId, ...action.answer, delivery: ChatMessageDeliveryState.Confirmed }),
        announcement: fresh ? `${action.answer.text}${action.answer.stones.length ? ` ${action.answer.stones.length} stone links available.` : ''}` : '',
        announcedTurns: fresh ? [...state.announcedTurns, action.turnId] : state.announcedTurns };
    }
    case ChatSessionActionType.Failed:
      if (state.confirmedRequest) return { ...state, phase: ChatStreamPhase.RecoveryRequired, error: undefined, failedRequest: undefined, recoveryError: action.message };
      return { ...state, phase: action.recovery ? ChatStreamPhase.RecoveryRequired : ChatStreamPhase.Idle, error: action.message,
        failedRequest: state.request, messages: state.messages.map(message => message.turnId === state.request?.turnId && message.role === 'ASSISTANT' ? { ...message, stones: [], delivery: ChatMessageDeliveryState.Incomplete } : message) };
    case ChatSessionActionType.RestoreStarted:
      return { ...state, phase: ChatStreamPhase.Restoring, recoveryError: undefined };
    case ChatSessionActionType.RestoreFailed:
      return { ...state, phase: ChatStreamPhase.RecoveryRequired, recoveryError: action.message };
    case ChatSessionActionType.Restored: {
      if (!historyMatchesConfirmed(state, action.history)) return { ...state, phase: ChatStreamPhase.RecoveryRequired, recoveryError: 'Saved history does not match the confirmed answer. Restore history manually.' };
      let messages: ChatMessage[] = action.history.messages.map(message => ({ role: message.role, turnId: message.turnId, text: message.text, stones: message.stones, delivery: ChatMessageDeliveryState.Confirmed }));
      const savedUser = action.history.messages.find(message => message.role === 'USER' && message.turnId === state.failedRequest?.turnId);
      const savedAssistant = action.history.messages.find(message => message.role === 'ASSISTANT' && message.turnId === state.failedRequest?.turnId);
      if (state.failedRequest && (savedUser || savedAssistant) && (!savedUser || !savedAssistant || savedUser.text !== state.failedRequest.message
        || (savedUser.context == null) !== (state.failedRequest.context == null) || savedUser.context?.stoneId !== state.failedRequest.context?.stoneId)) {
        return { ...state, phase: ChatStreamPhase.RecoveryRequired, recoveryError: 'Saved history does not match the original request. Restore history manually.' };
      }
      const recovered = Boolean(state.failedRequest && savedUser && savedAssistant);
      if (state.failedRequest && !recovered) for (const message of state.messages.filter(message => message.turnId === state.failedRequest?.turnId)) messages = put(messages, message);
      return { ...state, messages, phase: ChatStreamPhase.Idle, confirmedRequest: undefined, failedRequest: recovered ? undefined : state.failedRequest,
        error: recovered || !state.failedRequest ? undefined : state.error, recoveryError: undefined };
    }
  }
}
