import { afterEach, expect, test, vi } from 'vitest';
import { ApiError } from './ApiError';
import { StoneReservationError } from './StoneReservationError';
import { createStone, createStoneReservation, getCatalogStones, getStone, uploadStonePhotoDraft } from './stonesApi';
import { mockStones } from '../mocks/data/stones';

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.stubEnv('MODE', 'mock'); });

function backend(body: unknown, status = 200) {
  vi.stubEnv('MODE', 'api');
  vi.stubEnv('VITE_API_BASE_URL', 'https://backend.example/');
  const fetch = vi.fn().mockImplementation(async () => Response.json(body, { status }));
  vi.stubGlobal('fetch', fetch);
  return fetch;
}

test('API search sends every applied option and retains response pagination metadata', async () => {
  const response = { content: [], page: 2, size: 12, totalElements: 41 };
  const fetch = backend(response);
  const sort = { field: 'name', direction: 'asc' } as const;
  const filter = { stoneSizes: ['SMALL'], stoneTypes: ['BASALT'], admissionDateFrom: '2026-01-01' } as const;
  expect(await getCatalogStones(2, 12, sort, { ...filter, stoneSizes: ['SMALL'], stoneTypes: ['BASALT'] })).toEqual(response);
  expect(fetch).toHaveBeenCalledWith('https://backend.example/api/v1/stones/search', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ filter, page: 2, size: 12, sort }),
  });
});

test('ordinary development and production modes use API; mock mode reads without fetch', async () => {
  const fetch = backend({ content: [], page: 0, size: 8, totalElements: 0 });
  for (const mode of ['development', 'production', 'api']) {
    vi.stubEnv('MODE', mode);
    await getCatalogStones();
  }
  expect(fetch).toHaveBeenCalledTimes(3);
  vi.stubEnv('MODE', 'mock');
  expect((await getCatalogStones()).totalElements).toBe(30);
  expect((await getStone(1))?.name).toBe('Mars');
  expect(fetch).toHaveBeenCalledTimes(3);
});

test('details request the exact ID and only HTTP 404 becomes a missing stone', async () => {
  const stone = { ...mockStones[0], id: 7, photos: [] };
  const fetch = backend(stone);
  expect(await getStone(7)).toEqual(stone);
  expect(fetch).toHaveBeenCalledWith('https://backend.example/api/v1/stones/7', undefined);
  fetch.mockResolvedValueOnce(Response.json({ detail: 'Missing stone' }, { status: 404 }));
  expect(await getStone(999)).toBeUndefined();
  fetch.mockResolvedValueOnce(Response.json({ detail: 'Storage unavailable' }, { status: 503 }));
  await expect(getStone(7)).rejects.toMatchObject({ status: 503, message: 'Storage unavailable' });
});

test('creation sends references and no date or bytes; reservation uses its dedicated JSON route', async () => {
  const fetch = backend({ ...mockStones[0], id: 7, photos: [] }, 201);
  const request = { name: 'Basalt', stoneType: 'BASALT', stoneSize: 'SMALL', adoptionStatus: 'AVAILABLE', photoUploadIds: ['draft-id'] } as const;
  await createStone({ ...request, photoUploadIds: [...request.photoUploadIds] });
  expect(fetch.mock.calls[0]).toEqual(['https://backend.example/api/v1/stones', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }]);
  fetch.mockResolvedValueOnce(Response.json({ id: 1, stoneId: 7, adoptionStatus: 'RESERVED', createdAt: '2026-01-01T00:00:00Z' }, { status: 201 }));
  await createStoneReservation(7, { applicantName: 'Visitor', contactDetails: 'By the window' });
  expect(fetch.mock.calls[1]).toEqual(['https://backend.example/api/v1/stones/7/reservations', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ applicantName: 'Visitor', contactDetails: 'By the window' }),
  }]);
});

test('uploads the original file as multipart without setting a Content-Type boundary', async () => {
  const fetch = backend({ id: 'draft-id', url: '/photo.png', uploadedAt: '2026-01-01T00:00:00Z', expiresAt: '2026-01-02T00:00:00Z' }, 201);
  const file = new File(['image bytes'], 'stone.png', { type: 'image/png' });
  await uploadStonePhotoDraft(file);
  const [url, options] = fetch.mock.calls[0];
  expect(url).toBe('https://backend.example/api/v1/stone-photo-drafts');
  expect(options.method).toBe('POST');
  expect(options.headers).toBeUndefined();
  expect(options.body.get('file')).toBe(file);
});

test.each([400, 404, 409, 413, 415, 500, 503])('preserves ProblemDetail HTTP %i and reservation failure classification', async status => {
  backend({ detail: 'The operation was rejected.' }, status);
  await expect(createStoneReservation(7, { applicantName: 'A', contactDetails: 'B' })).rejects.toBeInstanceOf(StoneReservationError);
  await expect(uploadStonePhotoDraft(new File(['x'], 'x.png'))).rejects.toMatchObject({ status, message: 'The operation was rejected.' });
});

test('normalizes non-JSON, invalid JSON values and network failures without falling back to mocks', async () => {
  const fetch = backend({});
  fetch.mockResolvedValueOnce(new Response('<html>Unavailable</html>', { status: 502 }));
  await expect(getCatalogStones()).rejects.toMatchObject({ status: 502, message: expect.stringContaining('HTTP 502') });
  fetch.mockResolvedValueOnce(Response.json(null));
  await expect(getCatalogStones()).rejects.toBeInstanceOf(ApiError);
  fetch.mockResolvedValueOnce(Response.json({ content: [], page: 0, size: 0, totalElements: 0 }));
  await expect(getCatalogStones()).rejects.toBeInstanceOf(ApiError);
  fetch.mockRejectedValueOnce(new TypeError('Network failed'));
  await expect(getCatalogStones()).rejects.toMatchObject({ status: 0, message: expect.stringContaining('backend could not be reached') });
  expect(fetch).toHaveBeenCalledTimes(4);
});

test('uses ProblemDetail title when detail is blank and a same-origin base when unset', async () => {
  const fetch = backend({ title: 'Invalid request', detail: ' ' }, 400);
  vi.stubEnv('VITE_API_BASE_URL', '');
  await expect(getCatalogStones()).rejects.toMatchObject({ status: 400, message: 'Invalid request' });
  expect(fetch.mock.calls[0][0]).toBe('/api/v1/stones/search');
});

test('rejects malformed detail fields and gallery metadata before rendering', async () => {
  const fetch = backend({});
  for (const fields of [{ biography: 7 }, { photos: null }, { photos: [{ id: 1, url: '/photo.png' }] }, { admissionDate: 'invalid' }]) {
    fetch.mockResolvedValueOnce(Response.json({ ...mockStones[0], photos: [], ...fields }));
    await expect(getStone(1)).rejects.toMatchObject({ message: 'The backend returned an invalid response. Please try again.' });
  }
});
