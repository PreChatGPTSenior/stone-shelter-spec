import { useRef, useState } from 'react';
import { createStoneReservation } from '../../../api/stonesApi';
import { StoneReservationError } from '../../../api/StoneReservationError';
import type { StoneReservationCreateRequest } from '../../../api/dto/StoneReservationCreateRequest';
import type { ReservationSubmissionStatus } from '../../../enums/ReservationSubmissionStatus';

export function useStoneReservation(stoneId: number, onReserved: () => void) {
  const [status, setStatus] = useState<ReservationSubmissionStatus>('editing');
  const [error, setError] = useState<string>();
  const [blocked, setBlocked] = useState(false);
  const pending = useRef(false);

  function reset() {
    if (pending.current) return;
    setStatus('editing');
    setError(undefined);
    setBlocked(false);
  }

  async function submit(request: StoneReservationCreateRequest) {
    if (pending.current || blocked || status === 'success') return;
    pending.current = true;
    setStatus('submitting');
    setError(undefined);
    try {
      await createStoneReservation(stoneId, request);
      setStatus('success');
      onReserved();
    } catch (failure) {
      setStatus('error');
      setError(failure instanceof StoneReservationError ? failure.message : 'We could not send your application. Please try again.');
      setBlocked(failure instanceof StoneReservationError && (failure.status === 404 || failure.status === 409));
    } finally {
      pending.current = false;
    }
  }

  return { status, error, blocked, submit, reset };
}
