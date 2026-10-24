import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import * as stonesApi from '../../api/stonesApi';
import type { StoneCreateResponse } from '../../api/dto/StoneCreateResponse';
import { resetMockStoneCreations, resetMockStoneReservations } from '../../mocks/api/mockStonesApi';
import { App } from '../App';
import { ApiError } from '../../api/ApiError';

beforeEach(() => {
  window.history.replaceState(null, '', '/');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); resetMockStoneCreations(); resetMockStoneReservations(); });

function fillDetails(name = 'Photo-free stone', size = 'SMALL') {
  fireEvent.change(screen.getByLabelText('Name *'), { target: { value: name } });
  fireEvent.change(screen.getByLabelText('Stone type *'), { target: { value: 'MARBLE' } });
  fireEvent.change(screen.getByLabelText('Size *'), { target: { value: size } });
}
function submit() { fireEvent.submit(screen.getByRole('form', { name: 'Stone details' })); }

test('creation failure retains details and draft references for manual retry without photo reupload', async () => {
  const create = vi.spyOn(stonesApi, 'createStone').mockRejectedValueOnce(new ApiError(500, 'Creation failed. Please try again.'));
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft');
  window.history.replaceState(null, '', '/stone-shelter/add-stone');
  await act(async () => { render(<App />); });
  fillDetails('Retry stone');
  fireEvent.change(screen.getByLabelText('Biography (optional)'), { target: { value: 'Preserved biography' } });
  fireEvent.change(screen.getByLabelText('Choose photos'), { target: { files: [new File(['photo'], 'stone.png', { type: 'image/png' })] } });
  await screen.findByText('1 / 16 photos');
  await act(async () => { submit(); });
  expect(screen.getByRole('alert').textContent).toBe('Creation failed. Please try again.');
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('Retry stone');
  expect((screen.getByLabelText('Biography (optional)') as HTMLTextAreaElement).value).toBe('Preserved biography');
  expect(screen.getByText('1 / 16 photos')).toBeTruthy();
  expect((screen.getByRole('button', { name: 'Add stone' }) as HTMLButtonElement).disabled).toBe(false);
  expect(screen.queryByText('Stone added successfully.')).toBeNull();
  expect(create).toHaveBeenCalledTimes(1);
  await act(async () => { submit(); });
  expect(screen.getByText('Stone added successfully.')).toBeTruthy();
  expect(create).toHaveBeenCalledTimes(2);
  expect(create.mock.calls[1][0]).toEqual(create.mock.calls[0][0]);
  expect(upload).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('alert')).toBeNull();
});
async function confirmation() {
  await waitFor(() => expect(screen.getByRole('status').textContent).toBe('Stone added successfully.'));
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { resolve, promise };
}
const photo = new File(['local image bytes'], 'stone.png', { type: 'image/png' });

test.each(['/stone-shelter/add-stone', '/stone-shelter/add-stone/'])('direct and refreshed entry at %s renders a fresh accessible creation page before ID parsing', async path => {
  window.history.replaceState(null, '', path);
  const first = await act(async () => render(<App />));
  expect(screen.getByRole('main', { name: 'Add a stone' })).toBeTruthy();
  expect(screen.getByRole('banner')).toBeTruthy();
  expect(screen.getByRole('contentinfo')).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Add a stone' }));
  fillDetails();
  first.unmount();
  await act(async () => { render(<App />); });
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('');
  expect(screen.queryByRole('heading', { name: 'Stone not found' })).toBeNull();
});

test('catalog entry, success, detail and refreshed catalog all support a stone without uploaded photos', async () => {
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft');
  const create = vi.spyOn(stonesApi, 'createStone');
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  expect(window.location.pathname).toBe('/stone-shelter/add-stone');
  fillDetails();
  submit();
  await confirmation();
  expect(window.location.pathname).toBe('/stone-shelter/add-stone');
  expect(screen.queryByRole('form')).toBeNull();
  expect(screen.queryByRole('button', { name: 'Add stone' })).toBeNull();
  expect(document.activeElement).toBe(screen.getByRole('status'));
  expect(screen.getByText('ID 31')).toBeTruthy();
  expect(screen.getByAltText('Photo coming soon for Photo-free stone').getAttribute('src')).toBe('/placeholder-rock.png');
  expect(create).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ photoUploadIds: [], adoptionStatus: 'AVAILABLE' }));
  expect(create.mock.calls[0][0]).not.toHaveProperty('admissionDate');
  expect(upload).not.toHaveBeenCalled();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'View stone' })); });
  expect(window.location.pathname).toBe('/stone-shelter/stones/31');
  expect(screen.getByRole('heading', { name: 'Photo-free stone' })).toBeTruthy();
  expect(screen.getByAltText('Photo coming soon for Photo-free stone').getAttribute('src')).toBe('/placeholder-rock.png');
  expect(screen.queryByRole('group', { name: 'Photo thumbnails' })).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getByText('31', { selector: 'strong' })).toBeTruthy();
  expect(screen.getByRole('link', { name: 'View details for Photo-free stone' })).toBeTruthy();
  expect(screen.getByAltText('Photo coming soon for Photo-free stone').getAttribute('src')).toBe('/placeholder-rock.png');
});

