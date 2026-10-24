import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from '../../../../App/App';
import * as stonesApi from '../../../../api/stonesApi';
import { ApiError } from '../../../../api/ApiError';
import type { StonesSearchResponse } from '../../../../api/dto/StonesSearchResponse';

afterEach(() => {
  cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.stubEnv('MODE', 'mock');
});

async function catalog() {
  window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  await act(async () => { render(<App />); });
}

async function select(group: string, option: string) {
  const trigger = screen.getByRole('button', { name: new RegExp(`^${group} \\d+$`) });
  if (trigger.getAttribute('aria-expanded') === 'false') fireEvent.click(trigger);
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: option })); });
}

function labels() {
  return within(screen.getByRole('region', { name: 'Active filters' })).getAllByRole('listitem')
    .map(item => item.querySelector('span')?.textContent);
}

test('labels synchronize hidden search options and remain removable with sidebar closed', async () => {
  await catalog();
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
  await select('Size', 'Medium');
  await select('Size', 'Small');
  await select('Stone type', 'Granite');
  await select('Stone type', 'Basalt');
  expect(labels()).toEqual(['Size: Small', 'Size: Medium', 'Stone type: Basalt', 'Stone type: Granite']);
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'Bas' } });
  expect(screen.queryByRole('checkbox', { name: 'Granite' })).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove stone type Granite' })); });
  expect(labels()).toEqual(['Size: Small', 'Size: Medium', 'Stone type: Basalt']);
  fireEvent.click(screen.getByRole('button', { name: 'Stone type 1' }));
  expect((screen.getByRole('checkbox', { name: 'Granite' }) as HTMLInputElement).checked).toBe(false);
  fireEvent.click(screen.getByRole('button', { name: 'Close filters' }));
  expect(screen.getByLabelText('2 active filter groups').textContent).toBe('2');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove size Medium' })); });
  expect(screen.getByLabelText('2 active filter groups').textContent).toBe('2');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove size Small' })); });
  expect(screen.getByLabelText('1 active filter groups').textContent).toBe('1');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove stone type Basalt' })); });
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
  expect(screen.getByLabelText('0 active filter groups').textContent).toBe('0');
});

test('labels represent last valid dates and date removal clears invalid drafts without clearing sizes', async () => {
  const search = vi.spyOn(stonesApi, 'getCatalogStones');
  await catalog();
  await select('Size', 'Small');
  await act(async () => { fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } }); });
  expect(labels()).toEqual(['Size: Small', 'Admission date: from 2026-09-01']);
  await act(async () => { fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-10' } }); });
  const count = search.mock.calls.length;
  fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-08-01' } });
  expect(search).toHaveBeenCalledTimes(count);
  expect(labels()).toEqual(['Size: Small', 'Admission date: 2026-09-01 to 2026-09-10']);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove size Small' })); });
  expect(screen.getByRole('alert').textContent).toContain('From must be on or before To.');
  expect((screen.getByLabelText('To') as HTMLInputElement).value).toBe('2026-08-01');
  expect(labels()).toEqual(['Admission date: 2026-09-01 to 2026-09-10']);
  await select('Size', 'Medium');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove admission date filter' })); });
  expect(labels()).toEqual(['Size: Medium']);
  expect((screen.getByLabelText('From') as HTMLInputElement).value).toBe('');
  expect((screen.getByLabelText('To') as HTMLInputElement).value).toBe('');
  expect(screen.queryByRole('alert')).toBeNull();
  expect(search.mock.calls.at(-1)?.[3]).toEqual({ stoneSizes: ['MEDIUM'], admissionDateFrom: undefined, admissionDateTo: undefined });
});

test('removal resets page but preserves sorting and page size; reset clears the label region', async () => {
  const search = vi.spyOn(stonesApi, 'getCatalogStones');
  await catalog();
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  await select('Size', 'Small');
  await select('Size', 'Medium');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  const remove = screen.getByRole('button', { name: 'Remove size Small' });
  act(() => remove.focus());
  await act(async () => { fireEvent.click(remove); });
  expect(search).toHaveBeenLastCalledWith(0, 12, { field: 'admissionDate', direction: 'asc' }, { stoneSizes: ['MEDIUM'] });
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Remove size Medium' }));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reset filters' })); });
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  expect((screen.getByLabelText('Stones per page') as HTMLSelectElement).value).toBe('12');
});

