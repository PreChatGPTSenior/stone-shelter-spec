import type { StoneSize } from '../../enums/StoneSize';
import type { StoneType } from '../../enums/StoneType';
import type { AdoptionStatus } from '../../enums/AdoptionStatus';

export interface StoneSearchFilter {
  stoneSizes?: StoneSize[] | null;
  stoneTypes?: StoneType[] | null;
  admissionDateFrom?: string | null;
  admissionDateTo?: string | null;
  stoneSize?: StoneSize | null;
  stoneType?: StoneType | null;
  adoptionStatus?: AdoptionStatus | null;
}
