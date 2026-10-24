import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import './FilterSidebar.css';

interface FilterSidebarProps {
  id: string;
  open: boolean;
  onClose: () => void;
  triggerRef: RefObject<HTMLButtonElement | null>;
  children: ReactNode;
}

export function FilterSidebar({ id, open, onClose, triggerRef, children }: FilterSidebarProps) {
  const previouslyOpen = useRef(open);
  const panelRef = useRef<HTMLDivElement>(null);
  const [mobile, setMobile] = useState(() => window.innerWidth < 960);
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth < 960);
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, []);

  useEffect(() => {
    if (previouslyOpen.current && !open) triggerRef.current?.focus();
    previouslyOpen.current = open;
  }, [open, triggerRef]);

  useEffect(() => {
    if (!open || !mobile || !panelRef.current) return;
    const panel = panelRef.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const background = new Map<HTMLElement, boolean>();
    let ancestor: HTMLElement = panel.parentElement!;
    while (ancestor !== document.body) {
      for (const sibling of ancestor.parentElement!.children) {
        if (sibling !== ancestor && sibling instanceof HTMLElement) {
          background.set(sibling, Boolean(sibling.inert));
          sibling.inert = true;
        }
      }
      ancestor = ancestor.parentElement!;
    }
    panel.querySelector<HTMLButtonElement>('button')?.focus();
    function keydown(event: KeyboardEvent) {
      if (event.defaultPrevented) return;
      if (event.key === 'Escape') { event.preventDefault(); onClose(); }
      if (event.key !== 'Tab') return;
      const controls = [...panel.querySelectorAll<HTMLElement>('button, input, select, [tabindex="0"]')]
        .filter(control => !control.hasAttribute('disabled') && !control.closest('[hidden], [inert], [aria-hidden="true"]'));
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener('keydown', keydown);
    return () => {
      document.removeEventListener('keydown', keydown);
      document.body.style.overflow = previousOverflow;
      background.forEach((inert, element) => { element.inert = inert; });
      triggerRef.current?.focus();
    };
  }, [open, mobile, onClose, triggerRef]);

  return (
    <>
      {mobile && open && <div className="sidebar-backdrop" onClick={onClose} aria-hidden="true" />}
      <div id={id} ref={panelRef} className={`filter-sidebar${open ? ' is-open' : ''}`}
        role={mobile && open ? 'dialog' : 'region'} aria-label="Filters"
        aria-modal={mobile && open ? true : undefined} aria-hidden={!open} inert={!open}>
        <div className="sidebar-heading"><h2>Filters</h2>
          <button type="button" aria-label="Close filters" aria-expanded={open} aria-controls={id} onClick={onClose}>
            <span aria-hidden="true">←</span>
          </button>
        </div>
        {children}
      </div>
    </>
  );
}
