import { useRef, useState } from 'react';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test } from 'vitest';
import type { StoneSearchFilter } from '../../../../api/dto/StoneSearchFilter';
import { ActiveFilterLabels } from './ActiveFilterLabels';

afterEach(cleanup);

function Harness({ initial }: { initial: StoneSearchFilter }) {
  const [filter, setFilter] = useState(initial);
  const headingRef = useRef<HTMLDivElement>(null);
  return <><div ref={headingRef} tabIndex={-1} role="group" aria-label="Catalog heading">Catalog</div>
    <ActiveFilterLabels filter={filter} headingRef={headingRef}
      onSizesChange={stoneSizes => setFilter(current => ({ ...current, stoneSizes }))}
      onTypesChange={stoneTypes => setFilter(current => ({ ...current, stoneTypes }))}
      onClearDates={() => setFilter(current => ({ ...current, admissionDateFrom: undefined, admissionDateTo: undefined }))} />
  </>;
}

test('derives unique labels in option order rather than selection order', () => {
  render(<Harness initial={{ stoneSizes: ['LARGE', 'SMALL', 'SMALL'], stoneTypes: ['GRANITE', 'BASALT'],
    admissionDateFrom: '2026-09-01', admissionDateTo: '2026-09-10' }} />);
  expect(within(screen.getByRole('region', { name: 'Active filters' })).getAllByRole('listitem').map(item => item.querySelector('span')?.textContent))
    .toEqual(['Size: Small', 'Size: Large', 'Stone type: Basalt', 'Stone type: Granite', 'Admission date: 2026-09-01 to 2026-09-10']);
  expect(screen.getAllByRole('button')).toHaveLength(5);
});

test.each([
  [{ admissionDateFrom: '2026-09-01' }, 'Admission date: from 2026-09-01'],
  [{ admissionDateTo: '2026-09-10' }, 'Admission date: through 2026-09-10'],
])('renders open date bounds and removes the date group', (initial, text) => {
  render(<Harness initial={initial} />);
  expect(screen.getByText(text)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'Remove admission date filter' }));
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
});

test('focused removal moves to next, previous and finally catalog heading', () => {
  render(<Harness initial={{ stoneSizes: ['SMALL', 'LARGE'], stoneTypes: ['GRANITE'] }} />);
  const first = screen.getByRole('button', { name: 'Remove size Small' });
  act(() => first.focus());
  fireEvent.click(first);
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Remove size Large' }));
  const last = screen.getByRole('button', { name: 'Remove stone type Granite' });
  act(() => last.focus());
  fireEvent.click(last);
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Remove size Large' }));
  fireEvent.click(document.activeElement!);
  expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull();
  expect(document.activeElement).toBe(screen.getByRole('group', { name: 'Catalog heading' }));
});
