import type { ReactNode, RefObject } from 'react';
import type { StonesSearchResponse } from '../../../../api/dto/StonesSearchResponse';
import { StoneCard } from '../../../../domain/stone/components/StoneCard/StoneCard';
import { Pagination } from '../../../../components/Common/Pagination/Pagination';

import type { CatalogSortOption } from '../../../../enums/CatalogSortOption';
import { CatalogSort } from '../CatalogSort/CatalogSort';
import './Catalog.css';
import { addStonePath } from '../../../../App/navigation/pageRoutes';

interface CatalogProps {
  activeFilters: ReactNode;
  headingRef: RefObject<HTMLDivElement | null>;
  sortOption: CatalogSortOption;
  onSortChange: (value: CatalogSortOption) => void;
  response?: StonesSearchResponse;
  loading: boolean;
  error?: string;
  onRetry: () => void;
  page: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

export function Catalog({ activeFilters, headingRef, response, loading, error, onRetry, page, size, onPageChange, onSizeChange, sortOption, onSortChange }: CatalogProps) {
  return (
    <main className="catalog" aria-label="Stone catalog" aria-busy={loading}>
      <div className="catalog-heading" ref={headingRef} tabIndex={-1} role="group" aria-label="Catalog heading">
        {!loading && !error && response && <p>Found <strong>{response.totalElements}</strong> stones</p>}
        <div className="catalog-actions"><a className="catalog-add-stone" href={addStonePath}>Add stone</a><CatalogSort value={sortOption} onChange={onSortChange} /></div>
      </div>
      {activeFilters}
      {loading && <p role="status">Loading stones…</p>}
      {error && <div><p role="alert">{error}</p><button type="button" onClick={onRetry}>Retry catalog</button></div>}
      {!loading && !error && response?.totalElements === 0 && <p role="status">No stones match your filters. Try changing or resetting them.</p>}
      <div className="catalog-grid">{!loading && !error && response?.content.map((stone) => <StoneCard key={stone.id} stone={stone} />)}</div>
      {!loading && !error && <Pagination page={response?.page ?? page} size={response?.size ?? size} totalElements={response?.totalElements ?? 0}
        onPageChange={onPageChange} onSizeChange={onSizeChange} />}
    </main>
  );
}
