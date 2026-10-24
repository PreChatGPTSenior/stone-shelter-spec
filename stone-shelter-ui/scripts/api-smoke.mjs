import assert from 'node:assert/strict';
import { File } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { createServer, preview } from 'vite';

const target = process.env.API_PROXY_TARGET;
assert(target, 'Set API_PROXY_TARGET to an isolated backend. This test creates and deletes its own stones.');
assert(['http:', 'https:'].includes(new URL(target).protocol), 'Use an HTTP(S) backend URL.');
const server = await createServer({
  root: fileURLToPath(new URL('../', import.meta.url)), mode: 'api',
  define: { 'import.meta.env.VITE_API_BASE_URL': '""' },
  server: { host: '127.0.0.1', port: 0,
    proxy: Object.fromEntries(['/api', '/images'].map(path => [path, { target, changeOrigin: true }])) },
});
const originalFetch = globalThis.fetch;
const createdIds = [];
let baseUrl;
try {
  await server.listen();
  baseUrl = server.resolvedUrls.local[0];
  globalThis.fetch = (url, options) => originalFetch(new URL(url, baseUrl), options);
  const api = await server.ssrLoadModule('/src/api/stonesApi.ts');
  const details = { name: `UI integration ${Date.now()}`, stoneType: 'BASALT', stoneSize: 'SMALL', biography: 'Created by the isolated UI transport smoke test.', adoptionStatus: 'AVAILABLE' };
  const withoutPhotos = await api.createStone({ ...details, photoUploadIds: [] });
  createdIds.push(withoutPhotos.id);
  assert.deepEqual(withoutPhotos.photos, []);
  assert(Number.isFinite(Date.parse(withoutPhotos.admissionDate)));
  assert.equal((await api.getStone(withoutPhotos.id)).name, details.name);

  const files = await Promise.all(['01.png', '02.png'].map(async name => new File([
    await readFile(new URL(`../../specs/0008-add-stone-api/stone_test_img/Basalt/${name}`, import.meta.url)),
  ], name, { type: 'image/png' })));
  const uploads = await Promise.all(files.map(api.uploadStonePhotoDraft));
  for (let index = 0; index < uploads.length; index++) {
    const preview = await originalFetch(new URL(uploads[index].url, target));
    assert.equal(preview.status, 200);
    assert.deepEqual(new Uint8Array(await preview.arrayBuffer()), new Uint8Array(await files[index].arrayBuffer()));
  }
  const withPhotos = await api.createStone({ ...details, photoUploadIds: uploads.map(upload => upload.id) });
  createdIds.push(withPhotos.id);
  assert.deepEqual(withPhotos.photos.map(photo => photo.position), [0, 1]);
  const readback = await api.getStone(withPhotos.id);
  assert.equal(readback.admissionDate, withPhotos.admissionDate);
  assert.equal(readback.photos.length, 2);
  for (let index = 0; index < readback.photos.length; index++) {
    const image = await originalFetch(new URL(readback.photos[index].url, target));
    assert.equal(image.status, 200);
    assert.deepEqual(new Uint8Array(await image.arrayBuffer()), new Uint8Array(await files[index].arrayBuffer()));
  }
  const available = await api.getCatalogStones(0, 24, { field: 'name', direction: 'asc' });
  assert(available.content.some(stone => stone.id === withPhotos.id));
  assert(available.content.some(stone => stone.id === withoutPhotos.id));
  const reservation = await api.createStoneReservation(withPhotos.id, { applicantName: 'UI smoke visitor', contactDetails: 'Test window' });
  assert.equal(reservation.stoneId, withPhotos.id);
  assert.equal((await api.getStone(withPhotos.id)).adoptionStatus, 'RESERVED');
  const afterReservation = await api.getCatalogStones(0, 24);
  assert.equal(afterReservation.totalElements, available.totalElements - 1);
  assert(!afterReservation.content.some(stone => stone.id === withPhotos.id));
  assert.equal((await api.getCatalogStones(0, 24, undefined, { adoptionStatus: 'RESERVED' })).totalElements, 0);
  await assert.rejects(api.createStoneReservation(withPhotos.id, { applicantName: 'Duplicate', contactDetails: 'Test' }), { status: 409 });
  await assert.rejects(api.uploadStonePhotoDraft(new File(['invalid image'], 'bad.png', { type: 'image/png' })), { status: 400 });
  for (const path of ['/stone-shelter/catalog', `/stone-shelter/stones/${withPhotos.id}`, '/stone-shelter/add-stone']) {
    const page = await originalFetch(new URL(path, baseUrl));
    assert.equal(page.status, 200);
    assert.match(await page.text(), /<div id="root"><\/div>/);
  }
  const previewServer = await preview({
    root: fileURLToPath(new URL('../', import.meta.url)), mode: 'api',
    preview: { host: '127.0.0.1', port: 0,
      proxy: Object.fromEntries(['/api', '/images'].map(path => [path, { target, changeOrigin: true }])) },
  });
  try {
    const previewBase = previewServer.resolvedUrls.local[0];
    const search = await originalFetch(new URL('/api/v1/stones/search', previewBase), {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    });
    assert.equal(search.status, 200);
    assert.equal((await search.json()).totalElements, afterReservation.totalElements);
    const image = await originalFetch(new URL('/images/placeholder-rock.png', previewBase));
    assert.equal(image.status, 200);
    assert.match(image.headers.get('content-type'), /^image\//);
    await image.arrayBuffer();
    const page = await originalFetch(new URL('/stone-shelter/add-stone', previewBase));
    assert.equal(page.status, 200);
    assert.match(await page.text(), /<div id="root"><\/div>/);
  } finally {
    await previewServer.close();
  }
  process.stdout.write('Real UI API adapter + Vite proxy passed: zero/photo creation, ordered image bytes, readback, server date, catalog and reservation/conflict/error flows.\n');
  process.stdout.write('Development SPA routes and preview API/image proxy passed.\n');
} finally {
  globalThis.fetch = originalFetch;
  try {
    for (const id of createdIds) {
      const response = await originalFetch(new URL(`/api/v1/stones/${id}`, baseUrl), { method: 'DELETE' });
      assert.equal(response.status, 200, `Cleanup failed for test stone ${id}`);
      assert.deepEqual(await response.json(), { id });
    }
    if (createdIds.length) process.stdout.write(`Deleted ${createdIds.length} test stones; object cleanup remains owned by the backend/isolated infrastructure.\n`);
  } finally {
    await server.close();
  }
}
