import { useEffect, useReducer, useState } from 'react';
import { getStone } from '../../../api/stonesApi';
import { ApiError } from '../../../api/ApiError';
import type { StoneResponse } from '../../../api/dto/StoneResponse';

export function useStoneDetails(id?: number) {
  const [revision, refreshStone] = useReducer((revision: number) => revision + 1, 0);
  const [state, setState] = useState<{ id?: number; stone?: StoneResponse; loading: boolean; error?: string }>({ loading: id !== undefined });

  useEffect(() => {
    let current = true;
    if (id === undefined) {
      setState({ loading: false });
      return;
    }
    setState(previous => ({ id, stone: previous.id === id ? previous.stone : undefined, loading: true }));
    getStone(id).then(stone => {
      if (current) setState({ id, stone, loading: false });
    }).catch(failure => {
      if (current) setState(previous => ({ ...previous, loading: false,
        error: failure instanceof ApiError ? failure.message : 'The stone could not be loaded. Please try again.' }));
    });
    return () => { current = false; };
  }, [id, revision]);

  return {
    stone: state.id === id ? state.stone : undefined,
    loading: id !== undefined && (state.id !== id || state.loading),
    error: state.id === id ? state.error : undefined,
    refreshStone,
  };
}
