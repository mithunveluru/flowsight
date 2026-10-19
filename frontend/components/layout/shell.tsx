"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "./sidebar";
import { Header } from "./header";
import { CommandMenu } from "./command-menu";
import { SampleDataBanner } from "./sample-data-banner";
import { accentForPath } from "./nav";

export function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [navOpen, setNavOpen] = useState(false);
  const [commandOpen, setCommandOpen] = useState(false);

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
    <div className="flex h-dvh overflow-hidden bg-background">
      <a
        href="#main-content"
        className="sr-only z-[200] rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
      <CommandMenu open={commandOpen} onClose={() => setCommandOpen(false)} />

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Header
          showCapture={pathname !== "/dashboard"}
          onOpenNav={() => setNavOpen(true)}
          onOpenSearch={() => setCommandOpen(true)}
        />
        <main
          id="main-content"
          tabIndex={-1}
          className="flex-1 overflow-y-auto focus:outline-none"
          style={{ ["--section-accent" as string]: accentForPath(pathname) }}
        >
          <SampleDataBanner />
          <div className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 sm:py-8 lg:px-12 lg:py-12">{children}</div>
        </main>
      </div>
    </div>
  );
}
