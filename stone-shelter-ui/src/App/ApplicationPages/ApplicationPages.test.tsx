import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from '../App';

beforeEach(() => {
  window.history.replaceState(null, '', '/');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});
function openFilterDropdown(label: string) {
  const trigger = screen.getByRole('button', { name: new RegExp(`^${label} \\d+$`) });
  if (trigger.getAttribute('aria-expanded') === 'false') fireEvent.click(trigger);
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

test('photo links address the precise stone after sorting and paging, then return with catalog state and scroll', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Medium' })); });
  await act(async () => { fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  await act(async () => { fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-08-31' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close filters' })); });
  const catalog = screen.getByRole('main', { name: 'Stone catalog' });
  const names = within(catalog).getAllByRole('heading', { level: 2 }).map(heading => heading.textContent);
  const link = within(catalog).getByRole('link', { name: `View details for ${names[0]}` });
  const href = link.getAttribute('href');
  vi.spyOn(window, 'scrollY', 'get').mockReturnValue(412);
  await act(async () => { fireEvent.click(link); });
  expect(window.location.pathname).toBe(href);
  expect(within(screen.getByRole('main')).getByRole('heading', { level: 1 }).textContent).toBe(names[0]);
  expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 0, behavior: 'instant' });
  expect(document.activeElement?.tagName).toBe('H1');
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(window.location.pathname).toBe('/stone-shelter/catalog');
  expect(within(screen.getByRole('main')).getAllByRole('heading', { level: 2 }).map(heading => heading.textContent)).toEqual(names);
  expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 412, behavior: 'instant' });
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  expect((screen.getByLabelText('Stones per page') as HTMLSelectElement).value).toBe('12');
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  expect(screen.getByRole('button', { name: 'Open filters' })).toBeTruthy();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Open filters' })); });
  openFilterDropdown('Size');
  expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
  openFilterDropdown('Size');
  expect((screen.getByRole('checkbox', { name: 'Medium' }) as HTMLInputElement).checked).toBe(true);
  expect((screen.getByLabelText('To') as HTMLInputElement).value).toBe('2026-08-31');
  expect(screen.getByRole('alert').textContent).toBe('From must be on or before To.');
});

test('direct visits look up stones beyond the visible catalog and return to defaults', async () => {
  window.history.replaceState(null, '', '/stone-shelter/stones/1');
  await act(async () => { render(<App />); });
  expect(within(screen.getByRole('main')).getByRole('heading', { name: 'Mars' })).toBeTruthy();
  expect(screen.getByText('ID 1')).toBeTruthy();
  expect(screen.getByText('Basalt')).toBeTruthy();
  expect(screen.getByText('Small')).toBeTruthy();
  expect(screen.getByText('Available')).toBeTruthy();
  expect(screen.getByText('September 1, 2026')).toBeTruthy();
  expect(screen.getAllByRole('button', { name: /View photo/ })).toHaveLength(6);
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getAllByRole('article')).toHaveLength(8);
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('NEWEST');
});

test.each(['/stone-shelter/stones/999', '/stone-shelter/stones/not-an-id', '/stone-shelter/stones/0', '/stone-shelter/stones/1/extra', '/stone-shelter/stones/9007199254740993'])('shows a recoverable not-found state for %s', async path => {
  window.history.replaceState(null, '', path);
  await act(async () => { render(<App />); });
  expect(screen.getByRole('heading', { name: 'Stone not found' })).toBeTruthy();
  expect(screen.queryByText('Characteristics')).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getAllByRole('article')).toHaveLength(8);
});

test('browser back and forward restore routes and reset gallery selection on each detail visit', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'View details for Mars' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'View photo 3 of Mars' })); });
  expect(screen.getByRole('button', { name: 'View photo 3 of Mars' }).getAttribute('aria-pressed')).toBe('true');
  window.history.back();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy());
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  window.history.forward();
  await waitFor(() => expect(screen.getByRole('button', { name: 'View photo 1 of Mars' })).toBeTruthy());
  expect(screen.getByRole('button', { name: 'View photo 1 of Mars' }).getAttribute('aria-pressed')).toBe('true');
});

