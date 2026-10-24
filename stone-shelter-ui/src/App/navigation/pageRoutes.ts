export const catalogPath = '/stone-shelter/catalog';
export const addStonePath = '/stone-shelter/add-stone';

export function stoneDetailsPath(id: number) {
  return `/stone-shelter/stones/${id}`;
}

type PageRoute = {
  pathname: string;
  page: 'catalog' | 'add-stone' | 'stone-details' | 'not-found';
  id?: number;
};

export function resolvePageRoute(pathname: string): PageRoute {
  const path = pathname.endsWith('/') ? pathname.slice(0, -1) : pathname;
  if (path === '' || path === '/stone-shelter' || path === catalogPath) {
    return { pathname: catalogPath, page: 'catalog' };
  }
  if (path === '/stones/new' || path === addStonePath) {
    return { pathname: addStonePath, page: 'add-stone' };
  }
  const match = /^(?:\/stone-shelter)?\/stones\/(.+)$/.exec(path);
  if (match) {
    const id = /^[1-9]\d*$/.test(match[1]) && Number.isSafeInteger(Number(match[1]))
      ? Number(match[1]) : undefined;
    return { pathname: id === undefined ? pathname : stoneDetailsPath(id), page: 'stone-details', id };
  }
  return { pathname, page: 'not-found' };
}
