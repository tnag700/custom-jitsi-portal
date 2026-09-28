import { component$ } from "@qwik.dev/core";
import { Link } from "@qwik.dev/router";
import { pageHref } from "../routes/page-query";

interface PageNavigationProps {
  currentUrl: string;
  parameter: string;
  page: number;
  totalPages: number;
  label: string;
}

export const PageNavigation = component$<PageNavigationProps>(
  ({ currentUrl, parameter, page, totalPages, label }) => {
    if (totalPages <= 1 && page === 0) return null;

    return (
      <nav aria-label={label} class="mt-4 flex items-center justify-between gap-3 text-sm">
        {page > 0 ? (
          <Link href={pageHref(currentUrl, parameter, page - 1)} class="text-primary underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
            Предыдущая
          </Link>
        ) : <span />}
        <span class="text-muted">Страница {page + 1} из {Math.max(totalPages, 1)}</span>
        {page + 1 < totalPages ? (
          <Link href={pageHref(currentUrl, parameter, page + 1)} class="text-primary underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
            Следующая
          </Link>
        ) : <span />}
      </nav>
    );
  },
);