test.each([1, 16])('creation with %i photos uses uploaded references once and exposes the returned gallery across views', async count => {
  const create = vi.spyOn(stonesApi, 'createStone');
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft');
  window.history.replaceState(null, '', '/stone-shelter/add-stone');
  await act(async () => { render(<App />); });
  fillDetails('Gallery stone');
  await act(async () => { fireEvent.change(screen.getByLabelText('Choose photos'), { target: { files: Array.from({ length: count }, () => photo) } }); });
  await waitFor(() => expect(screen.getByText(`${count} / 16 photos`)).toBeTruthy());
  const uploads = await Promise.all(upload.mock.results.map(result => result.value));
  submit();
  await confirmation();
  expect(create).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ photoUploadIds: uploads.map(value => value.id) }));
  expect(upload).toHaveBeenCalledTimes(count);
  expect(screen.getAllByRole('button', { name: /View photo/ })).toHaveLength(count);
  expect(screen.getByAltText(`Gallery stone, photo 1 of ${count}`).getAttribute('src')).toBe(uploads[0].url);
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'View stone' })); });
  expect(screen.getAllByRole('button', { name: /View photo/ })).toHaveLength(count);
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getByAltText('Gallery stone').getAttribute('src')).toBe(uploads[0].url);
});

test('creation pending guards click/Enter submissions, locks all input and changes to confirmation exactly once', async () => {
  const pending = deferred<StoneCreateResponse>();
  const create = vi.spyOn(stonesApi, 'createStone').mockReturnValue(pending.promise);
  window.history.replaceState(null, '', '/stone-shelter/add-stone');
  await act(async () => { render(<App />); });
  fillDetails();
  submit();
  expect(screen.getByRole('status', { name: 'Stone creation progress' }).textContent).toBe('Adding stone…');
  for (const control of [screen.getByLabelText('Name *'), screen.getByLabelText('Stone type *'), screen.getByLabelText('Size *'), screen.getByLabelText('Biography (optional)'), screen.getByLabelText('Choose photos')]) {
    expect(control.matches(':disabled')).toBe(true);
  }
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Adding stone…' })); });
  submit();
  submit();
  expect(create).toHaveBeenCalledTimes(1);
  await act(async () => { pending.resolve({ ...create.mock.calls[0][0], id: 99, admissionDate: '2026-10-08T10:00:00Z', photos: [] }); });
  await confirmation();
  expect(screen.getByText('ID 99')).toBeTruthy();
  expect(create).toHaveBeenCalledTimes(1);
});

