import type { StoneCreateRequest } from '../../../api/dto/StoneCreateRequest';
import type { StoneSize } from '../../../enums/StoneSize';
import type { StoneType } from '../../../enums/StoneType';
import { stoneSizeLabels, stoneTypeLabels } from '../../../domain/stone/presentation/stoneLabels';

export interface StoneFormValues {
  name: string;
  stoneType: StoneType | '';
  stoneSize: StoneSize | '';
  biography: string;
}
export type StoneFormErrors = Partial<Record<keyof StoneFormValues, string>>;

export function initialStoneForm(): StoneFormValues {
  return { name: '', stoneType: '', stoneSize: '', biography: '' };
}

export function validateStoneForm(values: StoneFormValues): StoneFormErrors {
  const errors: StoneFormErrors = {};
  if (!values.name.trim()) errors.name = 'Enter a name.';
  else if (values.name.length > 120) errors.name = 'Use at most 120 characters.';
  if (!Object.hasOwn(stoneTypeLabels, values.stoneType)) errors.stoneType = 'Choose a stone type.';
  if (!Object.hasOwn(stoneSizeLabels, values.stoneSize)) errors.stoneSize = 'Choose a size.';
  if (values.biography.length > 2048) errors.biography = 'Use at most 2048 characters.';
  return errors;
}

export function toStoneCreateRequest(values: StoneFormValues, photoUploadIds: string[]): StoneCreateRequest {
  return {
    name: values.name, stoneType: values.stoneType as StoneType,
    stoneSize: values.stoneSize as StoneSize, biography: values.biography,
    adoptionStatus: 'AVAILABLE',
    photoUploadIds,
  };
}
