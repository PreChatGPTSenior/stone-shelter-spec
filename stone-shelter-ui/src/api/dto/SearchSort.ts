import type { StoneSortField } from '../../enums/StoneSortField';
import type { SortDirection } from '../../enums/SortDirection';

export interface SearchSort {
  field: StoneSortField;
  direction: SortDirection;
}
