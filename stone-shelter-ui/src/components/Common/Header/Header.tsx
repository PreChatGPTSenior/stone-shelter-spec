import './Header.css';
import { catalogPath } from '../../../App/navigation/pageRoutes';

export function Header() {
  return (
      <header className="site-header">
        <a className="brand" href={catalogPath}>
          <svg viewBox="0 0 48 48" width="42" height="42" aria-hidden="true">
            <path d="M5 21 24 5l19 16" fill="none" stroke="currentColor" strokeWidth="1.6" />
            <path d="M9 36 15 25 25 21 35 27 39 36 33 40H14Z" fill="#e1e3de" stroke="currentColor" strokeWidth="1.4" />
            <path d="m15 34 9-7 10 7" fill="none" stroke="#939c91" />
          </svg>
          <h1>Stone Shelter</h1>
        </a>
      </header>
  );
}
