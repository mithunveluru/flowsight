"use client";

import { Menu, Search } from "lucide-react";
import { cn } from "@/lib/utils";

interface HeaderProps {
  title?: string;
  description?: string;
  actions?: React.ReactNode;
  onOpenNav?: () => void;
  onOpenSearch?: () => void;
}

export function Header({ title, description, actions, onOpenNav, onOpenSearch }: HeaderProps) {
  return (
    <header className="flex h-header shrink-0 items-center gap-2 border-b border-border bg-background px-4 sm:px-6 lg:px-8">
      {/* Mobile hamburger — opens the sidebar drawer. 44px target. */}
      <button
        type="button"
        onClick={onOpenNav}
        aria-label="Open navigation"
        className="-ml-2 inline-flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
      >
        <Menu className="h-5 w-5" strokeWidth={1.75} />
      </button>

      <div className="flex-1 min-w-0">
        {title && (
          <h1 className="truncate text-[13px] font-medium text-muted-foreground">
            {title}
          </h1>
        )}
        {description && (
          <p className="mt-0.5 truncate text-xs text-muted-foreground/70">
            {description}
          </p>
        )}
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={onOpenSearch}
          className={cn(
            "hidden md:flex items-center gap-2 h-8 rounded-lg border bg-background pl-3 pr-1.5",
            "text-xs text-muted-foreground",
            "transition-colors hover:bg-muted"
          )}
          style={{ borderColor: "hsl(var(--input))" }}
          aria-label="Open command menu"
        >
          <Search className="h-3.5 w-3.5" strokeWidth={1.75} />
          <span>Search</span>
          <kbd className="kbd-hint ml-1">⌘K</kbd>
        </button>

        {actions && <div className="ml-2 flex items-center gap-2">{actions}</div>}
      </div>
    </header>
  );
}
