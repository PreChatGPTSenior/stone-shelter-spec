import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import * as stonesApi from '../../../../api/stonesApi';
import type { StonePhotoDraftUploadResponse } from '../../../../api/dto/StonePhotoDraftUploadResponse';
import { resetMockStoneCreations } from '../../../../mocks/api/mockStonesApi';
import { AddStoneForm } from './AddStoneForm';
import { ApiError } from '../../../../api/ApiError';

afterEach(() => { cleanup(); vi.restoreAllMocks(); resetMockStoneCreations(); });

const photo = (name: string, type = 'image/png') => new File(['image bytes'], name, { type });
const draft = (id: string): StonePhotoDraftUploadResponse => ({
  id, url: `data:image/png;base64,${btoa(id)}`,
  uploadedAt: '2026-01-01T00:00:00Z', expiresAt: '2026-01-02T00:00:00Z',
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
function select(files: File[]) { fireEvent.change(screen.getByLabelText('Choose photos'), { target: { files } }); }
function details() {
  fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Photo stone' } });
  fireEvent.change(screen.getByLabelText('Stone type *'), { target: { value: 'BASALT' } });
  fireEvent.change(screen.getByLabelText('Size *'), { target: { value: 'SMALL' } });
}

test('retains successful uploads in selection order after a partial failure without resending prior photos', async () => {
  const later = deferred<StonePhotoDraftUploadResponse>();
  let fail!: (failure: Error) => void;
  const failed = new Promise<StonePhotoDraftUploadResponse>((_, reject) => { fail = reject; });
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft').mockResolvedValueOnce(draft('existing'))
    .mockReturnValueOnce(later.promise).mockReturnValueOnce(failed).mockResolvedValueOnce(draft('last'));
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  details();
  await act(async () => { select([photo('existing.png')]); });
  select([photo('first.png'), photo('failed.png'), photo('last.png')]);
  await act(async () => fail(new ApiError(503, 'Draft storage unavailable.')));
  expect(screen.getByText('1 / 16 photos')).toBeTruthy();
  expect((screen.getByRole('button', { name: 'Add stone' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => later.resolve(draft('first')));
  expect(screen.getByRole('alert').textContent).toContain('Draft storage unavailable. Successful photos were retained.');
  expect(screen.getAllByAltText(/Photo \d preview/).map(element => element.getAttribute('src')))
    .toEqual(['existing', 'first', 'last'].map(id => draft(id).url));
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('Photo stone');
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ photoUploadIds: ['existing', 'first', 'last'] }));
  expect(upload).toHaveBeenCalledTimes(4);
  expect((screen.getByRole('button', { name: 'Add stone' }) as HTMLButtonElement).disabled).toBe(false);
});

test('batches retain order, repeated files get separate IDs and creation passes remaining references without reuploading', async () => {
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft');
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  details();
  const repeated = photo('same.png');
  select([photo('first.jpg', 'image/jpeg'), repeated]);
  await waitFor(() => expect(screen.getByText('2 / 16 photos')).toBeTruthy());
  select([photo('third.webp', 'image/webp'), repeated]);
  await waitFor(() => expect(screen.getByText('4 / 16 photos')).toBeTruthy());
  const uploaded = await Promise.all(upload.mock.results.map(result => result.value as Promise<StonePhotoDraftUploadResponse>));
  expect(new Set(uploaded.map(value => value.id)).size).toBe(4);
  const grid = screen.getByRole('list', { name: 'Photo slots' });
  expect(within(grid).getAllByRole('listitem')).toHaveLength(16);
  expect(within(grid).getAllByRole('listitem', { name: /Empty photo slot/ })).toHaveLength(12);
  expect(within(within(grid).getByRole('listitem', { name: 'Photo 1' })).getByText('Cover')).toBeTruthy();
  expect(screen.getByAltText('Photo 1 preview').getAttribute('src')).toBe(uploaded[0].url);
  fireEvent.click(screen.getByRole('button', { name: 'Remove photo 1' }));
  expect(screen.getByAltText('Photo 1 preview').getAttribute('src')).toBe(uploaded[1].url);
  expect(within(grid).getAllByRole('listitem')).toHaveLength(16);
  expect(within(grid).getAllByRole('listitem', { name: /Empty photo slot/ })).toHaveLength(13);
  expect(screen.getAllByAltText(/Photo \d preview/).map(element => element.getAttribute('src'))).toEqual(uploaded.slice(1).map(value => value.url));
  select([]);
  expect(screen.getByText('3 / 16 photos')).toBeTruthy();
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
    name: 'Photo stone', photoUploadIds: uploaded.slice(1).map(value => value.id),
  }));
  expect(Object.keys(onSubmit.mock.calls[0][0])).not.toContain('photo');
  expect(upload).toHaveBeenCalledTimes(4);
});

