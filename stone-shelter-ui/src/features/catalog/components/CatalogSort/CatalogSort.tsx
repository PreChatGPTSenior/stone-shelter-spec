import type { CatalogSortOption } from '../../../../enums/CatalogSortOption';
import { catalogSortOptions } from '../../presentation/catalogSortOptions';
import './CatalogSort.css';

interface CatalogSortProps {
  value: CatalogSortOption;
  onChange: (value: CatalogSortOption) => void;
}

export function CatalogSort({ value, onChange }: CatalogSortProps) {
  return (
    <select className="catalog-sort" aria-label="Sort stones" value={value}
      onChange={(event) => onChange(event.target.value as CatalogSortOption)}>
      {Object.entries(catalogSortOptions).map(([key, option]) => (
        <option key={key} value={key}>{option.label}</option>
      ))}
    </select>
  );
}
