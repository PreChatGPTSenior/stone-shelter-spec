import type { StoneSize } from '../../../enums/StoneSize';
import type { StoneType } from '../../../enums/StoneType';
import type { AdoptionStatus } from '../../../enums/AdoptionStatus';

export const stoneTypeLabels: Record<StoneType, string> = {
  BASALT: 'Basalt', GRANITE: 'Granite', OBSIDIAN: 'Obsidian', PUMICE: 'Pumice',
  LIMESTONE: 'Limestone', SANDSTONE: 'Sandstone', SHALE: 'Shale', MARBLE: 'Marble',
  GNEISS: 'Gneiss', SLATE: 'Slate',
};
export const stoneSizeLabels: Record<StoneSize, string> = {
  SMALL: 'Small', MEDIUM: 'Medium', LARGE: 'Large',
};
export const adoptionStatusLabels: Record<AdoptionStatus, string> = {
  AVAILABLE: 'Available', RESERVED: 'Reserved', ADOPTED: 'Adopted',
};
