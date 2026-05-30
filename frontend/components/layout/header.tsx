"use client";

import { Search } from "lucide-react";
import { cn } from "@/lib/utils";

interface HeaderProps {
  title?: string;
  description?: string;
  actions?: React.ReactNode;
}

export function Header({ title, description, actions }: HeaderProps) {
  return (
    <header className="flex h-header shrink-0 items-center border-b border-border bg-background px-8">
      {/* Page title — quiet, doesn't compete with main page heading */}
      <div className="flex-1 min-w-0">
        {title && (
          <h1 className="text-[13px] font-medium text-muted-foreground truncate">
            {title}
          </h1>
        )}
        {description && (
          <p className="text-xs text-muted-foreground/70 mt-0.5 truncate">
            {description}
          </p>
        )}
      </div>

      {/* Right-side controls — minimal */}
      <div className="flex items-center gap-2">
        <button
          className={cn(
            "hidden md:flex items-center gap-2 h-8 rounded-lg border bg-background pl-3 pr-1.5",
            "text-xs text-muted-foreground",
            "transition-colors hover:bg-muted"
          )}
          style={{ borderColor: "hsl(var(--input))" }}
          aria-label="Search"
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