test('Add another stone resets details and uploaded references instead of reusing the previous request', async () => {
  const create = vi.spyOn(stonesApi, 'createStone');
  window.history.replaceState(null, '', '/stone-shelter/add-stone');
  await act(async () => { render(<App />); });
  fillDetails('First stone');
  await act(async () => { fireEvent.change(screen.getByLabelText('Biography (optional)'), { target: { value: 'Story' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Choose photos'), { target: { files: [photo] } }); });
  await waitFor(() => expect(screen.getByText('1 / 16 photos')).toBeTruthy());
  submit();
  await confirmation();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Add another stone' })); });
  for (const label of ['Name *', 'Stone type *', 'Size *', 'Biography (optional)']) {
    expect((screen.getByLabelText(label) as HTMLInputElement).value).toBe('');
  }
  expect(screen.queryByLabelText('Admission date *')).toBeNull();
  expect(screen.getByText('0 / 16 photos')).toBeTruthy();
  expect(within(screen.getByRole('list', { name: 'Photo slots' })).getAllByRole('listitem', { name: /Empty photo slot/ })).toHaveLength(16);
  expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Add a stone' }));
  fillDetails('Second stone');
  submit();
  await confirmation();
  expect(screen.getByText('ID 32')).toBeTruthy();
  expect(create.mock.calls[1][0].photoUploadIds).toEqual([]);
  expect(create).toHaveBeenCalledTimes(2);
});

test('return after creation refreshes totals with all catalog choices preserved and excludes nonmatching additions', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Stones per page'), { target: { value: '12' } }); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  fireEvent.click(screen.getByRole('button', { name: 'Size 0' }));
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Small' })); });
  await act(async () => { fireEvent.click(screen.getByRole('checkbox', { name: 'Medium' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Page 2' })); });
  const names = screen.getAllByRole('article').map(article => within(article).getByRole('heading').textContent);
  const total = Number(screen.getByRole('main').querySelector('strong')?.textContent);
  vi.spyOn(window, 'scrollY', 'get').mockReturnValue(280);
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  fillDetails('Filtered out', 'LARGE');
  submit();
  await confirmation();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  expect((screen.getByLabelText('Stones per page') as HTMLSelectElement).value).toBe('12');
  expect((screen.getByLabelText('Sort stones') as HTMLSelectElement).value).toBe('OLDEST');
  fireEvent.click(screen.getByRole('button', { name: 'Size 2' }));
  expect((screen.getByRole('checkbox', { name: 'Small' }) as HTMLInputElement).checked).toBe(true);
  expect((screen.getByRole('checkbox', { name: 'Medium' }) as HTMLInputElement).checked).toBe(true);
  expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 280, behavior: 'instant' });
  expect(Number(screen.getByRole('main').querySelector('strong')?.textContent)).toBe(total);
  expect(screen.getAllByRole('article').map(article => within(article).getByRole('heading').textContent)).toEqual(names);
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  fillDetails('Matching addition');
  submit();
  await confirmation();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(Number(screen.getByRole('main').querySelector('strong')?.textContent)).toBe(total + 1);
  expect(screen.getByRole('button', { name: 'Page 2' }).getAttribute('aria-current')).toBe('page');
  expect(screen.getByRole('link', { name: 'View details for Matching addition' })).toBeTruthy();
});

test('back/forward and revisiting creation discard the unsaved form', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  fillDetails('Unsaved');
  window.history.back();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Stone catalog' })).toBeTruthy());
  window.history.forward();
  await waitFor(() => expect(screen.getByRole('main', { name: 'Add a stone' })).toBeTruthy());
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('');
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  expect((screen.getByLabelText('Stone type *') as HTMLSelectElement).value).toBe('');
});

test('modified creation links keep native behavior', async () => {
  await act(async () => { render(<App />); });
  const link = screen.getByRole('link', { name: 'Add stone' });
  let prevented = true;
  const cancelNative = (event: Event) => { prevented = event.defaultPrevented; event.preventDefault(); };
  document.addEventListener('click', cancelNative);
  try {
    await act(async () => { fireEvent.click(link, { metaKey: true }); });
    expect(prevented).toBe(false);
    expect(window.location.pathname).toBe('/stone-shelter/catalog');
    expect(link.getAttribute('href')).toBe('/stone-shelter/add-stone');
  } finally { document.removeEventListener('click', cancelNative); }
});

test('a creation completing after navigation refreshes catalog without replacing a later empty form', async () => {
  const originalCreate = stonesApi.createStone;
  const pending = deferred<StoneCreateResponse>();
  vi.spyOn(stonesApi, 'createStone').mockReturnValue(pending.promise);
  window.history.replaceState(null, '', '/stone-shelter/add-stone');
  await act(async () => { render(<App />); });
  fillDetails('Late stone');
  submit();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  await act(async () => { pending.resolve(await originalCreate({
    name: 'Late stone', stoneType: 'MARBLE', stoneSize: 'SMALL', biography: '',
    adoptionStatus: 'AVAILABLE', photoUploadIds: [],
  })); });
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('');
  expect(screen.queryByText('Stone added successfully.')).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  expect(screen.getByRole('link', { name: 'View details for Late stone' })).toBeTruthy();
});

test('a created stone uses the existing adoption flow and reservation readback', async () => {
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Add stone' })); });
  fillDetails('Adoptable addition');
  submit();
  await confirmation();
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'View stone' })); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' })); });
  const dialog = screen.getByRole('dialog');
  await act(async () => { fireEvent.change(within(dialog).getByLabelText('Your name'), { target: { value: 'Visitor' } }); });
  await act(async () => { fireEvent.change(within(dialog).getByLabelText('Contact details'), { target: { value: 'visitor@example.com' } }); });
  await act(async () => { fireEvent.click(within(dialog).getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(screen.getByText('Reserved', { selector: '.stone-status' })).toBeTruthy());
  expect((await stonesApi.getStone(31))?.adoptionStatus).toBe('RESERVED');
});
