"use client";

import { usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { locate } from "./nav";

/** Where the page sits. The page's own h1 names it; this only shows the trail. */
export function Breadcrumb() {
  const place = locate(usePathname());
  return (
    <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
      {place && (
        <ol className="flex min-w-0 items-center gap-1 text-ty-nav-link text-ink-nav">
          {place.section && (
            <li className="hidden shrink-0 items-center gap-1 sm:flex">
              {place.section}
              <ChevronRight className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
            </li>
          )}
          <li aria-current="page" className="truncate text-ink">
            {place.label}
          </li>
        </ol>
      )}
    </nav>
  );
}