test.each([
  [() => [photo('ok.png'), photo('bad.gif', 'image/gif')], 'Choose JPEG, PNG or WebP photos.'],
  [() => [photo('ok.png'), photo('missing-type.png', '')], 'Choose JPEG, PNG or WebP photos.'],
  [() => [photo('ok.png'), new File([], 'empty.png', { type: 'image/png' })], 'Photos must not be empty.'],
  [() => [photo('ok.png'), new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' })], 'Each photo must be 10 MiB or smaller.'],
  [() => Array.from({ length: 16 }, (_, index) => photo(`${index}.png`)), 'Choose at most 16 photos in total.'],
])('invalid batches preserve all prior data and start no uploads (%s)', async (newBatch, message) => {
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft').mockResolvedValue(draft('existing'));
  render(<AddStoneForm onSubmit={vi.fn()} />);
  details();
  select([photo('existing.png')]);
  await waitFor(() => expect(screen.getByText('1 / 16 photos')).toBeTruthy());
  upload.mockClear();
  select(newBatch());
  expect(screen.getByRole('alert').textContent).toBe(message);
  expect(screen.getByLabelText('Choose photos').getAttribute('aria-describedby')).toContain(screen.getByRole('alert').id);
  expect(screen.getByText('1 / 16 photos')).toBeTruthy();
  expect(screen.getByAltText('Photo 1 preview').getAttribute('src')).toBe(draft('existing').url);
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('Photo stone');
  expect(upload).not.toHaveBeenCalled();
});

test('16 photos including exactly 10 MiB are accepted; a later batch cannot exceed the cumulative limit', async () => {
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft').mockImplementation(async file => draft(file.name));
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  details();
  select([new File([new Uint8Array(10 * 1024 * 1024)], 'limit.png', { type: 'image/png' })]);
  await waitFor(() => expect(screen.getByText('1 / 16 photos')).toBeTruthy());
  select(Array.from({ length: 15 }, (_, index) => photo(`${index}.png`)));
  await waitFor(() => expect(screen.getByText('16 / 16 photos')).toBeTruthy());
  const grid = screen.getByRole('list', { name: 'Photo slots' });
  expect(within(grid).getAllByRole('listitem')).toHaveLength(16);
  expect(within(grid).getAllByRole('img')).toHaveLength(16);
  expect(within(grid).queryAllByRole('listitem', { name: /Empty photo slot/ })).toHaveLength(0);
  expect(screen.queryByAltText('Stone preview')).toBeNull();
  select([photo('too-many.png')]);
  expect(screen.getByRole('alert').textContent).toBe('Choose at most 16 photos in total.');
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit.mock.calls[0][0].photoUploadIds).toEqual(['limit.png', ...Array.from({ length: 15 }, (_, index) => `${index}.png`)]);
  expect(upload).toHaveBeenCalledTimes(16);
});

test('pending uploads keep details editable, block submission and mutations, and finish in selection order', async () => {
  const first = deferred<StonePhotoDraftUploadResponse>();
  const second = deferred<StonePhotoDraftUploadResponse>();
  const upload = vi.spyOn(stonesApi, 'uploadStonePhotoDraft')
    .mockResolvedValueOnce(draft('existing')).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  details();
  select([photo('existing.png')]);
  await waitFor(() => expect(screen.getByText('1 / 16 photos')).toBeTruthy());
  select([photo('first.png'), photo('second.png')]);
  expect(screen.getByRole('status', { name: 'Photo upload progress' }).textContent).toBe('Uploading photos…');
  expect(screen.getByRole('region', { name: 'Photo preview' }).getAttribute('aria-busy')).toBe('true');
  expect(screen.getByLabelText('Name *').matches(':disabled')).toBe(false);
  fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Edited during upload' } });
  for (const control of [screen.getByLabelText('Choose photos'), screen.getByRole('button', { name: 'Remove photo 1' }), screen.getByRole('button', { name: 'Add stone' })]) {
    expect(control.matches(':disabled')).toBe(true);
  }
  select([photo('ignored.png')]);
  fireEvent.click(screen.getByRole('button', { name: 'Remove photo 1' }));
  fireEvent.submit(screen.getByRole('form'));
  expect(upload).toHaveBeenCalledTimes(3);
  expect(onSubmit).not.toHaveBeenCalled();
  await act(async () => { second.resolve(draft('second')); });
  expect(screen.getByText('1 / 16 photos')).toBeTruthy();
  await act(async () => { first.resolve(draft('first')); });
  expect(screen.getByText('3 / 16 photos')).toBeTruthy();
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit.mock.calls[0][0]).toMatchObject({ name: 'Edited during upload', photoUploadIds: ['existing', 'first', 'second'] });
});

test('removing all photos restores 16 empty slots and remains submittable; broken slot images fall back', async () => {
  vi.spyOn(stonesApi, 'uploadStonePhotoDraft').mockResolvedValue(draft('photo'));
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  details();
  select([photo('photo.png')]);
  await waitFor(() => expect(screen.getByText('1 / 16 photos')).toBeTruthy());
  const image = screen.getByAltText('Photo 1 preview');
  fireEvent.error(image);
  expect(image.getAttribute('src')).toBe('/placeholder-rock.png');
  fireEvent.error(image);
  expect(image.getAttribute('src')).toBe('/placeholder-rock.png');
  fireEvent.click(screen.getByRole('button', { name: 'Remove photo 1' }));
  expect(screen.getByText('0 / 16 photos')).toBeTruthy();
  const grid = screen.getByRole('list', { name: 'Photo slots' });
  expect(within(grid).getAllByRole('listitem', { name: /Empty photo slot/ })).toHaveLength(16);
  expect(within(grid).queryAllByRole('img')).toHaveLength(0);
  expect(onSubmit).not.toHaveBeenCalled();
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit.mock.calls[0][0].photoUploadIds).toEqual([]);
});

test('uploads finishing after leaving cannot populate the next form', async () => {
  const pending = deferred<StonePhotoDraftUploadResponse>();
  vi.spyOn(stonesApi, 'uploadStonePhotoDraft').mockReturnValue(pending.promise);
  const first = render(<AddStoneForm onSubmit={vi.fn()} />);
  select([photo('late.png')]);
  first.unmount();
  render(<AddStoneForm onSubmit={vi.fn()} />);
  await act(async () => { pending.resolve(draft('late')); });
  expect(screen.getByText('0 / 16 photos')).toBeTruthy();
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('');
  expect(screen.queryByRole('button', { name: 'Remove photo 1' })).toBeNull();
});
