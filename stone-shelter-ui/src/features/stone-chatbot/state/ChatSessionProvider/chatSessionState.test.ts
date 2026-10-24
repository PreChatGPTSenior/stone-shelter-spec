import { expect, test } from 'vitest';
import { chatSessionReducer, initialChatSessionState, historyMatchesConfirmed } from './chatSessionState';
import { ChatSessionActionType as Action } from '../../../../enums/ChatSessionActionType';
import { ChatStreamPhase } from '../../../../enums/ChatStreamPhase';
import { ChatMessageDeliveryState } from '../../../../enums/ChatMessageDeliveryState';
import type { StoneChatHistoryResponse } from '../../../../api/dto/StoneChatHistoryResponse';
const request = { conversationId: '9960a79a-ae24-4c89-9f54-51103bcb00c9', turnId: '8103df2b-dad2-4f4c-9701-5e88739a2dda', message: 'Original.', context: { stoneId: 3 } };
const answer = { text: ' 🪨\n<&> ', stones: [{ id: 7, name: 'Fixture stone' }] };
const history: StoneChatHistoryResponse = { conversationId: request.conversationId, messages: [
  { role: 'USER', turnId: request.turnId, text: request.message, context: request.context, stones: [] },
  { role: 'ASSISTANT', turnId: request.turnId, ...answer, context: null },
] };
test('fragments keep one unlinked bubble and completed finalizes the same pair once', () => {
  let state = chatSessionReducer(initialChatSessionState(), { type: Action.Begin, request }); const waiting = state.announcement;
  state = chatSessionReducer(state, { type: Action.Delta, turnId: request.turnId, text: ' 🪨' });
  state = chatSessionReducer(state, { type: Action.Delta, turnId: request.turnId, text: '\n<&> ' });
  expect(state.messages).toHaveLength(2); expect(state.messages[1]).toMatchObject({ text: answer.text, stones: [], delivery: ChatMessageDeliveryState.Streaming }); expect(state.announcement).toBe(waiting);
  state = chatSessionReducer(state, { type: Action.Completed, turnId: request.turnId, answer }); expect(state.phase).toBe(ChatStreamPhase.Synchronizing); expect(state.messages[1]).toMatchObject({ ...answer, delivery: ChatMessageDeliveryState.Confirmed });
  const announced = state.announcement; state = chatSessionReducer(state, { type: Action.Restored, history }); expect(state.messages).toHaveLength(2); expect(state.phase).toBe(ChatStreamPhase.Idle); expect(state.announcement).toBe(announced);
});
test('interruption stays ephemeral incomplete and manual history resolves by identity without duplicate user', () => {
  let state = chatSessionReducer(initialChatSessionState(), { type: Action.Begin, request }); state = chatSessionReducer(state, { type: Action.Delta, turnId: request.turnId, text: 'partial' });
  state = chatSessionReducer(state, { type: Action.Failed, message: 'Disconnected', recovery: true }); expect(state.messages[1]).toMatchObject({ text: 'partial', stones: [], delivery: ChatMessageDeliveryState.Incomplete });
  state = chatSessionReducer(state, { type: Action.Restored, history: { conversationId: request.conversationId, messages: [] } }); expect(state.failedRequest).toEqual(request); expect(state.messages).toHaveLength(2);
  state = chatSessionReducer(state, { type: Action.Restored, history }); expect(state.messages).toHaveLength(2); expect(state.messages[1]).toMatchObject(answer); expect(state.failedRequest).toBeUndefined();
});
test('failed or inconsistent completion synchronization preserves confirmed answer and disables resend', () => {
  let state = chatSessionReducer(initialChatSessionState(), { type: Action.Begin, request }); state = chatSessionReducer(state, { type: Action.Completed, turnId: request.turnId, answer });
  state = chatSessionReducer(state, { type: Action.Failed, message: 'Sync failed', recovery: true }); expect(state.error).toBeUndefined(); expect(state.failedRequest).toBeUndefined(); expect(state.messages[1]).toMatchObject({ ...answer, delivery: ChatMessageDeliveryState.Confirmed });
  expect(historyMatchesConfirmed(state, { conversationId: request.conversationId, messages: [] })).toBe(false);
  state = chatSessionReducer(state, { type: Action.Restored, history: { conversationId: request.conversationId, messages: [] } }); expect(state.phase).toBe(ChatStreamPhase.RecoveryRequired); expect(state.messages[1]).toMatchObject(answer);
  state = chatSessionReducer(state, { type: Action.Restored, history }); expect(state.phase).toBe(ChatStreamPhase.Idle);
});
test('retry resets stream accumulation without duplicating the user or repeating waiting announcement', () => {
  let state = chatSessionReducer(initialChatSessionState(), { type: Action.Begin, request }); state = chatSessionReducer(state, { type: Action.Delta, turnId: request.turnId, text: 'old partial' });
  state = chatSessionReducer(state, { type: Action.Failed, message: 'Retry', recovery: false }); state = chatSessionReducer(state, { type: Action.Begin, request }); expect(state.announcement).toBe('');
  state = chatSessionReducer(state, { type: Action.Delta, turnId: request.turnId, text: 'new' }); expect(state.messages).toHaveLength(2); expect(state.messages[1].text).toBe('new');
});
