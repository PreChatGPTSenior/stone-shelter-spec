import type { AdoptionStatus } from '../../enums/AdoptionStatus';
import type { StoneSize } from '../../enums/StoneSize';
import type { StoneType } from '../../enums/StoneType';
import type { StonePhotoResponse } from './StonePhotoResponse';

export interface StoneResponse {
  id: number;
  name: string;
  photo?: string | null;
  stoneType: StoneType;
  biography?: string | null;
  adoptionStatus: AdoptionStatus;
  admissionDate: string;
  stoneSize: StoneSize;
  photos: StonePhotoResponse[];
}
