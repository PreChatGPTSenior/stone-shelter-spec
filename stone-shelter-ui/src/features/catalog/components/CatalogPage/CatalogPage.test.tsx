import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from '../../../../App/App';
import * as stonesApi from '../../../../api/stonesApi';
import { ApiError } from '../../../../api/ApiError';
import type { StonesSearchResponse } from '../../../../api/dto/StonesSearchResponse';
import { mockStones } from '../../../../mocks/data/stones';

function openFilterDropdown(label: string) {
  const trigger = screen.getByRole('button', { name: new RegExp(`^${label} \\d+$`) });
  if (trigger.getAttribute('aria-expanded') === 'false') fireEvent.click(trigger);
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

function deferred() {
  let resolve!: (response: StonesSearchResponse) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<StonesSearchResponse>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

function catalog() {
  window.history.replaceState(null, '', '/stone-shelter/catalog');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  return render(<App />);
}

test('distinguishes pending, failure and successful empty results, and retries manually', async () => {
  const pending = deferred();
  const search = vi.spyOn(stonesApi, 'getCatalogStones').mockReturnValueOnce(pending.promise)
    .mockResolvedValueOnce({ content: [], page: 0, size: 8, totalElements: 0 });
  catalog();
  expect(screen.getByRole('status').textContent).toBe('Loading stones…');
  expect(screen.queryByText(/No stones match/)).toBeNull();
  expect(screen.queryAllByRole('article')).toHaveLength(0);
  await act(async () => pending.reject(new ApiError(503, 'Catalog temporarily unavailable.')));
  expect(screen.getByRole('alert').textContent).toBe('Catalog temporarily unavailable.');
  expect(screen.queryByText(/No stones match/)).toBeNull();
  expect(search).toHaveBeenCalledTimes(1);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry catalog' })); });
  expect(screen.getByRole('status').textContent).toContain('No stones match');
  expect(screen.queryByRole('alert')).toBeNull();
  expect(search).toHaveBeenCalledTimes(2);
});

test('ignores stale filter results and uses current response metadata instead of page length', async () => {
  const older = deferred();
  const newer = deferred();
  const search = vi.spyOn(stonesApi, 'getCatalogStones').mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise);
  catalog();
  openFilterDropdown('Size');
  fireEvent.click(screen.getByRole('checkbox', { name: 'Small' }));
  expect(search).toHaveBeenLastCalledWith(0, 8, { field: 'admissionDate', direction: 'desc' }, { stoneSizes: ['SMALL'] });
  await act(async () => newer.resolve({ content: [mockStones[0]], page: 0, size: 8, totalElements: 17 }));
  expect(screen.getAllByRole('article')).toHaveLength(1);
  expect(screen.getByRole('button', { name: 'Page 3' })).toBeTruthy();
  await act(async () => older.resolve({ content: [mockStones[29]], page: 0, size: 8, totalElements: 30 }));
  expect(screen.getByRole('heading', { name: 'Mars' })).toBeTruthy();
  expect(screen.queryByRole('heading', { name: 'Meadow' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Page 4' })).toBeNull();
  expect(screen.getByText((_, element) => element?.tagName === 'P' && element.textContent === 'Found 17 stones')).toBeTruthy();
});

test('refresh corrects an empty last page without resetting filters, sort or size', async () => {
  const search = vi.spyOn(stonesApi, 'getCatalogStones');
  await act(async () => { catalog(); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  const link = within(screen.getByRole('main')).getAllByRole('link', { name: /View details for/ })[0];
  await act(async () => { fireEvent.click(link); });
  search.mockResolvedValueOnce({ content: [], page: 1, size: 8, totalElements: 8 })
    .mockResolvedValueOnce({ content: [mockStones[0]], page: 0, size: 8, totalElements: 8 });
  fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' }));
  fireEvent.change(screen.getByLabelText('Your name'), { target: { value: 'Visitor' } });
  fireEvent.change(screen.getByLabelText('Contact details'), { target: { value: 'Window' } });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(search).toHaveBeenLastCalledWith(0, 8, { field: 'admissionDate', direction: 'asc' }, { stoneSizes: ['SMALL'] });
  expect(screen.getByRole('button', { name: 'Page 1' }).getAttribute('aria-current')).toBe('page');
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  openFilterDropdown('Size');
  expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
  expect((screen.getByLabelText('Stones per page') as HTMLSelectElement).value).toBe('8');
});
