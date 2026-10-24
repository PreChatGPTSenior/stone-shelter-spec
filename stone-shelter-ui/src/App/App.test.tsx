import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from './App';
import { StoneCard } from '../domain/stone/components/StoneCard/StoneCard';
import { mockStones } from '../mocks/data/stones';

beforeEach(() => {
  window.history.replaceState(null, '', '/');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});
function openFilterDropdown(label: string) {
  const trigger = screen.getByRole('button', { name: new RegExp(`^${label} \\d+$`) });
  if (trigger.getAttribute('aria-expanded') === 'false') fireEvent.click(trigger);
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

test('displays the application name', async () => {
  await act(async () => { render(<App />); });

  expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Stone Shelter');
});

test('renders the initial 8 stones with required content and total count', async () => {
  await act(async () => { render(<App />); });
  expect(screen.getAllByRole('article')).toHaveLength(8);
  expect(screen.getByText((_, element) => element?.textContent === 'Found 30 stones' && element.tagName === 'P').textContent).toBe('Found 30 stones');
  for (const stone of [...mockStones].reverse().slice(0, 8)) {
    expect(screen.getByRole('heading', { name: stone.name }).textContent).toBe(stone.name);
    expect(within(screen.getByRole('article', { name: stone.name })).getByRole('img').getAttribute('src')).toBe('/placeholder-rock.png');
  }
  expect(screen.getAllByText('Type')).toHaveLength(8);
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByText('Size')).toHaveLength(8);
  expect(screen.getAllByText(mockStones[29].biography!)).toHaveLength(8);
});

test('links only the header brand and exposes canonical detail and creation links', async () => {
  await act(async () => { render(<App />); });
  const header = screen.getByRole('banner');
  expect(within(header).getAllByRole('link')).toHaveLength(1);
  expect(within(header).getByRole('link', { name: 'Stone Shelter' }).getAttribute('href')).toBe('/stone-shelter/catalog');
  expect(within(header).queryByRole('navigation')).toBeNull();
  expect(header.textContent?.trim()).toBe('Stone Shelter');
  expect(screen.getAllByRole('link', { name: /View details for/ })).toHaveLength(8);
  expect(screen.getByRole('link', { name: 'Add stone' }).getAttribute('href')).toBe('/stone-shelter/add-stone');
  expect(within(header).queryAllByRole('button')).toHaveLength(0);
  expect(screen.queryByRole('button', { name: /adopt|favorite|details/i })).toBeNull();
});

test('uses a supplied photo and falls back when it cannot load', async () => {
  await act(async () => { render(<StoneCard stone={{ ...mockStones[0], photo: '/missing-stone.png' }} />); });
  const photo = screen.getByAltText('Mars');
  expect(photo.getAttribute('src')).toBe('/missing-stone.png');
  await act(async () => { fireEvent.error(photo); });
  expect(photo.getAttribute('src')).toBe('/placeholder-rock.png');
});


test('traverses every stone across pages with boundary controls', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  expect((screen.getByRole('button', { name: 'Previous' }) as HTMLButtonElement).disabled).toBe(true);
  const names: string[] = [];
  for (let page = 1; page <= 3; page++) {
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: `Page ${page}` })); });
    expect(screen.getAllByRole('article')).toHaveLength(page === 3 ? 6 : 12);
    names.push(...within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 }).map(h => h.textContent!));
    expect(screen.getByRole('button', { name: `Page ${page}` }).getAttribute('aria-current')).toBe('page');
  }
  expect(names).toEqual([...mockStones].reverse().map(stone => stone.name));
  expect((screen.getByRole('button', { name: 'Next' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Previous' })); });
  expect(screen.getByRole('heading', { name: 'Orion' })).toBeTruthy();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next' })); });
  expect(screen.getAllByRole('article')).toHaveLength(6);
});

test('changes page size to 24 and resets to the first page', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 3' })); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '24' } }); });
  expect(screen.getAllByRole('article')).toHaveLength(24);
  expect(screen.getByRole('heading', { name: 'Meadow' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Page 3' })).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next' })); });
  expect(screen.getAllByRole('article')).toHaveLength(6);
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  expect(screen.getAllByRole('article')).toHaveLength(12);
  expect(screen.getByRole('heading', { name: 'Meadow' })).toBeTruthy();
});


test('sort selection resets page, preserves size and remains selected during navigation', async () => {
  await act(async () => { render(<App />); });
  const sort = screen.getByRole('combobox', { name: 'Sort stones' });
  expect((sort as HTMLSelectElement).value).toBe('NEWEST');
  expect(within(sort).getAllByRole('option').map(option => option.textContent)).toEqual([
    'Newest first', 'Oldest first', 'Name: A–Z', 'Name: Z–A',
    'Size: small to large', 'Size: large to small',
  ]);
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '24' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next' })); });
  await act(async () => { fireEvent.change(sort, { target: { value: 'OLDEST' } }); });
  expect(screen.getAllByRole('article')).toHaveLength(24);
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Mars');
  expect(screen.getByRole('button', { name: 'Page 1' }).getAttribute('aria-current')).toBe('page');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next' })); });
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Fern');
  expect((sort as HTMLSelectElement).value).toBe('OLDEST');
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  expect((sort as HTMLSelectElement).value).toBe('OLDEST');
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Mars');
  await act(async () => { fireEvent.change(sort, { target: { value: 'NAME_ASC' } }); });
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Ash');
  await act(async () => { fireEvent.change(sort, { target: { value: 'NAME_DESC' } }); });
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Wren');
  await act(async () => { fireEvent.change(sort, { target: { value: 'SIZE_ASC' } }); });
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Mars');
  await act(async () => { fireEvent.change(sort, { target: { value: 'SIZE_DESC' } }); });
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 })[0].textContent).toBe('Luna');
});


