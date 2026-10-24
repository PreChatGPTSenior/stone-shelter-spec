import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { createServer, preview } from 'vite';

const target = process.env.API_PROXY_TARGET;
assert(target, 'Set API_PROXY_TARGET to a backend exposing the chatbot stub. No catalog data is changed.');
assert(['http:', 'https:'].includes(new URL(target).protocol), 'Use an HTTP(S) backend URL.');
const root = fileURLToPath(new URL('../', import.meta.url));
const expected = { text: 'Here are some stones you might like.', stones: [{ id: 1, name: 'Mars' }, { id: 3, name: 'Luna' }] };
const request = { conversationId: randomUUID(), message: 'Choose a stone', context: { stoneId: null } };
const proxy = { '/api': { target, changeOrigin: true } };
const originalFetch = globalThis.fetch;
const server = await createServer({ root, mode: 'api', define: { 'import.meta.env.VITE_API_BASE_URL': '""' },
  server: { host: '127.0.0.1', port: 0, proxy } });
try {
  await server.listen();
  const baseUrl = server.resolvedUrls.local[0];
  globalThis.fetch = (url, options) => originalFetch(new URL(url, baseUrl), options);
  const chatbotApi = await server.ssrLoadModule('/src/api/chatbotApi.ts');
  assert.deepEqual(await chatbotApi.sendChatMessage(request), expected);
  assert.deepEqual(await chatbotApi.sendChatMessage({ ...request, message: 'About this stone', context: { stoneId: 3 } }), expected);
  await assert.rejects(chatbotApi.sendChatMessage({ ...request, message: '' }), { status: 400 });
  const previewServer = await preview({ root, mode: 'api', preview: { host: '127.0.0.1', port: 0, proxy } });
  try {
    const response = await originalFetch(new URL('/api/v1/chat/messages', previewServer.resolvedUrls.local[0]), {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), expected);
  } finally {
    await previewServer.close();
  }
} finally {
  globalThis.fetch = originalFetch;
  await server.close();
}

const mockServer = await createServer({ root, mode: 'mock' });
try {
  globalThis.fetch = () => { throw new Error('Mock chatbot must not use fetch.'); };
  const chatbotApi = await mockServer.ssrLoadModule('/src/api/chatbotApi.ts');
  assert.deepEqual(await chatbotApi.sendChatMessage(request), expected);
} finally {
  globalThis.fetch = originalFetch;
  await mockServer.close();
}
process.stdout.write('Chatbot smoke passed: actual API adapter, development/preview proxy, validation and standalone mock mode. No database writes.\n');
