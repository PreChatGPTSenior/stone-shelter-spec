import { createHttpStone, createHttpStoneReservation, getHttpCatalogStones, getHttpStone, uploadHttpStonePhotoDraft } from './httpStonesApi';
import type { StoneCreateRequest } from './dto/StoneCreateRequest';
import type { StoneReservationCreateRequest } from './dto/StoneReservationCreateRequest';
import type { SearchSort } from './dto/SearchSort';
import type { StoneSearchFilter } from './dto/StoneSearchFilter';

export async function getCatalogStones(page?: number, size?: number, sort?: SearchSort, filter?: StoneSearchFilter) {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockStonesApi')).getMockCatalogStones(page, size, sort, filter);
  return getHttpCatalogStones(page, size, sort, filter);
}

export async function getStone(id: number) {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockStonesApi')).getMockStone(id);
  return getHttpStone(id);
}

export async function createStoneReservation(id: number, request: StoneReservationCreateRequest) {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockStonesApi')).createMockStoneReservation(id, request);
  return createHttpStoneReservation(id, request);
}

export async function createStone(request: StoneCreateRequest) {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockStonesApi')).createMockStone(request);
  return createHttpStone(request);
}

export async function uploadStonePhotoDraft(file: File) {
  if (import.meta.env.MODE === 'mock') return (await import('../mocks/api/mockStonesApi')).uploadMockStonePhotoDraft(file);
  return uploadHttpStonePhotoDraft(file);
}
