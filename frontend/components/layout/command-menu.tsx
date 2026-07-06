"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CornerDownLeft, Plus, Receipt, Search } from "lucide-react";
import { navSections } from "./sidebar";
import { cn } from "@/lib/utils";

interface Command {
  label: string;
  hint?: string;
  href: string;
  icon: React.ElementType;
  section: string;
}

// quick actions surface first; navigation follows
const quickActions: Command[] = [
  { label: "Add transaction", hint: "Record a purchase or income", href: "/dashboard/transactions/new", icon: Plus, section: "Actions" },
  { label: "Scan receipt", hint: "Upload and extract a receipt", href: "/dashboard/receipts/upload", icon: Receipt, section: "Actions" },
];

const allCommands: Command[] = [
  ...quickActions,
  ...navSections.flatMap((s) =>
    s.items.map((i) => ({ label: i.label, href: i.href, icon: i.icon, section: "Go to" }))
  ),
];

// Lightweight command palette (⌘K). Pure keyboard-first navigation:
// no network, no new dependencies — filters the app's own destinations.
export function CommandMenu({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return allCommands;
    return allCommands.filter(
      (c) =>
        c.label.toLowerCase().includes(q) ||
        c.hint?.toLowerCase().includes(q) ||
        c.section.toLowerCase().includes(q)
    );
  }, [query]);

  // reset + focus on open
  useEffect(() => {
    if (open) {
      setQuery("");
      setActive(0);
      // focus after paint so the panel exists
      requestAnimationFrame(() => inputRef.current?.focus());
      document.body.style.overflow = "hidden";
      return () => { document.body.style.overflow = ""; };
    }
  }, [open]);

  useEffect(() => { setActive(0); }, [query]);

  const run = useCallback(
    (cmd: Command) => {
      onClose();
      router.push(cmd.href);
    },
    [onClose, router]
  );

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const cmd = results[active];
      if (cmd) run(cmd);
    } else if (e.key === "Escape") {
      e.preventDefault();
      onClose();
    }
  };

  // keep the active row visible while arrowing
  useEffect(() => {
    listRef.current
      ?.querySelector(`[data-index="${active}"]`)
      ?.scrollIntoView({ block: "nearest" });
  }, [active]);

  if (!open) return null;

  let lastSection = "";

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center px-4 pt-[18vh]"
      role="dialog"
      aria-modal="true"
      aria-label="Command menu"
    >
      {/* backdrop */}
      <div
        className="animate-fade-in absolute inset-0 bg-foreground/25 backdrop-blur-[2px]"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* panel */}
      <div className="elev-3 animate-slide-up relative w-full max-w-lg overflow-hidden rounded-xl bg-popover">
        <div className="flex items-center gap-2.5 border-b border-border px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground/70" strokeWidth={1.75} />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Where to?"
            className="h-12 w-full bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
            role="combobox"
            aria-expanded="true"
            aria-controls="command-menu-list"
            aria-activedescendant={results[active] ? `command-item-${active}` : undefined}
          />
          <kbd className="kbd-hint shrink-0">esc</kbd>
        </div>

        <div
          ref={listRef}
          id="command-menu-list"
          role="listbox"
          className="max-h-[320px] overflow-y-auto p-2"
        >
          {results.length === 0 && (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">
              Nothing matches &ldquo;{query}&rdquo;
            </p>
          )}
          {results.map((cmd, i) => {
            const showSection = cmd.section !== lastSection;
            lastSection = cmd.section;
            return (
              <div key={cmd.href + cmd.label}>
                {showSection && (
                  <p className="px-3 pb-1.5 pt-3 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground/70 first:pt-1.5">
                    {cmd.section}
                  </p>
                )}
                <button
                  id={`command-item-${i}`}
                  data-index={i}
                  role="option"
                  aria-selected={i === active}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => run(cmd)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition-colors",
                    i === active
                      ? "bg-muted text-foreground"
                      : "text-foreground/80"
                  )}
                >
                  <cmd.icon
                    className="h-4 w-4 shrink-0 text-muted-foreground/70"
                    strokeWidth={1.75}
                  />
                  <span className="flex-1 truncate font-medium">{cmd.label}</span>
                  {cmd.hint && (
                    <span className="hidden truncate text-xs text-muted-foreground/70 sm:block">
                      {cmd.hint}
                    </span>
                  )}
                  {i === active && (
                    <CornerDownLeft className="h-3.5 w-3.5 shrink-0 text-muted-foreground/50" />
                  )}
                </button>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
