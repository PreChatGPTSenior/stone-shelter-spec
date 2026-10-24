import { expect, test, vi } from 'vitest';
import type { SearchSort } from '../../api/dto/SearchSort';
import { getMockCatalogStones } from './mockStonesApi';
import { mockStones } from '../data/stones';

vi.mock('../data/stones', () => ({
  mockStones: [
    { id: 4, name: 'Beta', stoneSize: 'SMALL', stoneType: 'GRANITE', adoptionStatus: 'AVAILABLE', admissionDate: '2020-01-02T00:00:00Z' },
    { id: 3, name: 'Alpha', stoneSize: 'LARGE', stoneType: 'BASALT', adoptionStatus: 'AVAILABLE', admissionDate: '2020-01-01T10:00:00Z' },
    { id: 2, name: 'Beta', stoneSize: 'SMALL', stoneType: 'BASALT', adoptionStatus: 'AVAILABLE', admissionDate: '2020-01-01T12:00:00+02:00' },
    { id: 1, name: 'Zulu', stoneSize: 'MEDIUM', stoneType: 'GRANITE', adoptionStatus: 'AVAILABLE', admissionDate: '2020-01-01T10:30:00+02:00' },
  ],
}));

const cases: { sort: SearchSort; ids: number[] }[] = [
  { sort: { field: 'admissionDate', direction: 'desc' }, ids: [4, 2, 3, 1] },
  { sort: { field: 'admissionDate', direction: 'asc' }, ids: [1, 2, 3, 4] },
  { sort: { field: 'name', direction: 'asc' }, ids: [3, 2, 4, 1] },
  { sort: { field: 'name', direction: 'desc' }, ids: [1, 2, 4, 3] },
  { sort: { field: 'stoneSize', direction: 'asc' }, ids: [2, 4, 1, 3] },
  { sort: { field: 'stoneSize', direction: 'desc' }, ids: [3, 1, 2, 4] },
];

test.each(cases)('sorts the complete dataset by $sort before paging, with ascending id ties', ({ sort, ids }) => {
  const originalIds = mockStones.map(stone => stone.id);
  const first = getMockCatalogStones(0, 2, sort);
  const second = getMockCatalogStones(1, 2, sort);
  expect([...first.content, ...second.content].map(stone => stone.id)).toEqual(ids);
  expect(first).toMatchObject({ page: 0, size: 2, totalElements: 4 });
  expect(second).toMatchObject({ page: 1, size: 2, totalElements: 4 });
  expect(mockStones.map(stone => stone.id)).toEqual(originalIds);
});


test('filters before sorting/paging with OR groups, AND constraints and correct totals', () => {
  const response = getMockCatalogStones(0, 1, { field: 'name', direction: 'desc' }, {
    stoneSizes: ['SMALL', 'MEDIUM'], stoneTypes: ['GRANITE', 'BASALT'],
    admissionDateTo: '2020-01-01',
  });
  expect(response.content.map(stone => stone.id)).toEqual([1]);
  expect(response.totalElements).toBe(2);
  expect(getMockCatalogStones(0, 12, undefined, { stoneSizes: [], stoneTypes: null }).totalElements).toBe(4);
  expect(getMockCatalogStones(0, 12, undefined, { stoneSizes: ['SMALL'], stoneSize: 'LARGE' }).totalElements).toBe(0);
});

test('date bounds use inclusive UTC days and open endpoints', () => {
  expect(getMockCatalogStones(0, 12, undefined, { admissionDateFrom: '2020-01-01', admissionDateTo: '2020-01-01' }).content.map(s => s.id)).toEqual([2, 3, 1]);
  expect(getMockCatalogStones(0, 12, undefined, { admissionDateFrom: '2020-01-02' }).content.map(s => s.id)).toEqual([4]);
});
