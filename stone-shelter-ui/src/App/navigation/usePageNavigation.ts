import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { catalogPath, resolvePageRoute } from './pageRoutes';

function readPathname() {
  const pathname = resolvePageRoute(window.location.pathname).pathname;
  if (pathname !== window.location.pathname) {
    window.history.replaceState(window.history.state, '', `${pathname}${window.location.search}${window.location.hash}`);
  }
  return pathname;
}

export function usePageNavigation() {
  const [pathname, setPathname] = useState(() => resolvePageRoute(window.location.pathname).pathname);
  const currentPath = useRef(pathname);
  const catalogScroll = useRef(0);

  useEffect(() => {
    const scrollRestoration = window.history.scrollRestoration;
    window.history.scrollRestoration = 'manual';
    function changePage() {
      if (currentPath.current === catalogPath) catalogScroll.current = window.scrollY;
      currentPath.current = readPathname();
      setPathname(currentPath.current);
    }

    function onNavigate(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const link = event.target instanceof Element ? event.target.closest('a') : null;
      if (!link || link.hasAttribute('download') || (link.target && link.target !== '_self')) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin || resolvePageRoute(url.pathname).page === 'not-found') return;
      if (link.getAttribute('href')?.startsWith('#')) return;
      event.preventDefault();
      if (url.href === window.location.href) return;
      if (currentPath.current === catalogPath) catalogScroll.current = window.scrollY;
      window.history.pushState(null, '', `${url.pathname}${url.search}${url.hash}`);
      currentPath.current = readPathname();
      setPathname(currentPath.current);
    }

    window.addEventListener('popstate', changePage);
    document.addEventListener('click', onNavigate);
    return () => {
      window.removeEventListener('popstate', changePage);
      document.removeEventListener('click', onNavigate);
      window.history.scrollRestoration = scrollRestoration;
    };
  }, []);

  useLayoutEffect(() => {
    readPathname();
    window.scrollTo({ top: pathname === catalogPath ? catalogScroll.current : 0, behavior: 'instant' });
  }, [pathname]);

  return { pathname };
}
