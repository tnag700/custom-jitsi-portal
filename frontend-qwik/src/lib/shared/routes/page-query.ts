export function readPage(
  query: Pick<URLSearchParams, "get"> | undefined,
  name: string,
): number {
  const raw = query?.get(name);
  if (!raw || !/^(0|[1-9]\d*)$/.test(raw)) {
    return 0;
  }
  const page = Number(raw);
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 0;
}

export function pageHref(currentUrl: string, name: string, page: number): string {
  const url = new URL(currentUrl);
  url.searchParams.set(name, String(page));
  return `${url.pathname}${url.search}`;
}
