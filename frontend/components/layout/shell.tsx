"use client";

import { usePathname } from "next/navigation";
import { Sidebar } from "./sidebar";
import { Header } from "./header";

interface ShellProps {
  children: React.ReactNode;
  title?: string;
  description?: string;
  actions?: React.ReactNode;
}

/**
 * Derive the section-accent CSS variable from the current route so every
 * `.accent-edge` and `.nav-item-active` on the page picks up a consistent
 * identity color. Defaults to the brand indigo for unknown routes.
 */
function accentForPath(pathname: string | null): string {
  if (!pathname) return "var(--brand)";
  if (pathname.startsWith("/dashboard/analytics"))    return "var(--accent-insights)";
  if (pathname.startsWith("/dashboard/insights"))     return "var(--accent-insights)";
  if (pathname.startsWith("/dashboard/recurring"))    return "var(--accent-recurring)";
  if (pathname.startsWith("/dashboard/leaks"))        return "var(--accent-leaks)";
  if (pathname.startsWith("/dashboard/simulate"))     return "var(--accent-simulate)";
  if (pathname.startsWith("/dashboard/reports"))      return "var(--accent-reports)";
  if (pathname.startsWith("/dashboard/receipts"))     return "var(--accent-receipts)";
  if (pathname.startsWith("/dashboard/transactions")) return "var(--accent-transactions)";
  if (pathname.startsWith("/dashboard/budgets"))      return "var(--accent-budgets)";
  if (pathname.startsWith("/dashboard/goals"))        return "var(--accent-goals)";
  return "var(--accent-overview)";
}

export function Shell({ children, title, description, actions }: ShellProps) {
  const pathname = usePathname();
  const sectionAccent = accentForPath(pathname);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />

      <div className="flex flex-1 flex-col overflow-hidden">
        <Header title={title} description={description} actions={actions} />
        <main
          className="flex-1 overflow-y-auto"
          style={{ ["--section-accent" as string]: sectionAccent }}
        >
          <div className="mx-auto w-full max-w-6xl px-8 py-10 lg:px-12 lg:py-12">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
