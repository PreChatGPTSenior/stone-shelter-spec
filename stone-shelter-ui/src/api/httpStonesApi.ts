import { ApiError } from './ApiError';
import { StoneReservationError } from './StoneReservationError';
import { jsonRequest, requestJson } from './httpClient';
import type { SearchSort } from './dto/SearchSort';
import type { StoneSearchFilter } from './dto/StoneSearchFilter';
import type { StonesSearchResponse } from './dto/StonesSearchResponse';
import type { StoneResponse } from './dto/StoneResponse';
import type { StoneCreateRequest } from './dto/StoneCreateRequest';
import type { StoneCreateResponse } from './dto/StoneCreateResponse';
import type { StonePhotoDraftUploadResponse } from './dto/StonePhotoDraftUploadResponse';
import type { StoneReservationCreateRequest } from './dto/StoneReservationCreateRequest';
import type { StoneReservationCreateResponse } from './dto/StoneReservationCreateResponse';

export function getHttpCatalogStones(page = 0, size = 8,
  sort: SearchSort = { field: 'admissionDate', direction: 'desc' }, filter: StoneSearchFilter = {}) {
  return requestJson<StonesSearchResponse>('/api/v1/stones/search', jsonRequest({ filter, page, size, sort }),
    response => Array.isArray(response.content) && response.content.every(isStoneSummary)
      && Number.isInteger(response.page) && response.page >= 0
      && Number.isInteger(response.size) && response.size > 0
      && Number.isInteger(response.totalElements) && response.totalElements >= 0);
}

export async function getHttpStone(id: number): Promise<StoneResponse | undefined> {
  try {
    return await requestJson<StoneResponse>(`/api/v1/stones/${id}`, undefined, isStone);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
}

export function createHttpStone(request: StoneCreateRequest) {
  return requestJson<StoneCreateResponse>('/api/v1/stones', jsonRequest(request), isStone);
}

export function uploadHttpStonePhotoDraft(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestJson<StonePhotoDraftUploadResponse>('/api/v1/stone-photo-drafts', { method: 'POST', body: formData },
    response => typeof response.id === 'string' && typeof response.url === 'string'
      && Number.isFinite(Date.parse(response.uploadedAt)) && Number.isFinite(Date.parse(response.expiresAt)));
}

export async function createHttpStoneReservation(id: number, request: StoneReservationCreateRequest) {
  try {
    return await requestJson<StoneReservationCreateResponse>(`/api/v1/stones/${id}/reservations`, jsonRequest(request),
      response => Number.isSafeInteger(response.id) && response.stoneId === id
        && response.adoptionStatus === 'RESERVED' && Number.isFinite(Date.parse(response.createdAt)));
  } catch (error) {
    if (error instanceof ApiError) throw new StoneReservationError(error.status, error.message);
    throw error;
  }
}

function isStoneSummary(stone: Omit<StoneResponse, 'photos'>) {
  return Boolean(stone) && Number.isSafeInteger(stone.id) && stone.id > 0
    && typeof stone.name === 'string' && typeof stone.stoneType === 'string'
    && typeof stone.stoneSize === 'string' && typeof stone.adoptionStatus === 'string'
    && (stone.biography == null || typeof stone.biography === 'string')
    && (stone.photo == null || typeof stone.photo === 'string')
    && Number.isFinite(Date.parse(stone.admissionDate));
}

function isStone(stone: StoneResponse) {
  return isStoneSummary(stone) && Array.isArray(stone.photos)
    && stone.photos.every(photo => Boolean(photo) && Number.isSafeInteger(photo.id)
      && typeof photo.url === 'string' && Number.isInteger(photo.position)
      && Number.isFinite(Date.parse(photo.addedAt)));
}