test('modified clicks retain native link behavior without changing the current route', async () => {
  await act(async () => { render(<App />); });
  const link = screen.getByRole('link', { name: 'View details for Meadow' });
  let preventedBeforeNativeDefault = true;
  const cancelBrowserDefault = (event: Event) => {
    preventedBeforeNativeDefault = event.defaultPrevented;
    event.preventDefault();
  };
  document.addEventListener('click', cancelBrowserDefault);
  try {
    await act(async () => { fireEvent.click(link, { ctrlKey: true }); });
    expect(preventedBeforeNativeDefault).toBe(false);
    expect(window.location.pathname).toBe('/stone-shelter/catalog');
    expect(link.getAttribute('href')).toBe('/stone-shelter/stones/30');
  } finally { document.removeEventListener('click', cancelBrowserDefault); }
});

test.each([
  ['/', '/stone-shelter/catalog', 'Stone catalog'],
  ['/stone-shelter/', '/stone-shelter/catalog', 'Stone catalog'],
  ['/stone-shelter/catalog/', '/stone-shelter/catalog', 'Stone catalog'],
  ['/stones/new/', '/stone-shelter/add-stone', 'Add a stone'],
  ['/stones/1/', '/stone-shelter/stones/1', 'Mars'],
  ['/stone-shelter/stones/1/', '/stone-shelter/stones/1', 'Mars'],
])('normalizes %s without adding history entries or losing query/fragment', async (path, canonical, pageName) => {
  window.history.replaceState({ source: 'direct' }, '', `${path}?source=bookmark#content`);
  const historyLength = window.history.length;
  await act(async () => { render(<App />); });
  expect(window.location.pathname).toBe(canonical);
  expect(window.location.search).toBe('?source=bookmark');
  expect(window.location.hash).toBe('#content');
  expect(window.history.length).toBe(historyLength);
  expect(window.history.state).toEqual({ source: 'direct' });
  expect(screen.getByRole('main', { name: pageName })).toBeTruthy();
});

test('header brand image and text navigate from a direct stone visit and participate in history', async () => {
  window.history.replaceState(null, '', '/stone-shelter/stones/1');
  await act(async () => { render(<App />); });
  const link = within(screen.getByRole('banner')).getByRole('link', { name: 'Stone Shelter' });
  expect(link.getAttribute('href')).toBe('/stone-shelter/catalog');
  const historyLength = window.history.length;
  await act(async () => { fireEvent.click(link.querySelector('svg')!); });
  expect(window.location.pathname).toBe('/stone-shelter/catalog');
  expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy();
  expect(window.history.length).toBe(historyLength + 1);
  await act(async () => { fireEvent.click(within(link).getByRole('heading', { name: 'Stone Shelter' })); });
  expect(window.history.length).toBe(historyLength + 1);
  window.history.back();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Mars' })).toBeTruthy());
  await act(async () => { fireEvent.click(within(link).getByRole('heading', { name: 'Stone Shelter' })); });
  expect(window.location.pathname).toBe('/stone-shelter/catalog');
  expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy();
  expect(window.history.length).toBe(historyLength + 1);
});

test('unknown pages offer canonical recovery with shared layout and heading focus', async () => {
  window.history.replaceState(null, '', '/stone-shelter/missing');
  await act(async () => { render(<App />); });
  expect(screen.getByRole('banner')).toBeTruthy();
  expect(screen.getByRole('contentinfo')).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Page not found' }));
  expect(screen.queryByRole('heading', { name: 'Stone not found' })).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(window.location.pathname).toBe('/stone-shelter/catalog');
  expect(screen.getAllByRole('article')).toHaveLength(8);
});

test('native external, download, target and modified link actions are not intercepted', async () => {
  await act(async () => { render(<App />); });
  const link = screen.getByRole('link', { name: 'Add stone' });
  let prevented = true;
  const cancelNative = (event: Event) => { prevented = event.defaultPrevented; event.preventDefault(); };
  document.addEventListener('click', cancelNative);
  try {
    for (const modifiers of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }]) {
      await act(async () => { fireEvent.click(link, modifiers); });
      expect(prevented).toBe(false);
    }
    link.setAttribute('target', '_blank');
    await act(async () => { fireEvent.click(link); });
    expect(prevented).toBe(false);
    link.removeAttribute('target');
    link.setAttribute('download', '');
    await act(async () => { fireEvent.click(link); });
    expect(prevented).toBe(false);
    link.removeAttribute('download');
    link.setAttribute('href', 'https://example.com/stone-shelter/catalog');
    await act(async () => { fireEvent.click(link); });
    expect(prevented).toBe(false);
    expect(window.location.pathname).toBe('/stone-shelter/catalog');
  } finally { document.removeEventListener('click', cancelNative); }
});
