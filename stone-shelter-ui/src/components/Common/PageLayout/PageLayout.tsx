import type { ReactNode } from 'react';
import './PageLayout.css';

export function PageLayout({ children, sidebar, sidebarOpen, toolbar, chatOpen = false }: { children: ReactNode; sidebar: ReactNode; sidebarOpen: boolean; toolbar?: ReactNode; chatOpen?: boolean }) {
  return (
    <div className={`page-layout${sidebarOpen ? " sidebar-visible" : ""}${chatOpen ? ' chat-visible' : ''}`}>
      <aside className="filters-space" aria-label="Catalog filters">{sidebar}</aside>
      <div className="catalog-slot">{toolbar}{children}</div>
      <div className="chat-space" aria-hidden="true" />
    </div>
  );
}
