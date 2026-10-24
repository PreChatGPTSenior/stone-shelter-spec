import { useId, useRef, useState } from 'react';
import { MultiSelectDropdown } from '../../../../components/Common/MultiSelectDropdown/MultiSelectDropdown';
import type { StoneSearchFilter } from '../../../../api/dto/StoneSearchFilter';
import type { StoneSize } from '../../../../enums/StoneSize';
import type { StoneType } from '../../../../enums/StoneType';
import { stoneSizeLabels, stoneTypeLabels } from '../../../../domain/stone/presentation/stoneLabels';
import './CatalogFilters.css';

interface CatalogFiltersProps {
  filter: StoneSearchFilter;
  dateDraft: { from: string; to: string };
  dateError?: string;
  onSizesChange: (values: StoneSize[]) => void;
  onTypesChange: (values: StoneType[]) => void;
  onDatesChange: (from: string, to: string, badInput?: boolean) => void;
  onResetFilters: () => void;
}

const sizeOptions = (Object.keys(stoneSizeLabels) as StoneSize[]).map(value => ({ value, label: stoneSizeLabels[value] }));
const typeOptions = (Object.keys(stoneTypeLabels) as StoneType[]).map(value => ({ value, label: stoneTypeLabels[value] }));

export function CatalogFilters(props: CatalogFiltersProps) {
  const errorId = useId();
  const resetButtonRef = useRef<HTMLButtonElement>(null);
  const [resetSignal, setResetSignal] = useState(0);
  return (
    <section className="catalog-filters" aria-label="Filters">
      <MultiSelectDropdown label="Size" options={sizeOptions} values={props.filter.stoneSizes ?? []}
        onChange={props.onSizesChange} resetSignal={resetSignal} resetButtonRef={resetButtonRef} />
      <MultiSelectDropdown label="Stone type" options={typeOptions} values={props.filter.stoneTypes ?? []}
        onChange={props.onTypesChange} resetSignal={resetSignal} resetButtonRef={resetButtonRef} />
        <fieldset className="date-fields"><legend>Admission date</legend>
          <label>From<input type="date" value={props.dateDraft.from} aria-invalid={Boolean(props.dateError)}
            aria-describedby={props.dateError ? errorId : undefined}
            onChange={event => props.onDatesChange(event.target.value, props.dateDraft.to, event.target.validity.badInput)} /></label>
          <label>To<input type="date" value={props.dateDraft.to} aria-invalid={Boolean(props.dateError)}
            aria-describedby={props.dateError ? errorId : undefined}
            onChange={event => props.onDatesChange(props.dateDraft.from, event.target.value, event.target.validity.badInput)} /></label>
          {props.dateError && <p id={errorId} className="date-error" role="alert">{props.dateError}</p>}
        </fieldset>
      <button ref={resetButtonRef} type="button" className="reset-filters" onClick={() => {
        setResetSignal(current => current + 1);
        props.onResetFilters();
      }}>Reset filters</button>
    </section>
  );
}
