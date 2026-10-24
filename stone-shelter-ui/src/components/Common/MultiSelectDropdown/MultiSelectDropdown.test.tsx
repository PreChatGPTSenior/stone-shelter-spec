import { useRef, useState } from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { MultiSelectDropdown } from './MultiSelectDropdown';

afterEach(cleanup);
const options = [{ value: 'BASALT', label: 'Basalt' }, { value: 'GRANITE', label: 'Granite' }, { value: 'MARBLE', label: 'Marble' }];

function Harness({ changed = () => {} }: { changed?: (values: string[]) => void }) {
  const [values, setValues] = useState<string[]>([]);
  const [resetSignal, setResetSignal] = useState(0);
  const resetButtonRef = useRef<HTMLButtonElement>(null);
  return <><MultiSelectDropdown label="Stone type" options={options} values={values}
    onChange={values => { setValues(values); changed(values); }} resetSignal={resetSignal} resetButtonRef={resetButtonRef} />
    <button ref={resetButtonRef} onClick={() => { setValues([]); setResetSignal(value => value + 1); }}>Reset</button>
    <button>Outside</button></>;
}

test('searches labels locally and preserves controlled selections hidden by a query', () => {
  const changed = vi.fn();
  render(<Harness changed={changed} />);
  const trigger = screen.getByRole('button', { name: 'Stone type 0' });
  expect(trigger.getAttribute('aria-expanded')).toBe('false');
  expect(screen.queryByRole('checkbox')).toBeNull();
  fireEvent.click(trigger);
  const search = screen.getByRole('searchbox', { name: 'Search stone type' });
  expect(document.activeElement).toBe(search);
  fireEvent.click(screen.getByRole('checkbox', { name: 'Granite' }));
  fireEvent.change(search, { target: { value: '  bAs  ' } });
  expect(screen.getAllByRole('checkbox').map(input => input.getAttribute('type'))).toEqual(['checkbox']);
  expect(screen.getByRole('checkbox', { name: 'Basalt' })).toBeTruthy();
  expect(screen.queryByRole('checkbox', { name: 'Granite' })).toBeNull();
  expect(changed).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('checkbox', { name: 'Basalt' }));
  expect(changed).toHaveBeenLastCalledWith(['GRANITE', 'BASALT']);
  expect(screen.getByRole('button', { name: 'Stone type 2' }).getAttribute('aria-expanded')).toBe('true');
  expect((search as HTMLInputElement).value).toBe('  bAs  ');
  fireEvent.change(search, { target: { value: 'unknown' } });
  expect(screen.getByRole('status').textContent).toBe('No matching options.');
  expect(screen.queryByRole('checkbox')).toBeNull();
  fireEvent.change(search, { target: { value: '' } });
  expect(screen.getAllByRole('checkbox').map(input => (input.parentElement?.textContent))).toEqual(['Basalt', 'Granite', 'Marble']);
  expect((screen.getByRole('checkbox', { name: 'Granite' }) as HTMLInputElement).checked).toBe(true);
  fireEvent.click(screen.getByRole('checkbox', { name: 'Granite' }));
  expect(changed).toHaveBeenLastCalledWith(['BASALT']);
});

test('Escape consumes dismissal, restores trigger focus and clears search only', () => {
  const keydown = vi.fn();
  document.addEventListener('keydown', keydown);
  try {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: 'Stone type 0' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Marble' }));
    const search = screen.getByRole('searchbox');
    fireEvent.change(search, { target: { value: 'mar' } });
    fireEvent.keyDown(search, { key: 'Escape' });
    expect(keydown).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Stone type 1' }));
    expect(screen.queryByRole('searchbox')).toBeNull();
    fireEvent.click(document.activeElement!);
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
    expect((screen.getByRole('checkbox', { name: 'Marble' }) as HTMLInputElement).checked).toBe(true);
  } finally { document.removeEventListener('keydown', keydown); }
});

test('toggle, outside click and focus departure close without stealing outside focus', () => {
  render(<Harness />);
  const trigger = screen.getByRole('button', { name: 'Stone type 0' });
  fireEvent.click(trigger);
  fireEvent.click(trigger);
  expect(screen.queryByRole('searchbox')).toBeNull();
  fireEvent.click(trigger);
  const outside = screen.getByRole('button', { name: 'Outside' });
  fireEvent.click(outside);
  expect(screen.queryByRole('searchbox')).toBeNull();
  fireEvent.click(trigger);
  act(() => outside.focus());
  expect(screen.queryByRole('searchbox')).toBeNull();
  expect(document.activeElement).toBe(outside);
});

test('reset clears the query and selections while retaining panel expansion', () => {
  render(<Harness />);
  fireEvent.click(screen.getByRole('button', { name: 'Stone type 0' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'Granite' }));
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'gra' } });
  const reset = screen.getByRole('button', { name: 'Reset' });
  act(() => reset.focus());
  fireEvent.click(reset);
  expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('');
  expect(screen.getByRole('button', { name: 'Stone type 0' }).getAttribute('aria-expanded')).toBe('true');
  expect(screen.getAllByRole('checkbox').every(input => !(input as HTMLInputElement).checked)).toBe(true);
  act(() => screen.getByRole('button', { name: 'Outside' }).focus());
  expect(screen.queryByRole('searchbox')).toBeNull();
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Outside' }));
});
