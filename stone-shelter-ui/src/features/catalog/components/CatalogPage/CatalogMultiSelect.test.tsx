import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from '../../../../App/App';
import * as stonesApi from '../../../../api/stonesApi';

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

async function catalog() {
  window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  await act(async () => { render(<App />); });
}

test('dropdown search is local, preserves pagination and sends only selected filter values', async () => {
  const search = vi.spyOn(stonesApi, 'getCatalogStones');
  await catalog();
  expect(screen.getByRole('button', { name: 'Size 0' }).getAttribute('aria-expanded')).toBe('false');
  expect(screen.getByRole('button', { name: 'Stone type 0' }).getAttribute('aria-expanded')).toBe('false');
  fireEvent.click(screen.getByRole('button', { name: 'Stone type 0' }));
  expect(screen.getAllByRole('checkbox')).toHaveLength(10);
  fireEvent.click(screen.getByRole('button', { name: 'Size 0' }));
  expect(screen.getAllByRole('checkbox')).toHaveLength(3);
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  expect(search).toHaveBeenLastCalledWith(0, 8, { field: 'admissionDate', direction: 'desc' }, { stoneSizes: ['SMALL'] });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  fireEvent.click(screen.getByRole('button', { name: 'Size 1' }));
  const count = search.mock.calls.length;
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: '  MED  ' } });
  expect(screen.queryByRole('checkbox', { name: 'Small' })).toBeNull();
  expect(screen.getByRole('checkbox', { name: 'Medium' })).toBeTruthy();
  expect(search).toHaveBeenCalledTimes(count);
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  fireEvent.keyDown(screen.getByRole('searchbox'), { key: 'Escape' });
  expect(search).toHaveBeenCalledTimes(count);
  fireEvent.click(screen.getByRole('button', { name: 'Size 1' }));
  expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
  expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
});

test('reset keeps the dropdown open but sidebar closure discards temporary search', async () => {
  await catalog();
  fireEvent.click(screen.getByRole('button', { name: 'Size 0' }));
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'small' } });
  const reset = screen.getByRole('button', { name: 'Reset filters' });
  act(() => reset.focus());
  await act(async () => { fireEvent.click(reset); });
  expect(screen.getByRole('button', { name: 'Size 0' }).getAttribute('aria-expanded')).toBe('true');
  expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'large' } });
  fireEvent.click(screen.getByRole('button', { name: 'Close filters' }));
  fireEvent.click(screen.getByRole('button', { name: 'Open filters' }));
  expect(screen.getByRole('button', { name: 'Size 0' }).getAttribute('aria-expanded')).toBe('false');
  fireEvent.click(screen.getByRole('button', { name: 'Size 0' }));
  expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
});

test('mobile Escape closes dropdown first, then sidebar, while retaining the expanded chat', async () => {
  vi.stubGlobal('innerWidth', 390);
  await catalog();
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  fireEvent.click(screen.getByRole('button', { name: 'Open filters' }));
  const first = screen.getByRole('button', { name: 'Close filters' });
  const last = screen.getByRole('button', { name: 'Reset filters' });
  fireEvent.click(screen.getByRole('button', { name: 'Stone type 0' }));
  fireEvent.keyDown(screen.getByRole('searchbox'), { key: 'Escape' });
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Stone type 0' }));
  expect(screen.getByRole('dialog', { name: 'Filters' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Collapse chat' }).getAttribute('aria-expanded')).toBe('true');
  act(() => last.focus());
  fireEvent.keyDown(last, { key: 'Tab' });
  expect(document.activeElement).toBe(first);
  fireEvent.keyDown(first, { key: 'Tab', shiftKey: true });
  expect(document.activeElement).toBe(last);
  fireEvent.keyDown(last, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Open filters' }));
  expect(screen.getByRole('button', { name: 'Collapse chat' })).toBeTruthy();
  expect(document.body.style.overflow).toBe('');
});

test('desktop dropdown Escape does not collapse the expanded chat', async () => {
  await catalog();
  fireEvent.click(screen.getByRole('button', { name: 'Open chat' }));
  fireEvent.click(screen.getByRole('button', { name: 'Size 0' }));
  fireEvent.keyDown(screen.getByRole('searchbox'), { key: 'Escape' });
  expect(screen.getByRole('button', { name: 'Collapse chat' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Size 0' }).getAttribute('aria-expanded')).toBe('false');
});
