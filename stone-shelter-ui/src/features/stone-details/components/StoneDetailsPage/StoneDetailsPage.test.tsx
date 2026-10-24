import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import type { StoneResponse } from '../../../../api/dto/StoneResponse';
import type { AdoptionStatus } from '../../../../enums/AdoptionStatus';
import { getStone } from '../../../../api/stonesApi';
import { StoneDetailsPage } from './StoneDetailsPage';
import { ApiError } from '../../../../api/ApiError';

vi.mock('../../../../api/stonesApi', () => ({ getStone: vi.fn(), createStoneReservation: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

const stone: StoneResponse = {
  id: 7, name: 'Willow', stoneType: 'SHALE', stoneSize: 'SMALL',
  adoptionStatus: 'AVAILABLE', admissionDate: '2026-09-02T00:30:00+02:00',
  biography: 'A curious traveler with many stories.\n\nA second paragraph that remains fully readable.',
  photos: [],
};

test('shows contract-backed characteristics, UTC date and complete biography with its paragraph breaks', async () => {
  vi.mocked(getStone).mockResolvedValue(stone);
  await act(async () => { render(<StoneDetailsPage id={7} />); });
  const main = screen.getByRole('main', { name: 'Willow' });
  expect(within(main).getByRole('heading', { name: 'Willow' })).toBeTruthy();
  expect(screen.getByText('ID 7')).toBeTruthy();
  expect(screen.getByText('September 1, 2026')).toBeTruthy();
  expect(screen.getByText('Shale')).toBeTruthy();
  expect(screen.getByText('Small')).toBeTruthy();
  expect(document.querySelector('.stone-details-biography p')?.textContent).toBe(stone.biography);
  expect(screen.getByRole('button', { name: 'Adopt this stone' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: /favorite|chat|upload/i })).toBeNull();
  expect(screen.queryByText(/weight|color|location|centimeters|found in nature/i)).toBeNull();
});

test('shows loading, recoverable backend failure and a manually retried missing stone as separate states', async () => {
  let reject!: (failure: Error) => void;
  vi.mocked(getStone).mockReturnValueOnce(new Promise((_, fail) => { reject = fail; })).mockResolvedValueOnce(undefined);
  render(<StoneDetailsPage id={7} />);
  expect(screen.getByRole('status').textContent).toBe('Loading stone…');
  expect(screen.queryByText('Stone not found')).toBeNull();
  await act(async () => reject(new ApiError(503, 'Photo storage unavailable.')));
  expect(screen.getByRole('alert').textContent).toBe('Photo storage unavailable.');
  expect(screen.queryByText('Stone not found')).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry stone' })); });
  expect(screen.getByRole('heading', { name: 'Stone not found' })).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
  expect(getStone).toHaveBeenCalledTimes(2);
});

test('ignores obsolete detail results after switching IDs, including a late failure', async () => {
  let resolve!: (stone: StoneResponse) => void;
  let reject!: (failure: Error) => void;
  vi.mocked(getStone).mockReturnValueOnce(new Promise(done => { resolve = done; }))
    .mockResolvedValueOnce({ ...stone, id: 8, name: 'Current stone' })
    .mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }))
    .mockResolvedValueOnce({ ...stone, id: 9, name: 'Latest stone' });
  const view = render(<StoneDetailsPage id={7} />);
  await act(async () => { view.rerender(<StoneDetailsPage id={8} />); });
  await act(async () => resolve(stone));
  expect(screen.getByRole('heading', { name: 'Current stone' })).toBeTruthy();
  expect(screen.queryByRole('heading', { name: 'Willow' })).toBeNull();
  await act(async () => { view.rerender(<StoneDetailsPage id={10} />); });
  expect(screen.queryByText('Current stone')).toBeNull();
  await act(async () => { view.rerender(<StoneDetailsPage id={9} />); });
  await act(async () => reject(new ApiError(500, 'Obsolete failure')));
  expect(screen.getByRole('heading', { name: 'Latest stone' })).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
});

test.each([
  ['AVAILABLE', 'Available'], ['RESERVED', 'Reserved'], ['ADOPTED', 'Adopted'],
] as [AdoptionStatus, string][])('renders the actual %s status without hiding its stone', async (adoptionStatus, label) => {
  vi.mocked(getStone).mockResolvedValue({ ...stone, adoptionStatus, biography: '  ' });
  await act(async () => { render(<StoneDetailsPage id={7} />); });
  expect(screen.getByText(label)).toBeTruthy();
  expect(screen.getByText('This stone’s story is coming soon.')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Back to catalog' }).getAttribute('href')).toBe('/stone-shelter/catalog');
});

test('accepts explicit null legacy photo and biography from the backend without fabricating gallery entries', async () => {
  vi.mocked(getStone).mockResolvedValue({ ...stone, photo: null, biography: null });
  await act(async () => { render(<StoneDetailsPage id={7} />); });
  expect(screen.getByRole('img', { name: 'Photo coming soon for Willow' }).getAttribute('src')).toBe('/placeholder-rock.png');
  expect(screen.getByText('This stone’s story is coming soon.')).toBeTruthy();
  expect(screen.queryByRole('group', { name: 'Photo thumbnails' })).toBeNull();
  expect(screen.queryByRole('button', { name: /photos/ })).toBeNull();
});
