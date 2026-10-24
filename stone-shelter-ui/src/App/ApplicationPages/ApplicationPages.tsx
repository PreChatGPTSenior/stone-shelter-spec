import { CatalogPage } from '../../features/catalog/components/CatalogPage/CatalogPage';
import { StoneDetailsPage } from '../../features/stone-details/components/StoneDetailsPage/StoneDetailsPage';
import { usePageNavigation } from '../navigation/usePageNavigation';
import { AddStonePage } from '../../features/add-stone/components/AddStonePage/AddStonePage';
import { useCatalogSession } from '../../features/catalog/state/CatalogSessionProvider/CatalogSessionProvider';
import { resolvePageRoute } from '../navigation/pageRoutes';
import { PageNotFound } from '../PageNotFound/PageNotFound';
import { StoneChatbot } from '../../features/stone-chatbot/components/StoneChatbot/StoneChatbot';
import { useChatSession } from '../../features/stone-chatbot/state/ChatSessionProvider/ChatSessionProvider';

export function ApplicationPages() {
  const { pathname } = usePageNavigation();
  const { refreshCatalog } = useCatalogSession();
  const { open } = useChatSession();
  const route = resolvePageRoute(pathname);

  return (
    <>
      {route.page === 'catalog' ? <CatalogPage chatOpen={open} />
        : route.page === 'add-stone' ? <AddStonePage key={pathname} onCreated={refreshCatalog} />
          : route.page === 'stone-details' ? <StoneDetailsPage key={pathname} id={route.id} onCatalogChange={refreshCatalog} />
            : <PageNotFound />}
      <StoneChatbot visible={route.page === 'catalog' || route.page === 'stone-details'} />
    </>
  );
}
