import { expect, test } from 'vitest';
import { initialStoneForm, toStoneCreateRequest, validateStoneForm } from './stoneForm';

test('creation form and valid request omit the server-owned admission date', () => {
  expect(initialStoneForm()).not.toHaveProperty('admissionDate');
  const values = { ...initialStoneForm(), name: 'Stone', stoneType: 'BASALT' as const, stoneSize: 'SMALL' as const };
  expect(validateStoneForm(values)).toEqual({});
  expect(toStoneCreateRequest(values, [])).toEqual({
    name: 'Stone', stoneType: 'BASALT', stoneSize: 'SMALL', biography: '',
    adoptionStatus: 'AVAILABLE', photoUploadIds: [],
  });
});
