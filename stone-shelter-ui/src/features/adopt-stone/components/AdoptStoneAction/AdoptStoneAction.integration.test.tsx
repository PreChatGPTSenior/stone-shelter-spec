import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from '../../../../App/App';
import { StoneDetailsPage } from '../../../stone-details/components/StoneDetailsPage/StoneDetailsPage';
import * as stonesApi from '../../../../api/stonesApi';
import { StoneReservationError } from '../../../../api/StoneReservationError';
import type { StoneReservationCreateResponse } from '../../../../api/dto/StoneReservationCreateResponse';
import { resetMockStoneReservations } from '../../../../mocks/api/mockStonesApi';

beforeEach(() => {
  resetMockStoneReservations();
  window.history.replaceState(null, '', '/stone-shelter/stones/1');
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); resetMockStoneReservations(); });

function openAndFill() {
  fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' }));
  fireEvent.change(screen.getByLabelText('Your name'), { target: { value: '  Visitor 石  ' } });
  fireEvent.change(screen.getByLabelText('Contact details'), { target: { value: 'Find me by the window' } });
}

const successText = 'Application submitted. Please wait for us to contact you.';

test('submits selected identity, keeps the same modal and updates status across navigation', async () => {
  const submit = vi.spyOn(stonesApi, 'createStoneReservation');
  await act(async () => { render(<App />); });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'View photo 3 of Mars' })); });
  openAndFill();
  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByRole('img').getAttribute('src')).toBe('/placeholder-rock.png');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(within(dialog).getByRole('status').textContent).toBe(successText));
  expect(screen.getByRole('dialog')).toBe(dialog);
  expect(submit).toHaveBeenCalledWith(1, { applicantName: '  Visitor 石  ', contactDetails: 'Find me by the window' });
  expect(window.location.pathname).toBe('/stone-shelter/stones/1');
  expect(dialog.querySelector('.adopt-stone-check')).toBeTruthy();
  expect(screen.queryByLabelText('Your name')).toBeNull();
  expect(screen.getByText('Reserved')).toBeTruthy();
  expect((await stonesApi.getStone(1))?.adoptionStatus).toBe('RESERVED');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Close adoption dialog' })); });
  expect((screen.getByRole('button', { name: 'Adopt this stone' }) as HTMLButtonElement).disabled).toBe(true);
  expect(document.activeElement).toBe(screen.getByRole('group', { name: 'Adoption action' }));
  await act(async () => { fireEvent.click(screen.getByRole('link', { name: 'Back to catalog' })); });
  await act(async () => { fireEvent.change(screen.getByLabelText('Sort stones'), { target: { value: 'OLDEST' } }); });
  expect(screen.queryByRole('link', { name: 'View details for Mars' })).toBeNull();
  expect(screen.getByText((_, element) => element?.tagName === 'P' && element.textContent === 'Found 29 stones')).toBeTruthy();
  window.history.back();
  await screen.findByRole('main', { name: 'Mars' });
  expect(screen.getByText('Reserved')).toBeTruthy();
  expect((screen.getByRole('button', { name: 'Adopt this stone' }) as HTMLButtonElement).disabled).toBe(true);
});

test('retains both arbitrary fields after failure and succeeds on manual retry', async () => {
  const submit = vi.spyOn(stonesApi, 'createStoneReservation').mockRejectedValueOnce(new Error('temporary failure'));
  await act(async () => { render(<App />); });
  openAndFill();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Please try again'));
  expect((screen.getByLabelText('Your name') as HTMLInputElement).value).toBe('  Visitor 石  ');
  expect((screen.getByLabelText('Contact details') as HTMLTextAreaElement).value).toBe('Find me by the window');
  expect(screen.queryByText(successText)).toBeNull();
  expect((await stonesApi.getStone(1))?.adoptionStatus).toBe('AVAILABLE');
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(screen.getByRole('status').textContent).toBe(successText));
  expect(submit).toHaveBeenCalledTimes(2);
});

test('prevents repeat submissions while pending including programmatic submit events', async () => {
  let resolve!: (response: StoneReservationCreateResponse) => void;
  const submit = vi.spyOn(stonesApi, 'createStoneReservation').mockImplementation(() => new Promise(result => { resolve = result; }));
  await act(async () => { render(<App />); });
  openAndFill();
  const form = screen.getByLabelText('Your name').closest('form')!;
  await act(async () => { fireEvent.submit(form); });
  expect(screen.getByRole('status').textContent).toBe('Sending your application…');
  expect((screen.getByRole('button', { name: 'Submitting…' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => { fireEvent.submit(form); });
  await act(async () => { fireEvent.submit(form); });
  expect(submit).toHaveBeenCalledTimes(1);
  await act(async () => resolve({ id: 1, stoneId: 1, adoptionStatus: 'RESERVED', createdAt: new Date().toISOString() }));
  expect(screen.getByRole('status').textContent).toBe(successText);
});

test.each([404, 409])('rejects stale eligibility with %i and prevents a misleading retry', async status => {
  const submit = vi.spyOn(stonesApi, 'createStoneReservation').mockRejectedValue(new StoneReservationError(status, 'This stone is unavailable.'));
  await act(async () => { render(<App />); });
  openAndFill();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('This stone is unavailable.'));
  expect(screen.queryByText(successText)).toBeNull();
  expect((screen.getByRole('button', { name: 'Submit application' }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => { fireEvent.submit(screen.getByLabelText('Your name').closest('form')!); });
  expect(submit).toHaveBeenCalledTimes(1);
});

test('rechecks availability at submission after another visitor reserved the stone', async () => {
  await act(async () => { render(<App />); });
  openAndFill();
  await stonesApi.createStoneReservation(1, { applicantName: 'Other visitor', contactDetails: 'elsewhere' });
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Submit application' })); });
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('no longer available'));
  expect(screen.queryByText(successText)).toBeNull();
});

test('changes selected stone without retaining applicant data or previous success state', async () => {
  const view = await act(async () => render(<StoneDetailsPage id={1} />));
  openAndFill();
  await act(async () => { view.rerender(<StoneDetailsPage id={2} />); });
  expect(screen.queryByRole('dialog')).toBeNull();
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' })); });
  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByRole('heading', { name: 'Olivia' })).toBeTruthy();
  expect((screen.getByLabelText('Your name') as HTMLInputElement).value).toBe('');
  expect((screen.getByLabelText('Contact details') as HTMLTextAreaElement).value).toBe('');
});
