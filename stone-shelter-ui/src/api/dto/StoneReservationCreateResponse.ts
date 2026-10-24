import type { AdoptionStatus } from '../../enums/AdoptionStatus';

export interface StoneReservationCreateResponse {
  id: number;
  stoneId: number;
  adoptionStatus: AdoptionStatus;
  createdAt: string;
}
