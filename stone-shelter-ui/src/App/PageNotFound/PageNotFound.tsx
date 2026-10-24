import { useEffect, useRef } from 'react';
import { catalogPath } from '../navigation/pageRoutes';
import './PageNotFound.css';

export function PageNotFound() {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, []);

  return (
    <main className="page-not-found" aria-labelledby="page-not-found-heading">
      <h1 id="page-not-found-heading" ref={heading} tabIndex={-1}>Page not found</h1>
      <p>We could not find a page at this address.</p>
      <a href={catalogPath}>Back to catalog</a>
    </main>
  );
}
