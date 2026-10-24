import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from '../../../../App/App';
import { stubChatStream } from '../../state/ChatSessionProvider/chatStreamTestSupport';
import * as chatbotApi from '../../../../api/chatbotApi';
import type { StoneChatMessageResponse } from '../../../../api/dto/StoneChatMessageResponse';

beforeEach(() => {
  sessionStorage.clear();
  window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

function navigate(path: string) {
  window.history.pushState(null, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}
async function mountOpen() {
  await act(async () => { render(<App />); });
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
}

test('chat links open mock stone details and Back/Forward preserve conversation, draft and open state', async () => {
  const send = vi.spyOn(chatbotApi, 'sendChatMessageStream');
  await mountOpen();
  expect(document.querySelector('.page-layout.chat-visible')).toBeTruthy();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Help me choose a stone' })); });
  const uuid = send.mock.calls[0][0].conversationId;
  await waitFor(() => expect(within(screen.getByRole('log')).getByRole('link', { name: 'Luna' })).toBeTruthy());
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'What about this one?' } });
  await act(async () => { fireEvent.click(within(screen.getByRole('log')).getByRole('link', { name: 'Luna' })); });
  expect(window.location.pathname).toBe('/stone-shelter/stones/3');
  expect(within(screen.getByRole('main')).getByRole('heading', { level: 1, name: 'Luna' })).toBeTruthy();
  expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('What about this one?');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Send message' })); });
  expect(send.mock.calls[1][0]).toEqual({ conversationId: uuid, turnId: expect.any(String), message: 'What about this one?', context: { stoneId: 3 } });
  window.history.back();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy());
  expect(screen.getByRole('button', { name: 'Collapse chat' })).toBeTruthy();
  window.history.forward();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Luna' })).toBeTruthy());
  expect(within(screen.getByRole('log')).getByText('What about this one?')).toBeTruthy();
  expect(send).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole('button', { name: 'Close chat' }));
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(document.querySelector('.page-layout.chat-visible')).toBeNull();
});

test('a pending request settles on a hidden route without resend and returns with its result', async () => {
  let resolve!: (response: StoneChatMessageResponse) => void;
  const send = stubChatStream(() => new Promise(accept => { resolve = accept; }));
  window.history.replaceState(null, '', '/stone-shelter/stones/1');
  await mountOpen();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'About Mars' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send message' }));
  expect(screen.getByRole('status').textContent).toContain('Waiting');
  fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
  await act(async () => { navigate('/stone-shelter/add-stone'); });
  expect(screen.queryByRole('button', { name: 'Collapse chat' })).toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  await act(async () => { resolve({ text: 'Result while hidden', stones: [{ id: 1, name: 'Mars' }] }); });
  await act(async () => { navigate('/stone-shelter/catalog'); });
  expect(within(screen.getByRole('log')).getByText('Result while hidden')).toBeTruthy();
  expect(send).toHaveBeenCalledTimes(1);
  expect(send.mock.calls[0][0].context).toEqual({ stoneId: 1 });
});

test('collapse and resize preserve a pending reply and the new draft until reopening', async () => {
  let resolve!: (response: StoneChatMessageResponse) => void;
  const send = stubChatStream(() => new Promise(accept => { resolve = accept; }));
  await mountOpen();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Choose' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send message' }));
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Draft while waiting' } });
  fireEvent.click(screen.getByRole('button', { name: 'Collapse chat' }));
  vi.stubGlobal('innerWidth', 375);
  fireEvent(window, new Event('resize'));
  await act(async () => { resolve({ text: 'Reply while collapsed', stones: [] }); });
  expect(send).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('region', { name: 'Stone Shelter Chat' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('Draft while waiting');
  expect(within(screen.getByRole('log')).getByText('Reply while collapsed')).toBeTruthy();
});

test('adoption modal takes priority over an expanded chat and closing it retains the conversation', async () => {
  window.history.replaceState(null, '', '/stone-shelter/stones/1');
  await mountOpen();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Unsaved chat draft' } });
  fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' }));
  expect(screen.getByRole('dialog', { name: 'Adopt this stone' })).toBeTruthy();
  expect((document.querySelector('.stone-chatbot')?.parentElement as HTMLElement).inert).toBe(true);
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.getByRole('button', { name: 'Collapse chat' })).toBeTruthy();
  expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('Unsaved chat draft');
});

test('filter modal keeps chat inert and Escape closes the modal without collapsing chat', async () => {
  vi.stubGlobal('innerWidth', 768);
  await mountOpen();
  fireEvent.click(screen.getByRole('button', { name: 'Open filters' }));
  expect(screen.getByRole('dialog', { name: 'Filters' })).toBeTruthy();
  expect((document.querySelector('.stone-chatbot') as HTMLElement).inert).toBe(true);
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.getByRole('button', { name: 'Collapse chat' }).getAttribute('aria-expanded')).toBe('true');
});

test('missing stub references retain the existing not-found route without clearing chat', async () => {
  stubChatStream(async () => ({ text: 'Demo', stones: [{ id: 999, name: 'Missing demo stone' }] }));
  await mountOpen();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Another topic' })); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Missing demo stone' })); });
  expect(screen.getByRole('heading', { name: 'Stone not found' })).toBeTruthy();
  expect(within(screen.getByRole('log')).getByText('Demo')).toBeTruthy();
});
