import { afterEach, expect, test } from 'vitest';
import { createStoneReservation, getCatalogStones, getStone } from '../../api/stonesApi';
import { StoneReservationError } from '../../api/StoneReservationError';
import { resetMockStoneReservations } from './mockStonesApi';
import { mockStones } from '../data/stones';

const request = { applicantName: '  Visitor 石  ', contactDetails: 'find me by the window ' + 'x'.repeat(3000) };
afterEach(resetMockStoneReservations);

test('reserves the precise stone through the boundary and projects status without mutating fixtures', async () => {
  const initial = JSON.stringify(mockStones);
  const response = await createStoneReservation(1, request);
  expect(response).toMatchObject({ stoneId: 1, adoptionStatus: 'RESERVED' });
  expect(response.id).toBeGreaterThan(0);
  expect(Number.isNaN(Date.parse(response.createdAt))).toBe(false);
  expect((await getStone(1))?.adoptionStatus).toBe('RESERVED');
  expect(await getCatalogStones(0, 24, undefined, { adoptionStatus: 'RESERVED' })).toMatchObject({ content: [], totalElements: 0 });
  expect((await getCatalogStones()).totalElements).toBe(29);
  expect((await getCatalogStones(0, 24, undefined, { adoptionStatus: 'AVAILABLE' })).content.some(stone => stone.id === 1)).toBe(false);
  expect(JSON.stringify(mockStones)).toBe(initial);
  expect((await getStone(1))).not.toHaveProperty('applicantName');
  response.adoptionStatus = 'ADOPTED';
  expect((await getStone(1))?.adoptionStatus).toBe('RESERVED');
});

test('permits the same applicant for different stones but refuses repeat or concurrent reservations', async () => {
  const results = await Promise.allSettled([
    createStoneReservation(1, request), createStoneReservation(1, request), createStoneReservation(1, { applicantName: 'Another', contactDetails: 'somewhere' }),
  ]);
  expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1);
  for (const result of results) {
    if (result.status === 'rejected') expect(result.reason).toMatchObject({ status: 409 });
  }
  expect(await createStoneReservation(2, request)).toMatchObject({ stoneId: 2, adoptionStatus: 'RESERVED' });
});

test.each(['applicantName', 'contactDetails'] as const)('rejects blank %s without writing then permits valid retry', async field => {
  for (const value of ['', ' ', '\t\n']) {
    await expect(createStoneReservation(1, { ...request, [field]: value })).rejects.toMatchObject({ status: 400 });
    expect((await getStone(1))?.adoptionStatus).toBe('AVAILABLE');
  }
  await expect(createStoneReservation(1, request)).resolves.toMatchObject({ stoneId: 1 });
});

test('rejects unknown and already unavailable stones', async () => {
  await expect(createStoneReservation(999, request)).rejects.toMatchObject({ status: 404 });
  for (const stone of mockStones.filter(stone => stone.adoptionStatus !== 'AVAILABLE')) {
    await expect(createStoneReservation(stone.id, request)).rejects.toMatchObject({ status: 409 });
    expect((await getStone(stone.id))?.adoptionStatus).toBe(stone.adoptionStatus);
  }
  await expect(createStoneReservation(NaN, request)).rejects.toBeInstanceOf(StoneReservationError);
});
