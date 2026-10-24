import { expect, test } from 'vitest';
import { validateAdmissionDates } from './admissionDates';

test('validates calendar dates, optional bounds and date order', () => {
  for (const value of ['not-a-date', '2026-02-30', '2026-13-01']) {
    expect(validateAdmissionDates(value, '')).toBe('Enter a valid date in YYYY-MM-DD format.');
  }
  expect(validateAdmissionDates('2026-09-02', '2026-09-01')).toBe('From must be on or before To.');
  expect(validateAdmissionDates('2024-02-29', '2024-02-29')).toBeUndefined();
  expect(validateAdmissionDates('', '2026-09-01')).toBeUndefined();
  expect(validateAdmissionDates('2026-09-01', '')).toBeUndefined();
});
