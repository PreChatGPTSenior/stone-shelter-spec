import { afterEach, expect, test, vi } from 'vitest';
import { createStone, createStoneReservation, getCatalogStones, getStone, uploadStonePhotoDraft } from '../../api/stonesApi';
import type { StoneCreateRequest } from '../../api/dto/StoneCreateRequest';
import { mockStones } from '../data/stones';
import { mockStonePhotos } from '../data/stonePhotos';
import { resetMockStoneCreations, resetMockStoneReservations } from './mockStonesApi';

const request: StoneCreateRequest = {
  name: '  Moss 石  ', stoneType: 'BASALT', stoneSize: 'SMALL',
  biography: '  A stone with a story.  ', adoptionStatus: 'AVAILABLE',
};
const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jL1sAAAAASUVORK5CYII='), character => character.charCodeAt(0));

afterEach(() => {
  vi.useRealTimers();
  resetMockStoneCreations();
  resetMockStoneReservations();
});

test.each([
  { mediaType: 'image/png', bytes: png },
  { mediaType: 'image/jpeg', bytes: new Uint8Array([255, 216, 255, 224]) },
  { mediaType: 'image/webp', bytes: Uint8Array.from(atob('UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA='), character => character.charCodeAt(0)) },
  { mediaType: 'image/png', bytes: new Uint8Array(10 * 1024 * 1024).map((_, index) => png[index] ?? 0) },
])('reads selected $mediaType bytes through the draft boundary without creating a stone', async ({ mediaType, bytes }) => {
  const initialCount = (await getCatalogStones()).totalElements;
  const draft = await uploadStonePhotoDraft(new File([bytes], 'photo', { type: mediaType }));
  expect(draft.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  const [header, encoded] = draft.url.split(',');
  expect(header).toBe(`data:${mediaType};base64`);
  const decoded = atob(encoded);
  expect(decoded.length).toBe(bytes.length);
  if (bytes.length === 10 * 1024 * 1024) {
    expect(decoded.slice(0, png.length)).toBe(String.fromCharCode(...png));
    expect(decoded.slice(png.length)).toMatch(/^\0+$/);
  } else {
    expect(decoded).toBe(String.fromCharCode(...bytes));
  }
  expect(Date.parse(draft.expiresAt) - Date.parse(draft.uploadedAt)).toBe(24 * 60 * 60 * 1000);
  expect((await getCatalogStones()).totalElements).toBe(initialCount);
});

test.each([undefined, null, []])('creates without photos for reference list %j and returns independent details', async photoUploadIds => {
  const created = await createStone({ ...request, photoUploadIds });
  expect(created).toEqual({ ...request, id: created.id, admissionDate: created.admissionDate, photo: null, photos: [] });
  expect(created.id).toBeGreaterThan(Math.max(...mockStones.map(stone => stone.id)));
  expect((await getStone(created.id))).toEqual(created);
  expect((await getCatalogStones()).totalElements).toBe(mockStones.length + 1);
  expect(created).not.toHaveProperty('photoUploadIds');
});

test('allows duplicate names and allocates distinct IDs for concurrent successful calls', async () => {
  const created = await Promise.all(Array.from({ length: 3 }, () => createStone({ ...request, name: mockStones[0].name })));
  expect(new Set(created.map(stone => stone.id)).size).toBe(3);
  for (const stone of created) {
    expect(stone.name).toBe(mockStones[0].name);
    expect(stone.id).toBeGreaterThan(Math.max(...mockStones.map(fixture => fixture.id)));
    expect((await getStone(stone.id))).toEqual(stone);
  }
});

test.each([1, 16])('creates an ordered gallery from %i uploaded references without reuploading files', async count => {
  const drafts = await Promise.all(Array.from({ length: count }, (_, index) =>
    uploadStonePhotoDraft(new File([`photo-${index}`], `${index}.png`, { type: 'image/png' }))));
  const ordered = [...drafts].reverse();
  const created = await createStone({ ...request, photoUploadIds: ordered.map(draft => draft.id) });
  expect(created.photos).toHaveLength(count);
  expect(created.photos.map(photo => photo.url)).toEqual(ordered.map(draft => draft.url));
  expect(created.photos.map(photo => photo.position)).toEqual(Array.from({ length: count }, (_, index) => index));
  expect(created.photo).toBe(ordered[0].url);
  expect(new Set(created.photos.map(photo => photo.id)).size).toBe(count);
  for (const photo of created.photos) {
    expect(photo.id).toBeGreaterThan(Math.max(...Object.values(mockStonePhotos).flat().map(fixture => fixture.id)));
    expect(Number.isNaN(Date.parse(photo.addedAt))).toBe(false);
  }
  expect((await getStone(created.id))).toEqual(created);
  const next = await createStone({ ...request, photoUploadIds: [drafts[0].id] });
  expect(next.photos[0].id).toBeGreaterThan(Math.max(...created.photos.map(photo => photo.id)));
});

test('includes additions in filtering, sorting, paging and totals without expanding search items', async () => {
  const beta = await createStone({ ...request, name: 'Beta' });
  const alpha = await createStone({ ...request, name: 'Alpha' });
  await createStone({ ...request, stoneType: 'GRANITE', stoneSize: 'LARGE' });
  const filter = {
    stoneTypes: ['BASALT'] as const, stoneSizes: ['SMALL'] as const,
    admissionDateFrom: alpha.admissionDate.slice(0, 10), admissionDateTo: alpha.admissionDate.slice(0, 10),
  };
  const first = (await getCatalogStones(0, 1, { field: 'name', direction: 'asc' }, {
    ...filter, stoneTypes: [...filter.stoneTypes], stoneSizes: [...filter.stoneSizes],
  }));
  const second = (await getCatalogStones(1, 1, { field: 'name', direction: 'asc' }, {
    ...filter, stoneTypes: [...filter.stoneTypes], stoneSizes: [...filter.stoneSizes],
  }));
  expect(first).toMatchObject({ page: 0, size: 1, totalElements: 2 });
  expect(second).toMatchObject({ page: 1, size: 1, totalElements: 2 });
  expect([...first.content, ...second.content].map(stone => stone.id)).toEqual([alpha.id, beta.id]);
  expect(first.content[0]).not.toHaveProperty('photos');
  expect(first.content[0]).not.toHaveProperty('photoUploadIds');
  expect((await getCatalogStones()).totalElements).toBe(mockStones.length + 3);
});

test('protects stored drafts and galleries from response/request mutation and keeps cover readback consistent', async () => {
  const first = await uploadStonePhotoDraft(new File([png], 'first.png', { type: 'image/png' }));
  const second = await uploadStonePhotoDraft(new File([png, 'second'], 'second.png', { type: 'image/png' }));
  const cover = first.url;
  first.url = '/mutated-upload.png';
  const photoUploadIds = [first.id, second.id];
  const created = await createStone({ ...request, photoUploadIds });
  const id = created.id;
  photoUploadIds.reverse();
  created.name = 'Changed response';
  created.photos.reverse();
  created.photos[0].url = '/mutated-created.png';
  const detail = (await getStone(id))!;
  expect(detail).toMatchObject({ name: request.name, photo: cover });
  expect(detail.photos.map(photo => photo.position)).toEqual([0, 1]);
  const galleryIds = detail.photos.map(photo => photo.id);
  detail.photos[0].url = '/mutated-details.png';
  detail.photos.pop();
  const catalog = (await getCatalogStones(0, 24, undefined, { admissionDateFrom: created.admissionDate.slice(0, 10) }));
  expect(catalog.content[0]).toMatchObject({ id, name: request.name, photo: cover });
  catalog.content[0].name = 'Changed catalog';
  expect((await getStone(id))?.photos.map(photo => photo.id)).toEqual(galleryIds);
  expect((await getStone(id))?.photos[0].url).toBe(cover);
  expect((await getStone(id))?.name).toBe(request.name);
});

test('applies reservations to newly created stones and leaves both fixture datasets unchanged', async () => {
  const initialStones = JSON.stringify(mockStones);
  const initialPhotos = JSON.stringify(mockStonePhotos);
  const draft = await uploadStonePhotoDraft(new File([png], 'stone.png', { type: 'image/png' }));
  const created = await createStone({ ...request, photoUploadIds: [draft.id] });
  await createStoneReservation(created.id, { applicantName: 'Visitor', contactDetails: 'Window 2' });
  expect((await getStone(created.id))).toMatchObject({ adoptionStatus: 'RESERVED', photos: created.photos });
  expect((await getCatalogStones(0, 24, undefined, { adoptionStatus: 'RESERVED' })).content)
    .toEqual([]);
  expect((await getCatalogStones(0, 24, undefined, { adoptionStatus: 'AVAILABLE' })).content.some(stone => stone.id === created.id)).toBe(false);
  await expect(createStoneReservation(created.id, { applicantName: 'Other', contactDetails: 'Window 3' }))
    .rejects.toMatchObject({ status: 409 });
  expect(JSON.stringify(mockStones)).toBe(initialStones);
  expect(JSON.stringify(mockStonePhotos)).toBe(initialPhotos);
});

test('resets new runtime state and its reservations without changing fixture reservation state', async () => {
  await createStoneReservation(1, { applicantName: 'Fixture visitor', contactDetails: 'Here' });
  const created = await createStone(request);
  await createStoneReservation(created.id, { applicantName: 'New visitor', contactDetails: 'There' });
  resetMockStoneCreations();
  expect((await getStone(created.id))).toBeUndefined();
  expect((await getCatalogStones()).totalElements).toBe(mockStones.length - 1);
  expect((await getStone(1))?.adoptionStatus).toBe('RESERVED');
  const fresh = await createStone(request);
  expect(fresh.id).toBe(created.id);
  expect((await getStone(fresh.id))?.adoptionStatus).toBe('AVAILABLE');
});


test('mock creation assigns its current UTC instant and preserves it in detail and search readback', async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-01-01T23:30:12.345-08:00'));
  const legacyRequest = { ...request, admissionDate: '2000-01-01T00:00:00Z' };
  const created = await createStone(legacyRequest);
  expect(created.admissionDate).toBe('2026-01-02T07:30:12.345Z');
  expect((await getStone(created.id))?.admissionDate).toBe(created.admissionDate);
  expect((await getCatalogStones(0, 24, undefined, {
    admissionDateFrom: '2026-01-02', admissionDateTo: '2026-01-02',
  })).content.find(stone => stone.id === created.id)?.admissionDate).toBe(created.admissionDate);
});
