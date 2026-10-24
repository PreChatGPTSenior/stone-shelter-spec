import { useEffect, useId, useRef, useState, type RefObject } from 'react';
import './MultiSelectDropdown.css';

interface MultiSelectDropdownProps<T extends string> {
  label: string;
  options: readonly { value: T; label: string }[];
  values: readonly T[];
  onChange: (values: T[]) => void;
  resetSignal?: number;
  resetButtonRef?: RefObject<HTMLButtonElement | null>;
}

export function MultiSelectDropdown<T extends string>({
  label, options, values, onChange, resetSignal, resetButtonRef,
}: MultiSelectDropdownProps<T>) {
  const id = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');

  function close() {
    setOpen(false);
    setQuery('');
  }

  useEffect(() => { setQuery(''); }, [resetSignal]);
  useEffect(() => {
    if (!open) return;
    searchRef.current?.focus();
    function outsideInteraction(event: MouseEvent | FocusEvent) {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)
        && !resetButtonRef?.current?.contains(event.target)) {
        setOpen(false);
        setQuery('');
      }
    }
    document.addEventListener('click', outsideInteraction);
    document.addEventListener('focusin', outsideInteraction);
    return () => {
      document.removeEventListener('click', outsideInteraction);
      document.removeEventListener('focusin', outsideInteraction);
    };
  }, [open, resetButtonRef]);

  const matchingOptions = options.filter(option => option.label.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div className="multi-select-dropdown" ref={rootRef}
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)
          && event.relatedTarget !== resetButtonRef?.current) close();
      }}
      onKeyDown={event => {
        if (open && event.key === 'Escape') {
          event.preventDefault();
          event.stopPropagation();
          close();
          triggerRef.current?.focus();
        }
      }}>
      <button ref={triggerRef} type="button" className="multi-select-trigger"
        aria-expanded={open} aria-controls={`${id}-panel`}
        onClick={() => { if (open) close(); else setOpen(true); }}>
        <span>{label}</span>{' '}<span className="multi-select-count">{values.length}</span>
        <span aria-hidden="true">{open ? '▴' : '▾'}</span>
      </button>
      <div id={`${id}-panel`} className="multi-select-panel" hidden={!open}>{open && <>
        <label className="multi-select-search" htmlFor={`${id}-search`}>Search {label.toLowerCase()}</label>
        <input ref={searchRef} id={`${id}-search`} type="search" value={query}
          onChange={event => setQuery(event.target.value)} autoComplete="off" />
        <fieldset className="multi-select-options">
          <legend className="multi-select-legend">{label} options</legend>
          {matchingOptions.map(option => <label key={option.value} className="multi-select-option">
            <input type="checkbox" checked={values.includes(option.value)}
              onChange={() => onChange(values.includes(option.value)
                ? values.filter(value => value !== option.value) : [...values, option.value])} />
            {option.label}
          </label>)}
          {matchingOptions.length === 0 && <p role="status">No matching options.</p>}
        </fieldset>
      </>}</div>
    </div>
  );
}
