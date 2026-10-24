import { createContext, useContext, type ReactNode } from 'react';
import { useCatalog } from '../../hooks/useCatalog';

const CatalogSessionContext = createContext<ReturnType<typeof useCatalog> | null>(null);

export function CatalogSessionProvider({ children }: { children: ReactNode }) {
  const catalog = useCatalog();
  return <CatalogSessionContext.Provider value={catalog}>{children}</CatalogSessionContext.Provider>;
}

export function useCatalogSession() {
  const catalog = useContext(CatalogSessionContext);
  if (!catalog) throw new Error('Catalog must be inside CatalogSessionProvider.');
  return catalog;
}
