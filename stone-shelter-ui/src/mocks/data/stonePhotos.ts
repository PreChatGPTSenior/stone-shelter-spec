import type { StonePhotoResponse } from '../../api/dto/StonePhotoResponse';
import { mockStones } from './stones';

// Keep the existing identities and illustrate empty, single and overflowing
// galleries. Every image reuses the supplied catalog placeholder.
export const mockStonePhotos: Record<number, StonePhotoResponse[]> = Object.fromEntries(
  mockStones.map(stone => [stone.id, Array.from(
    { length: stone.id % 3 === 0 ? 0 : stone.id % 3 === 2 ? 1 : 6 },
    (_, position) => ({
      id: stone.id * 10 + position + 1,
      url: '/placeholder-rock.png',
      addedAt: new Date(Date.parse(stone.admissionDate) + position * 1000).toISOString(),
      position,
    }),
  )]),
);
