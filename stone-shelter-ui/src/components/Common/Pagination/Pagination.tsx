import './Pagination.css';

interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

export function Pagination({ page, size, totalElements, onPageChange, onSizeChange }: PaginationProps) {
  const totalPages = Math.ceil(totalElements / size);
  return (
    <div className="pagination">
      <nav aria-label="Catalog pages">
        <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>Previous</button>
        {Array.from({ length: totalPages }, (_, index) => (
          <button type="button" key={index} aria-label={`Page ${index + 1}`}
            aria-current={page === index ? 'page' : undefined}
            onClick={() => onPageChange(index)}>{index + 1}</button>
        ))}
        <button type="button" disabled={totalPages === 0 || page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}>Next</button>
      </nav>
      <label>Stones per page
        <select value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          <option value={8}>8</option><option value={12}>12</option><option value={24}>24</option>
        </select>
      </label>
    </div>
  );
}
