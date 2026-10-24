import './Footer.css';

export function Footer() {
  return (
    <footer className="site-footer">
      <small>© {new Date().getFullYear()} Stone Shelter</small>
    </footer>
  );
}