test('labels survive pending, failed and successful empty reads and obsolete responses', async () => {
  let resolve!: (response: StonesSearchResponse) => void;
  let reject!: (error: Error) => void;
  const search = vi.spyOn(stonesApi, 'getCatalogStones');
  await catalog();
  search.mockReturnValueOnce(new Promise((done, fail) => { resolve = done; reject = fail; }));
  await select('Size', 'Small');
  expect(screen.getByText('Loading stones…')).toBeTruthy();
  expect(labels()).toEqual(['Size: Small']);
  await act(async () => { reject(new ApiError(503, 'Unavailable.')); });
  expect(screen.getByRole('alert').textContent).toBe('Unavailable.');
  expect(labels()).toEqual(['Size: Small']);
  search.mockResolvedValueOnce({ content: [], page: 0, size: 8, totalElements: 0 });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry catalog' })); });
  expect(screen.getByText(/No stones match/)).toBeTruthy();
  expect(labels()).toEqual(['Size: Small']);
  search.mockReturnValueOnce(new Promise(done => { resolve = done; }));
  await select('Size', 'Medium');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove size Small' })); });
  expect(labels()).toEqual(['Size: Medium']);
  await act(async () => { resolve({ content: [], page: 0, size: 8, totalElements: 0 }); });
  expect(labels()).toEqual(['Size: Medium']);
  expect(screen.getAllByRole('article')).toHaveLength(8);
});

test('Back and Forward preserve labels and session choices while dropping dropdown queries', async () => {
  await catalog();
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  await select('Size', 'Small');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  fireEvent.click(screen.getByRole('button', { name: 'Size 1' }));
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'medium' } });
  await act(async () => { fireEvent.click(within(screen.getByRole('main')).getAllByRole('link', { name: /View details for/ })[0]); });
  window.history.back();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy());
  expect(labels()).toEqual(['Size: Small']);
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  expect(screen.getByRole('button', { name: 'Size 1' }).getAttribute('aria-expanded')).toBe('false');
  fireEvent.click(screen.getByRole('button', { name: 'Size 1' }));
  expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
  window.history.forward();
  await waitFor(() => expect(screen.queryByRole('main', { name: 'Stone catalog' })).toBeNull());
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(labels()).toEqual(['Size: Small']);
});

test('API mode uses unchanged payloads and retains removable labels on failures without mock fallback', async () => {
  vi.stubEnv('MODE', 'api');
  vi.stubEnv('VITE_API_BASE_URL', '');
  const fetch = vi.fn().mockImplementation(async () => Response.json({ content: [], page: 0, size: 8, totalElements: 0 }));
  vi.stubGlobal('fetch', fetch);
  await catalog();
  await select('Size', 'Small');
  await select('Stone type', 'Granite');
  expect(JSON.parse(fetch.mock.calls.at(-1)![1].body)).toEqual({
    filter: { stoneSizes: ['SMALL'], stoneTypes: ['GRANITE'] }, page: 0, size: 8,
    sort: { field: 'admissionDate', direction: 'desc' },
  });
  const count = fetch.mock.calls.length;
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'bas' } });
  expect(fetch).toHaveBeenCalledTimes(count);
  fetch.mockRejectedValueOnce(new TypeError('Network unavailable'));
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove size Small' })); });
  expect(screen.getByRole('alert').textContent).toContain('backend could not be reached');
  expect(screen.queryByRole('article')).toBeNull();
  expect(labels()).toEqual(['Stone type: Granite']);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Remove stone type Granite' })); });
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
  expect(fetch.mock.calls.at(-1)![0]).toBe('/api/v1/stones/search');
  expect(JSON.parse(fetch.mock.calls.at(-1)![1].body).filter).toEqual({ stoneSizes: [], stoneTypes: [] });
});
