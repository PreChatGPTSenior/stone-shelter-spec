import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { AddStoneForm } from './AddStoneForm';

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

function fillDetails(name = '  Мрамор ✨  ') {
  fireEvent.change(screen.getByLabelText('Name *'), { target: { value: name } });
  fireEvent.change(screen.getByLabelText('Stone type *'), { target: { value: 'MARBLE' } });
  fireEvent.change(screen.getByLabelText('Size *'), { target: { value: 'SMALL' } });
}

test('fresh details have labelled empty choices, no admission-date input and 16 empty photo slots above the chooser', () => {
  render(<AddStoneForm onSubmit={vi.fn()} />);
  expect((screen.getByLabelText('Name *') as HTMLInputElement).value).toBe('');
  expect((screen.getByLabelText('Stone type *') as HTMLSelectElement).value).toBe('');
  expect((screen.getByLabelText('Size *') as HTMLSelectElement).value).toBe('');
  expect(screen.queryByLabelText('Admission date *')).toBeNull();
  expect((screen.getByLabelText('Biography (optional)') as HTMLTextAreaElement).value).toBe('');
  const grid = screen.getByRole('list', { name: 'Photo slots' });
  const emptySlots = within(grid).getAllByRole('listitem', { name: /Empty photo slot/ });
  expect(emptySlots).toHaveLength(16);
  for (const slot of emptySlots) expect(slot.textContent).toBe('');
  expect(within(grid).queryAllByRole('img')).toHaveLength(0);
  expect(screen.queryByAltText('Stone preview')).toBeNull();
  expect(screen.getAllByRole('list')).toHaveLength(1);
  expect(grid.compareDocumentPosition(screen.getByLabelText('Choose photos')) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
});

test('missing fields expose associated errors, focus the first control and prevent submission', () => {
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  fireEvent.click(screen.getByRole('button', { name: 'Add stone' }));
  const name = screen.getByLabelText('Name *');
  expect(document.activeElement).toBe(name);
  expect(name.getAttribute('aria-invalid')).toBe('true');
  expect(name.getAttribute('aria-describedby')).toContain(screen.getByText('Enter a name.').id);
  expect(screen.getByText('Choose a stone type.')).toBeTruthy();
  expect(screen.getByText('Choose a size.')).toBeTruthy();
  expect(onSubmit).not.toHaveBeenCalled();
});

test.each([
  ['Name *', ' ', 'Enter a name.'],
  ['Name *', 'n'.repeat(121), 'Use at most 120 characters.'],
  ['Biography (optional)', 'b'.repeat(2049), 'Use at most 2048 characters.'],
])('invalid %s blocks the boundary and focuses that field', (label, value, message) => {
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  fillDetails();
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.submit(screen.getByRole('form', { name: 'Stone details' }));
  expect(screen.getByText(message).getAttribute('role')).toBe('alert');
  expect(document.activeElement).toBe(screen.getByLabelText(label));
  expect(onSubmit).not.toHaveBeenCalled();
});

test('valid Unicode and whitespace remain unchanged; zero photos send no placeholder or legacy cover', () => {
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  fillDetails();
  fireEvent.change(screen.getByLabelText('Biography (optional)'), { target: { value: '  История\n🏔️  ' } });
  fireEvent.submit(screen.getByRole('form', { name: 'Stone details' }));
  expect(onSubmit).toHaveBeenCalledExactlyOnceWith({
    name: '  Мрамор ✨  ', stoneType: 'MARBLE', stoneSize: 'SMALL', biography: '  История\n🏔️  ',
    adoptionStatus: 'AVAILABLE', photoUploadIds: [],
  });
});

test('an existing name and empty optional biography are accepted at the field length limits', () => {
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} />);
  fillDetails('Mars');
  fireEvent.click(screen.getByRole('button', { name: 'Add stone' }));
  expect(onSubmit.mock.calls[0][0].biography).toBe('');
  fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'n'.repeat(120) } });
  fireEvent.change(screen.getByLabelText('Biography (optional)'), { target: { value: 'b'.repeat(2048) } });
  fireEvent.click(screen.getByRole('button', { name: 'Add stone' }));
  expect(onSubmit).toHaveBeenCalledTimes(2);
});

test('creation pending locks all details and exposes progress', () => {
  const onSubmit = vi.fn();
  render(<AddStoneForm onSubmit={onSubmit} submitting />);
  expect(screen.getByRole('button', { name: 'Adding stone…' }).matches(':disabled')).toBe(true);
  expect(screen.getByLabelText('Name *').matches(':disabled')).toBe(true);
  expect(screen.getByRole('form').getAttribute('aria-busy')).toBe('true');
  expect(screen.getByRole('status', { name: 'Stone creation progress' }).textContent).toBe('Adding stone…');
  fireEvent.submit(screen.getByRole('form'));
  expect(onSubmit).not.toHaveBeenCalled();
});
