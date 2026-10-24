import { useCallback, useRef, useState } from 'react';
import type { StoneResponse } from '../../../../api/dto/StoneResponse';
import { AdoptStoneModal } from '../AdoptStoneModal/AdoptStoneModal';
import { useStoneReservation } from '../../hooks/useStoneReservation';
import './AdoptStoneAction.css';

export function AdoptStoneAction({ stone, onReserved }: { stone: StoneResponse; onReserved: () => void }) {
  const [open, setOpen] = useState(false);
  const returnFocusRef = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setOpen(false), []);
  const reservation = useStoneReservation(stone.id, onReserved);
  return <div className="adopt-stone-action" ref={returnFocusRef} tabIndex={-1} role="group" aria-label="Adoption action">
    <button type="button" disabled={stone.adoptionStatus !== 'AVAILABLE' || reservation.status === 'submitting' || reservation.status === 'success'}
      onClick={() => { reservation.reset(); setOpen(true); }}>Adopt this stone</button>
    {open && <AdoptStoneModal key={stone.id} stone={stone} status={reservation.status} error={reservation.error} blocked={reservation.blocked}
      onSubmit={reservation.submit} onClose={close} returnFocusRef={returnFocusRef} />}
  </div>;
}
