import type { StoneSearchResponse } from './StoneSearchResponse';

export interface StonesSearchResponse {
  content: StoneSearchResponse[];
  page: number;
  size: number;
  totalElements: number;
}
