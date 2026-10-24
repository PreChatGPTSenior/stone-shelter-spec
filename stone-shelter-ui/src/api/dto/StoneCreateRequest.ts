import type { AdoptionStatus } from '../../enums/AdoptionStatus';
import type { StoneSize } from '../../enums/StoneSize';
import type { StoneType } from '../../enums/StoneType';

export interface StoneCreateRequest {
  name: string;
  photo?: string | null;
  stoneType: StoneType;
  biography?: string | null;
  adoptionStatus: AdoptionStatus;
  stoneSize: StoneSize;
  photoUploadIds?: string[] | null;
}