test('combines size/type selections, counts groups and resets after reopening', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '24' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Medium' })); });
  expect(screen.getAllByRole('article')).toHaveLength(20);
  openFilterDropdown('Stone type');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Basalt' })); });
  openFilterDropdown('Stone type');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Granite' })); });
  openFilterDropdown('Stone type');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Obsidian' })); });
  await act(async () => { fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } }); });
  expect(screen.getAllByRole('article')).toHaveLength(6);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close filters' })); });
  expect(screen.getByLabelText('3 active filter groups').textContent).toBe('3');
  expect(screen.queryByRole('checkbox')).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Open filters' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reset filters' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close filters' })); });
  expect(screen.getByLabelText('0 active filter groups').textContent).toBe('0');
  expect(screen.getAllByRole('article')).toHaveLength(24);
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  expect(screen.getByRole('button', { name: 'Open filters' })).toBeTruthy();
});

test('invalid date draft preserves applied range and page until corrected or reset', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-10' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  const previousNames = within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 }).map(h => h.textContent);
  await act(async () => { fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-01' } }); });
  expect(screen.getByRole('alert').textContent).toBe('From must be on or before To.');
  expect(screen.getByLabelText('To').getAttribute('aria-invalid')).toBe('true');
  expect(within(screen.getByRole('main', { name: 'Stone catalog' })).getAllByRole('heading', { level: 2 }).map(h => h.textContent)).toEqual(previousNames);
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  expect(screen.getAllByRole('article')).toHaveLength(7);
  await act(async () => { fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-10' } }); });
  expect(screen.queryByRole('alert')).toBeNull();
  expect(screen.getAllByRole('article')).toHaveLength(1);
  expect(screen.getByRole('heading', { name: 'River' })).toBeTruthy();
  await act(async () => { fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-01' } }); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close filters' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Open filters' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reset filters' })); });
  expect(screen.queryByRole('alert')).toBeNull();
  expect((screen.getByLabelText('From') as HTMLInputElement).value).toBe('');
  expect((screen.getByLabelText('To') as HTMLInputElement).value).toBe('');
});

test('empty matches disable navigation and retain reset; sort and paging preserve filters', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  openFilterDropdown('Size');
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Medium' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Next' })); });
  expect(screen.getAllByRole('article')).toHaveLength(8);
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  expect(screen.getAllByRole('article')).toHaveLength(12);
  expect(screen.getByRole('heading', { name: 'Mars' })).toBeTruthy();
  openFilterDropdown('Size');
  expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
  await act(async () => { fireEvent.change(screen.getByLabelText('From'), { target: { value: '2030-01-01' } }); });
  expect(screen.queryAllByRole('article')).toHaveLength(0);
  expect(screen.getByRole('status').textContent).toContain('No stones match');
  expect(screen.queryByRole('button', { name: 'Page 1' })).toBeNull();
  expect((screen.getByRole('button', { name: 'Next' }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByRole('button', { name: 'Previous' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Reset filters' })); });
  expect(screen.getAllByRole('article')).toHaveLength(12);
});


test('defaults to eight cards and offers 8, 12 and 24 with complete traversal', async () => {
  await act(async () => { render(<App />); });
  const size = screen.getByLabelText('Stones per page');
  expect((size as HTMLSelectElement).value).toBe('8');
  expect(within(size).getAllByRole('option').map(option => option.textContent)).toEqual(['8', '12', '24']);
  const names: string[] = [];
  for (let page = 1; page <= 4; page++) {
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: `Page ${page}` })); });
    expect(screen.getAllByRole('article')).toHaveLength(page === 4 ? 6 : 8);
    names.push(...within(screen.getByRole('main')).getAllByRole('heading', { level: 2 }).map(h => h.textContent!));
  }
  expect(names).toEqual([...mockStones].reverse().map(stone => stone.name));
});


test('mobile sidebar is modal, traps focus and restores the opening control', async () => {
  const width = window.innerWidth;
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
  try {
    await act(async () => { render(<App />); });
    expect(screen.queryByRole('checkbox')).toBeNull();
    const opener = screen.getByRole('button', { name: 'Open filters' });
    await act(async () => { fireEvent.click(opener); });
    const dialog = screen.getByRole('dialog', { name: 'Filters' });
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    const close = screen.getByRole('button', { name: 'Close filters' });
    expect(document.activeElement).toBe(close);
    expect(document.body.style.overflow).toBe('hidden');
    expect((document.querySelector('.catalog-slot') as HTMLElement).inert).toBe(true);
    await act(async () => { fireEvent.keyDown(document, { key: 'Tab', shiftKey: true }); });
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Reset filters' }));
    await act(async () => { fireEvent.keyDown(document, { key: 'Tab' }); });
    expect(document.activeElement).toBe(close);
    openFilterDropdown('Size');
    await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
    await act(async () => { fireEvent.keyDown(document, { key: 'Escape' }); });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByLabelText('1 active filter groups').textContent).toBe('1');
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Open filters' }));
    expect(document.body.style.overflow).toBe('');
    expect((document.querySelector('.catalog-slot') as HTMLElement).inert).toBe(false);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Open filters' })); });
    openFilterDropdown('Size');
    expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
    await act(async () => { fireEvent.click(document.querySelector('.sidebar-backdrop')!); });
    expect(screen.queryByRole('dialog')).toBeNull();
  } finally {
    cleanup();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
  }
});
