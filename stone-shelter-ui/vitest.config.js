import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/mocks/testSetup.ts'],
    maxWorkers: 4,
  },
});
