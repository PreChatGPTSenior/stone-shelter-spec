import { useEffect, useId, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import type { StoneResponse } from '../../../../api/dto/StoneResponse';
import type { StoneReservationCreateRequest } from '../../../../api/dto/StoneReservationCreateRequest';
import type { ReservationSubmissionStatus } from '../../../../enums/ReservationSubmissionStatus';
import './AdoptStoneModal.css';

interface AdoptStoneModalProps {
  stone: StoneResponse;
  status: ReservationSubmissionStatus;
  error?: string;
  blocked?: boolean;
  onSubmit: (request: StoneReservationCreateRequest) => void;
  onClose: () => void;
  returnFocusRef: RefObject<HTMLDivElement | null>;
}

const placeholder = '/placeholder-rock.png';

export function AdoptStoneModal({ stone, status, error, blocked, onSubmit, onClose, returnFocusRef }: AdoptStoneModalProps) {
  const id = useId();
  const modalRef = useRef<HTMLDivElement>(null);
  const backdropRef = useRef<HTMLDivElement>(null);
  const successRef = useRef<HTMLDivElement>(null);
  const [applicantName, setApplicantName] = useState('');
  const [contactDetails, setContactDetails] = useState('');
  const [errors, setErrors] = useState({ applicantName: '', contactDetails: '' });
  const photo = stone.photos[0]?.url || stone.photo || placeholder;
  const pending = status === 'submitting';

  useEffect(() => {
    const modal = modalRef.current!;
    const returnTarget = returnFocusRef.current;
    const previousOverflow = document.body.style.overflow;
    const background = new Map<HTMLElement, boolean>();
    for (const element of document.body.children) {
      if (element instanceof HTMLElement && element !== backdropRef.current) {
        background.set(element, Boolean(element.inert));
        element.inert = true;
      }
    }
    document.body.style.overflow = 'hidden';
    modal.querySelector<HTMLInputElement>('input')?.focus();
    function focusInside(event: FocusEvent) {
      if (!modal.contains(event.target as Node)) modal.querySelector<HTMLButtonElement>('button')?.focus();
    }
    function keydown(event: KeyboardEvent) {
      if (event.key === 'Escape') { event.preventDefault(); onClose(); return; }
      if (event.key !== 'Tab') return;
      const controls = [...modal.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled)')];
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && (document.activeElement === first || !controls.includes(document.activeElement as HTMLElement))) {
        event.preventDefault(); last?.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !controls.includes(document.activeElement as HTMLElement))) {
        event.preventDefault(); first?.focus();
      }
    }
    document.addEventListener('keydown', keydown);
    document.addEventListener('focusin', focusInside);
    return () => {
      document.removeEventListener('keydown', keydown);
      document.removeEventListener('focusin', focusInside);
      document.body.style.overflow = previousOverflow;
      background.forEach((inert, element) => { element.inert = inert; });
      if (returnTarget?.isConnected) {
        (returnTarget.querySelector<HTMLButtonElement>('button:not(:disabled)') ?? returnTarget).focus({ preventScroll: true });
      }
    };
  }, [onClose, returnFocusRef]);

  useEffect(() => {
    if (status === 'success') successRef.current?.focus();
  }, [status]);

  function submit() {
    if (pending || blocked) return;
    const validation = {
      applicantName: applicantName.trim() ? '' : 'Enter your name.',
      contactDetails: contactDetails.trim() ? '' : 'Enter your contact details.',
    };
    setErrors(validation);
    if (validation.applicantName || validation.contactDetails) {
      modalRef.current?.querySelector<HTMLInputElement | HTMLTextAreaElement>(validation.applicantName ? 'input' : 'textarea')?.focus();
      return;
    }
    onSubmit({ applicantName, contactDetails });
  }

  return createPortal(
    <div className="adopt-stone-backdrop" ref={backdropRef}>
      <div className="adopt-stone-modal" role="dialog" aria-modal="true" aria-labelledby={`${id}-title`} ref={modalRef}>
        <div className="adopt-stone-modal-heading">
          <h2 id={`${id}-title`}>Adopt this stone</h2>
          <button className="adopt-stone-close" type="button" aria-label="Close adoption dialog" onClick={onClose}>×</button>
        </div>
        <div className="adopt-stone-summary">
          <img src={photo} alt={`Photo of ${stone.name}`} onError={event => {
            if (event.currentTarget.getAttribute('src') !== placeholder) event.currentTarget.src = placeholder;
          }} />
          <div><h3>{stone.name}</h3><p>ID {stone.id}</p></div>
        </div>
        {status === 'success' ? <div className="adopt-stone-success" role="status" tabIndex={-1} ref={successRef}>
          <svg className="adopt-stone-check" viewBox="0 0 64 64" aria-hidden="true">
            <circle cx="32" cy="32" r="30" /><path d="m19 32 9 9 18-20" />
          </svg>
          <p>Application submitted. Please wait for us to contact you.</p>
        </div> : <form className="adopt-stone-form" noValidate aria-busy={pending} onSubmit={event => { event.preventDefault(); submit(); }}>
          <div className="adopt-stone-field">
            <label htmlFor={`${id}-name`}>Your name</label>
            <input id={`${id}-name`} name="applicantName" type="text" required value={applicantName} disabled={pending}
              aria-invalid={Boolean(errors.applicantName)} aria-describedby={errors.applicantName ? `${id}-name-error` : undefined}
              onChange={event => { setApplicantName(event.target.value); setErrors(current => ({ ...current, applicantName: '' })); }} />
            {errors.applicantName && <p className="adopt-stone-error" id={`${id}-name-error`} role="alert">{errors.applicantName}</p>}
          </div>
          <div className="adopt-stone-field">
            <label htmlFor={`${id}-contacts`}>Contact details</label>
            <textarea id={`${id}-contacts`} name="contactDetails" required rows={3} value={contactDetails} disabled={pending}
              aria-invalid={Boolean(errors.contactDetails)} aria-describedby={errors.contactDetails ? `${id}-contacts-error` : undefined}
              onChange={event => { setContactDetails(event.target.value); setErrors(current => ({ ...current, contactDetails: '' })); }} />
            {errors.contactDetails && <p className="adopt-stone-error" id={`${id}-contacts-error`} role="alert">{errors.contactDetails}</p>}
          </div>
          {error && <p className="adopt-stone-error" role="alert">{error}</p>}
          <button className="adopt-stone-submit" type="submit" disabled={pending || blocked}>{pending ? 'Submitting…' : 'Submit application'}</button>
          {pending && <p className="adopt-stone-pending" role="status">Sending your application…</p>}
        </form>}
      </div>
    </div>, document.body,
  );
}
