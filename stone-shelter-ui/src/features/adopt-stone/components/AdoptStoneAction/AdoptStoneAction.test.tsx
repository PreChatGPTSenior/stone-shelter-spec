import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import type { StoneResponse } from '../../../../api/dto/StoneResponse';
import type { AdoptionStatus } from '../../../../enums/AdoptionStatus';
import { createStoneReservation } from '../../../../api/stonesApi';
import { AdoptStoneAction } from './AdoptStoneAction';

const stone: StoneResponse = {
  id: 7, name: 'Willow', stoneType: 'SHALE', stoneSize: 'SMALL', adoptionStatus: 'AVAILABLE',
  admissionDate: '2026-09-01T10:00:00Z', photos: [],
};
vi.mock('../../../../api/stonesApi', () => ({ createStoneReservation: vi.fn() }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.resetAllMocks(); });

function open(stoneResponse = stone) {
  vi.mocked(createStoneReservation).mockImplementation(() => new Promise(() => {}));
  render(<AdoptStoneAction stone={stoneResponse} onReserved={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' }));
  return vi.mocked(createStoneReservation);
}

test('opens the precise stone summary above labelled fields without submitting', () => {
  const submit = open();
  const dialog = screen.getByRole('dialog', { name: 'Adopt this stone' });
  expect(dialog.getAttribute('aria-modal')).toBe('true');
  expect(within(dialog).getByRole('heading', { name: 'Willow' })).toBeTruthy();
  expect(within(dialog).getByText('ID 7')).toBeTruthy();
  expect(within(dialog).getByLabelText('Your name').getAttribute('type')).toBe('text');
  expect(within(dialog).getByLabelText('Contact details').tagName).toBe('TEXTAREA');
  expect(submit).not.toHaveBeenCalled();
});

test.each(['RESERVED', 'ADOPTED'] as AdoptionStatus[])('prevents opening an unavailable %s stone', adoptionStatus => {
  const submit = vi.mocked(createStoneReservation);
  render(<AdoptStoneAction stone={{ ...stone, adoptionStatus }} onReserved={vi.fn()} />);
  const action = screen.getByRole('button', { name: 'Adopt this stone' });
  expect((action as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(action);
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(submit).not.toHaveBeenCalled();
});

test('validates whitespace without clearing the other field and accepts arbitrary text unchanged', () => {
  const submit = open();
  fireEvent.change(screen.getByLabelText('Your name'), { target: { value: '   ' } });
  fireEvent.change(screen.getByLabelText('Contact details'), { target: { value: 'Find me at the window' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
  expect(screen.getByRole('alert').textContent).toBe('Enter your name.');
  expect((screen.getByLabelText('Contact details') as HTMLTextAreaElement).value).toBe('Find me at the window');
  expect(submit).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Your name'), { target: { value: '  Visitor 石  ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
  expect(submit).toHaveBeenCalledWith(7, { applicantName: '  Visitor 石  ', contactDetails: 'Find me at the window' });
});

test('reports both missing fields and does not submit', () => {
  const submit = open();
  fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
  expect(screen.getAllByRole('alert')).toHaveLength(2);
  expect(screen.getByLabelText('Contact details').getAttribute('aria-invalid')).toBe('true');
  expect(submit).not.toHaveBeenCalled();
});

test('contains focus, makes the background inert, and restores focus/scroll on Escape', () => {
  open();
  expect(document.activeElement).toBe(screen.getByLabelText('Your name'));
  expect(document.body.style.overflow).toBe('hidden');
  expect((document.body.firstElementChild as HTMLElement).inert).toBe(true);
  const close = screen.getByRole('button', { name: 'Close adoption dialog' });
  close.focus();
  fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Submit application' }));
  fireEvent.keyDown(document, { key: 'Tab' });
  expect(document.activeElement).toBe(close);
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(document.body.style.overflow).toBe('');
  expect((document.body.firstElementChild as HTMLElement).inert).toBe(false);
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Adopt this stone' }));
});

test('closing and reopening discards applicant data without submitting', () => {
  const submit = open();
  fireEvent.change(screen.getByLabelText('Your name'), { target: { value: 'Unsaved' } });
  fireEvent.click(screen.getByRole('button', { name: 'Close adoption dialog' }));
  fireEvent.click(screen.getByRole('button', { name: 'Adopt this stone' }));
  expect((screen.getByLabelText('Your name') as HTMLInputElement).value).toBe('');
  expect(submit).not.toHaveBeenCalled();
});

test('uses first gallery image before legacy cover and falls back for broken images', () => {
  open({ ...stone, photo: '/legacy.png', photos: [
    { id: 1, url: '/first.png', position: 0, addedAt: '2026-09-01T00:00:00Z' },
    { id: 2, url: '/second.png', position: 1, addedAt: '2026-09-02T00:00:00Z' },
  ] });
  const photo = screen.getByRole('img', { name: 'Photo of Willow' });
  expect(photo.getAttribute('src')).toBe('/first.png');
  fireEvent.error(photo);
  expect(photo.getAttribute('src')).toBe('/placeholder-rock.png');
  fireEvent.error(photo);
  expect(photo.getAttribute('src')).toBe('/placeholder-rock.png');
});


test.each([['/legacy.png', '/legacy.png'], [null, '/placeholder-rock.png']] as const)('uses %s when no gallery exists', (photo, expected) => {
  open({ ...stone, photo });
  expect(screen.getByRole('img', { name: 'Photo of Willow' }).getAttribute('src')).toBe(expected);
});
