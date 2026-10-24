import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { stubChatStream } from '../../state/ChatSessionProvider/chatStreamTestSupport';
import { ChatStreamEventType } from '../../../../enums/ChatStreamEventType';
import * as chatbotApi from '../../../../api/chatbotApi';
import { ChatSessionProvider } from '../../state/ChatSessionProvider/ChatSessionProvider';
import { StoneChatbot } from './StoneChatbot';

beforeEach(() => { sessionStorage.clear(); window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(chatbotApi, 'getChatHistory').mockImplementation(async id => ({ conversationId: id, messages: [] }));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

async function openChat() {
  await act(async () => { render(<ChatSessionProvider><StoneChatbot visible /></ChatSessionProvider>); });
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  return screen.getByRole('region', { name: 'Stone Shelter Chat' });
}

test('starts collapsed, opens without a request and preserves draft through launcher/close/Escape', async () => {
  const send = vi.spyOn(chatbotApi, 'sendChatMessageStream');
  const panel = await openChat();
  expect(send).not.toHaveBeenCalled();
  const input = screen.getByRole('textbox', { name: 'Message' });
  expect(document.activeElement).toBe(input);
  fireEvent.change(input, { target: { value: 'My draft' } });
  fireEvent.click(screen.getByRole('button', { name: 'Close chat' }));
  expect(screen.queryByRole('region', { name: 'Stone Shelter Chat' })).toBeNull();
  expect(panel.hidden).toBe(true);
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Open chat' }));
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  expect((input as HTMLInputElement).value).toBe('My draft');
  fireEvent.keyDown(input, { key: 'Escape' });
  expect(screen.getByRole('button', { name: 'Open chat' }).getAttribute('aria-expanded')).toBe('false');
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  fireEvent.click(screen.getByRole('button', { name: 'Collapse chat' }));
  expect(screen.queryByRole('textbox')).toBeNull();
});

test.each(['Help me choose a stone', 'Tell me about stone properties', 'How do I care for a stone?', 'Another topic'])('quick prompt sends through the normal boundary: %s', async prompt => {
  const send = stubChatStream(async () => ({ text: 'Demo reply', stones: [] }));
  const panel = await openChat();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: prompt })); });
  expect(send).toHaveBeenCalledTimes(1);
  expect(send.mock.calls[0][0]).toMatchObject({ message: prompt, context: { stoneId: null } });
  expect(within(panel).queryByRole('button', { name: 'Another topic' })).toBeNull();
  expect(within(panel).getByRole('log').textContent).toContain('Demo reply');
});

test('composer guards IME, blank/oversized input and renders plain text with ordered canonical links', async () => {
  const send = stubChatStream(async () => ({
    text: '<img src=x onerror=alert(1)>', stones: [{ id: 3, name: 'Luna' }, { id: 1, name: 'Mars' }],
  }));
  const panel = await openChat();
  const input = screen.getByRole('textbox', { name: 'Message' });
  expect((screen.getByRole('button', { name: 'Send message' }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.keyDown(input, { key: 'Enter' });
  expect(screen.getByRole('alert').textContent).toBe('Enter a message.');
  fireEvent.change(input, { target: { value: 'x'.repeat(2001) } });
  fireEvent.keyDown(input, { key: 'Enter' });
  expect(screen.getByRole('alert').textContent).toBe('Use at most 2000 characters.');
  fireEvent.change(input, { target: { value: '  Choose  ' } });
  fireEvent.keyDown(input, { key: 'Enter', isComposing: true });
  expect(send).not.toHaveBeenCalled();
  await act(async () => { fireEvent.keyDown(input, { key: 'Enter' }); });
  expect(send.mock.calls[0][0].message).toBe('  Choose  ');
  expect((input as HTMLInputElement).value).toBe('');
  const log = within(panel).getByRole('log');
  expect(within(log).getByText('<img src=x onerror=alert(1)>')).toBeTruthy();
  expect(log.querySelector('img')).toBeNull();
  expect(within(log).getAllByRole('link').map(link => link.getAttribute('href')))
    .toEqual(['/stone-shelter/stones/3', '/stone-shelter/stones/1']);
});

test('send button exposes failure and retries the original turn without duplicating the user bubble', async () => {
  const send = stubChatStream(vi.fn().mockRejectedValueOnce(new chatbotApi.ChatApiError(400, 'HTTP_400', 'Backend unavailable'))
    .mockResolvedValueOnce({ text: 'Recovered', stones: [] }));
  const panel = await openChat();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'First question' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Send message' })); });
  expect(screen.getByRole('alert').textContent).toBe('Backend unavailable');
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Next question' } });
  expect((screen.getByRole('button', { name: 'Send message' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry message' })); });
  await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  expect(send.mock.calls[1][0]).toEqual(send.mock.calls[0][0]);
  expect(within(within(panel).getByRole('log')).getAllByText('First question')).toHaveLength(1);
  expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('Next question');
});

test('fragments stay in one plain-text bubble without links or repeated live announcements', async () => {
  let fragment!: (text: string) => void;
  let interrupt!: (failure: Error) => void;
  vi.spyOn(chatbotApi, 'sendChatMessageStream').mockImplementation((request, onEvent) => {
    let sequence = 1;
    onEvent({ event: ChatStreamEventType.Started, data: { conversationId: request.conversationId, turnId: request.turnId, sequence } });
    fragment = text => onEvent({ event: ChatStreamEventType.TextDelta, data: { conversationId: request.conversationId, turnId: request.turnId, sequence: ++sequence, text } });
    return new Promise((_, reject) => { interrupt = reject; });
  });
  const panel = await openChat(); fireEvent.change(screen.getByRole('textbox'), { target: { value: 'question' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send message' }));
  const status = panel.querySelector('[aria-live="polite"]')!; const waiting = status.textContent;
  act(() => { fragment('  <b>🪨'); fragment('\n literal</b>  '); });
  const log = within(panel).getByRole('log');
  expect(log.querySelectorAll('.chat-assistant')).toHaveLength(1); expect(log.querySelector('b')).toBeNull();
  expect(within(log).queryAllByRole('link')).toHaveLength(0); expect(status.textContent).toBe(waiting);
  await act(async () => { interrupt(new Error('Lost delivery')); });
  expect(within(log).getByText('Incomplete reply. Restore history to check the saved result.')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Restore history' })).toBeTruthy();
});

test('new content preserves older-message scroll position and follows only near the bottom', async () => {
  const send = stubChatStream(async () => ({ text: 'Saved answer', stones: [] }));
  const panel = await openChat(); const log = within(panel).getByRole('log');
  Object.defineProperties(log, { scrollHeight: { configurable: true, value: 1000 }, clientHeight: { configurable: true, value: 200 } });
  log.scrollTop = 100; fireEvent.scroll(log);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Another topic' })); });
  expect(log.scrollTop).toBe(100); expect(send).toHaveBeenCalledTimes(1);
  log.scrollTop = 750; fireEvent.scroll(log);
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'next' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Send message' })); });
  expect(log.scrollTop).toBe(1000);
});
