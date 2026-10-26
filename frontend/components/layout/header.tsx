"use client";

import { usePathname } from "next/navigation";
import { ChevronRight, Menu, Search, Sparkles } from "lucide-react";
import { CaptureMenu } from "./capture-menu";
import { locate } from "./nav";

interface HeaderProps {
  // Today has its own capture button in the page header row
  showCapture?: boolean;
  onOpenNav?: () => void;
  onOpenSearch?: () => void;
  onOpenAssistant?: () => void;
  assistantOpen?: boolean;
}

// The page's own h1 names it; the header only shows where it sits.
export function Header({ showCapture = true, onOpenNav, onOpenSearch, onOpenAssistant, assistantOpen }: HeaderProps) {
  const place = locate(usePathname());

  return (
    <header className="flex h-header shrink-0 items-center gap-2 border-b border-border bg-background px-3 sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onOpenNav}
        aria-label="Open navigation"
        className="-ml-1 inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
      >
        <Menu className="h-5 w-5" strokeWidth={1.75} aria-hidden="true" />
      </button>

      <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
        {place && (
          <ol className="flex min-w-0 items-center gap-1 text-[13px] text-muted-foreground">
            {place.section && (
              <li className="hidden shrink-0 items-center gap-1 sm:flex">
                {place.section}
                <ChevronRight className="h-3.5 w-3.5" strokeWidth={1.75} aria-hidden="true" />
              </li>
            )}
            <li aria-current="page" className="truncate font-medium text-foreground">
              {place.label}
            </li>
          </ol>
        )}
      </nav>

      <div className="flex shrink-0 items-center gap-2">
        <button
          type="button"
          onClick={onOpenSearch}
          aria-label="Search and commands"
          aria-keyshortcuts="Control+K Meta+K"
          className="inline-flex h-9 items-center gap-2 rounded-lg border border-input bg-background px-2.5 text-xs text-muted-foreground transition-colors hover:bg-muted md:pl-3 md:pr-1.5"
        >
          <Search className="h-4 w-4 md:h-3.5 md:w-3.5" strokeWidth={1.75} aria-hidden="true" />
          <span className="hidden md:inline">Search</span>
          <kbd className="kbd-hint ml-1 hidden before:content-[attr(data-key)] md:inline-flex" data-key="⌘K" aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={onOpenAssistant}
          aria-label="Ask FlowSight"
          aria-expanded={!!assistantOpen}
          aria-keyshortcuts="Control+J Meta+J"
          className="inline-flex h-9 items-center gap-2 rounded-lg border border-input bg-background px-2.5 text-xs text-muted-foreground transition-colors hover:bg-muted"
        >
          <Sparkles className="h-4 w-4 md:h-3.5 md:w-3.5" strokeWidth={1.75} aria-hidden="true" />
          <span className="hidden md:inline">Ask</span>
          <kbd className="kbd-hint ml-1 hidden before:content-[attr(data-key)] md:inline-flex" data-key="⌘J" aria-hidden="true" />
        </button>
        {showCapture && (
          <>
            <CaptureMenu compact className="sm:hidden" />
            <CaptureMenu className="hidden sm:inline-flex" />
          </>
        )}
      </div>
    </header>
  );
}
