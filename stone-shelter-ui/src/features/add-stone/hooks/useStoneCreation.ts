import { useEffect, useRef, useState } from 'react';
import { createStone } from '../../../api/stonesApi';
import type { StoneCreateRequest } from '../../../api/dto/StoneCreateRequest';
import type { StoneCreateResponse } from '../../../api/dto/StoneCreateResponse';
import { ApiError } from '../../../api/ApiError';

export function useStoneCreation(onCreated: () => void) {
  const [result, setResult] = useState<StoneCreateResponse>();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();
  const pending = useRef(false);
  const mounted = useRef(true);
  const generation = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; generation.current += 1; };
  }, []);

  async function submit(request: StoneCreateRequest) {
    if (pending.current) return;
    pending.current = true;
    setSubmitting(true);
    setError(undefined);
    const currentGeneration = generation.current;
    try {
      const stoneCreateResponse = await createStone(request);
      onCreated();
      if (mounted.current && generation.current === currentGeneration) setResult(stoneCreateResponse);
    } catch (failure) {
      if (mounted.current && generation.current === currentGeneration) {
        setError(failure instanceof ApiError ? failure.message : 'The stone could not be added. Please try again.');
      }
    } finally {
      if (mounted.current && generation.current === currentGeneration) {
        pending.current = false;
        setSubmitting(false);
      }
    }
  }

  function addAnother() {
    generation.current += 1;
    setResult(undefined);
    setError(undefined);
  }

  return { result, submitting, error, submit, addAnother };
}
