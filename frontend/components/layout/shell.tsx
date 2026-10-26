"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { PageTransition } from "@/components/motion/page-transition";
import { Breadcrumb } from "./breadcrumb";
import { CaptureMenu } from "./capture-menu";
import { CommandMenu } from "./command-menu";
import { GlobalBar } from "./global-bar";
import { NavDrawer } from "./nav-drawer";
import { TabRail } from "./tab-rail";
import { SampleDataBanner } from "./sample-data-banner";
import { accentForPath } from "./nav";
import { AssistantProvider, useAssistant } from "@/components/assistant/assistant-context";
import { AssistantPanel } from "@/components/assistant/assistant-panel";

export function Shell({ children }: { children: React.ReactNode }) {
  return (
    <AssistantProvider>
      <ShellFrame>{children}</ShellFrame>
    </AssistantProvider>
  );
}

function ShellFrame({ children }: { children: React.ReactNode }) {
  const assistant = useAssistant();
  const pathname = usePathname();
  const [navOpen, setNavOpen] = useState(false);
  const [commandOpen, setCommandOpen] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setCommandOpen((v) => !v);
      } else if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "j") {
        e.preventDefault();
        assistant.toggle();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [assistant]);

  const showCapture = pathname !== "/dashboard";

  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-background">
      <a
        href="#main-content"
        className="sr-only z-[200] rounded-md bg-primary px-3 py-2 text-ty-caption font-medium text-primary-foreground focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <NavDrawer open={navOpen} onClose={() => setNavOpen(false)} />
      <CommandMenu open={commandOpen} onClose={() => setCommandOpen(false)} />

      <GlobalBar
        onOpenNav={() => setNavOpen(true)}
        onOpenSearch={() => setCommandOpen(true)}
        onOpenAssistant={assistant.toggle}
        assistantOpen={assistant.open}
      />
      <div className="flex min-h-10 shrink-0 items-center gap-3 bg-canvas px-3 py-2 sm:px-6 lg:px-8">
        <Breadcrumb />
        {showCapture && (
          <>
            <CaptureMenu compact className="sm:hidden" />
            <CaptureMenu className="hidden sm:inline-flex" />
          </>
        )}
      </div>
      <TabRail />

      <div className="flex min-h-0 flex-1">
        <main
          id="main-content"
          tabIndex={-1}
          className="min-w-0 flex-1 overflow-y-auto focus:outline-none"
          style={{ ["--section-accent" as string]: accentForPath(pathname) }}
        >
          <SampleDataBanner />
          <div className="[container-type:inline-size]">
            <div className="page-column mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-12">
              <PageTransition>{children}</PageTransition>
            </div>
          </div>
        </main>
        <AssistantPanel />
      </div>
    </div>
  );
}
