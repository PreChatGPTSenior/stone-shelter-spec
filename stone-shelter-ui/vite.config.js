import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxy = mode !== 'mock' && env.API_PROXY_TARGET
    ? Object.fromEntries(['/api', '/images'].map(path => [path, {
      target: env.API_PROXY_TARGET,
      changeOrigin: true,
      configure(proxy) {
        proxy.on('proxyReq', (proxyRequest, request) => {
          proxyRequest.removeHeader('Forwarded');
          proxyRequest.removeHeader('X-Forwarded-Host');
          proxyRequest.removeHeader('X-Forwarded-Proto');
          proxyRequest.setHeader('X-Forwarded-For', request.socket.remoteAddress ?? '');
        });
      },
    }]))
    : undefined;
  return { server: { proxy }, preview: { proxy } };
});
