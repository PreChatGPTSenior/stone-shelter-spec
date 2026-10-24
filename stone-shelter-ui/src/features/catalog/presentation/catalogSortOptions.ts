import type { SearchSort } from '../../../api/dto/SearchSort';
import type { CatalogSortOption } from '../../../enums/CatalogSortOption';

export const catalogSortOptions: Record<CatalogSortOption, { label: string; sort: SearchSort }> = {
  NEWEST: { label: 'Newest first', sort: { field: 'admissionDate', direction: 'desc' } },
  OLDEST: { label: 'Oldest first', sort: { field: 'admissionDate', direction: 'asc' } },
  NAME_ASC: { label: 'Name: A–Z', sort: { field: 'name', direction: 'asc' } },
  NAME_DESC: { label: 'Name: Z–A', sort: { field: 'name', direction: 'desc' } },
  SIZE_ASC: { label: 'Size: small to large', sort: { field: 'stoneSize', direction: 'asc' } },
  SIZE_DESC: { label: 'Size: large to small', sort: { field: 'stoneSize', direction: 'desc' } },
};
