import { useId, useRef } from 'react';
import { FilterSidebar } from '../../../../components/Common/FilterSidebar/FilterSidebar';
import { PageLayout } from '../../../../components/Common/PageLayout/PageLayout';
import { Catalog } from '../Catalog/Catalog';
import { CatalogFilters } from '../CatalogFilters/CatalogFilters';
import { ActiveFilterLabels } from '../ActiveFilterLabels/ActiveFilterLabels';
import { useCatalogSession } from '../../state/CatalogSessionProvider/CatalogSessionProvider';

export function CatalogPage({ chatOpen = false }: { chatOpen?: boolean }) {
  const catalog = useCatalogSession();
  const sidebarId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const headingRef = useRef<HTMLDivElement>(null);
  return (
    <PageLayout sidebarOpen={catalog.filtersExpanded} chatOpen={chatOpen} toolbar={!catalog.filtersExpanded &&
      <button ref={triggerRef} className="sidebar-open" type="button" aria-label="Open filters"
        aria-expanded={false} aria-controls={sidebarId} onClick={catalog.onOpenFilters}>
        Filters <span className="filter-count" aria-label={`${catalog.activeFilterGroups} active filter groups`}>{catalog.activeFilterGroups}</span>
        <span aria-hidden="true">→</span>
      </button>}
      sidebar={<FilterSidebar id={sidebarId} open={catalog.filtersExpanded}
        onClose={catalog.onCloseFilters} triggerRef={triggerRef}>{catalog.filtersExpanded && <CatalogFilters filter={catalog.filter} dateDraft={catalog.dateDraft}
      dateError={catalog.dateError}
      onSizesChange={catalog.onSizesChange} onTypesChange={catalog.onTypesChange}
      onDatesChange={catalog.onDatesChange} onResetFilters={catalog.onResetFilters} />}</FilterSidebar>}>
      <Catalog sortOption={catalog.sortOption} onSortChange={catalog.onSortChange}
        headingRef={headingRef} activeFilters={<ActiveFilterLabels filter={catalog.filter}
          onSizesChange={catalog.onSizesChange} onTypesChange={catalog.onTypesChange}
          onClearDates={() => catalog.onDatesChange('', '')} headingRef={headingRef} />}
        loading={catalog.loading} error={catalog.error} onRetry={catalog.refreshCatalog} page={catalog.page} size={catalog.size}
        response={catalog.response} onPageChange={catalog.onPageChange} onSizeChange={catalog.onSizeChange} />
    </PageLayout>
  );
}
