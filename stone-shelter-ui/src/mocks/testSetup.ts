import { beforeEach, vi } from 'vitest';

vi.stubEnv('MODE', 'mock');
beforeEach(async () => {
  const mockStonesApi = await import('./api/mockStonesApi');
  mockStonesApi.resetMockStoneCreations();
  mockStonesApi.resetMockStoneReservations();
});
