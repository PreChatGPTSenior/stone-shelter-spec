import { useLayoutEffect, useRef, type RefObject } from 'react';
import type { StoneSearchFilter } from '../../../../api/dto/StoneSearchFilter';
import type { StoneSize } from '../../../../enums/StoneSize';
import type { StoneType } from '../../../../enums/StoneType';
import { stoneSizeLabels, stoneTypeLabels } from '../../../../domain/stone/presentation/stoneLabels';
import './ActiveFilterLabels.css';

interface ActiveFilterLabelsProps {
  filter: StoneSearchFilter;
  onSizesChange: (values: StoneSize[]) => void;
  onTypesChange: (values: StoneType[]) => void;
  onClearDates: () => void;
  headingRef: RefObject<HTMLDivElement | null>;
}

export function ActiveFilterLabels({ filter, onSizesChange, onTypesChange, onClearDates, headingRef }: ActiveFilterLabelsProps) {
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const pendingFocus = useRef<string | null | undefined>(undefined);
  const labels = [
    ...(Object.keys(stoneSizeLabels) as StoneSize[]).filter(value => filter.stoneSizes?.includes(value)).map(value => ({
      key: `size-${value}`, text: `Size: ${stoneSizeLabels[value]}`, removeLabel: `Remove size ${stoneSizeLabels[value]}`,
      remove: () => onSizesChange((filter.stoneSizes ?? []).filter(item => item !== value)),
    })),
    ...(Object.keys(stoneTypeLabels) as StoneType[]).filter(value => filter.stoneTypes?.includes(value)).map(value => ({
      key: `type-${value}`, text: `Stone type: ${stoneTypeLabels[value]}`, removeLabel: `Remove stone type ${stoneTypeLabels[value]}`,
      remove: () => onTypesChange((filter.stoneTypes ?? []).filter(item => item !== value)),
    })),
  ];
  if (filter.admissionDateFrom || filter.admissionDateTo) {
    labels.push({
      key: 'date',
      text: `Admission date: ${filter.admissionDateFrom && filter.admissionDateTo
        ? `${filter.admissionDateFrom} to ${filter.admissionDateTo}`
        : filter.admissionDateFrom ? `from ${filter.admissionDateFrom}` : `through ${filter.admissionDateTo}`}`,
      removeLabel: 'Remove admission date filter', remove: onClearDates,
    });
  }

  useLayoutEffect(() => {
    if (pendingFocus.current === undefined) return;
    (pendingFocus.current === null ? headingRef.current : buttons.current.get(pendingFocus.current))?.focus({ preventScroll: true });
    pendingFocus.current = undefined;
  });

  if (labels.length === 0) return null;
  return <section className="active-filter-labels" aria-label="Active filters">
    <ul>{labels.map((label, index) => <li key={label.key}>
      <span>{label.text}</span>
      <button type="button" aria-label={label.removeLabel}
        ref={element => { if (element) buttons.current.set(label.key, element); else buttons.current.delete(label.key); }}
        onClick={event => {
          if (document.activeElement === event.currentTarget) {
            pendingFocus.current = labels[index + 1]?.key ?? labels[index - 1]?.key ?? null;
          }
          label.remove();
        }}><span aria-hidden="true">×</span></button>
    </li>)}</ul>
  </section>;
}
