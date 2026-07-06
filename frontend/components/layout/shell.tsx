"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "./sidebar";
import { Header } from "./header";
import { CommandMenu } from "./command-menu";

interface ShellProps {
  children: React.ReactNode;
  title?: string;
  description?: string;
  actions?: React.ReactNode;
}

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
  const [navOpen, setNavOpen] = useState(false);
  const [commandOpen, setCommandOpen] = useState(false);

  // global ⌘K / Ctrl+K opens the command menu
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setCommandOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <CommandMenu open={commandOpen} onClose={() => setCommandOpen(false)} />

      <div className="flex flex-1 flex-col overflow-hidden">
        <Header
          title={title}
          description={description}
          actions={actions}
          onOpenNav={() => setNavOpen(true)}
          onOpenSearch={() => setCommandOpen(true)}
        />
        <main
          className="flex-1 overflow-y-auto"
          style={{ ["--section-accent" as string]: sectionAccent }}
        >
          <div className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 sm:py-8 lg:px-12 lg:py-12">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
