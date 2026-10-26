"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CornerDownLeft, Monitor, Moon, Search, Settings, Sparkles, Sun } from "lucide-react";
import { useAssistant } from "@/components/assistant/assistant-context";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { useTheme } from "@/components/ui/theme";
import type { ThemePreference } from "@/components/ui/theme-core";
import { cn } from "@/lib/utils";
import { SETTINGS_ITEM, captureActions, navSections } from "./nav";

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: React.ElementType;
  section: string;
  href?: string;
  theme?: ThemePreference;
  assistant?: boolean;
}

const THEMES: { value: ThemePreference; label: string; icon: React.ElementType }[] = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "Match device", icon: Monitor },
];

// capture first, then every destination, then appearance
const allCommands: Command[] = [
  { id: "assistant", label: "Ask FlowSight", hint: "Questions about your spending", icon: Sparkles, assistant: true, section: "Assistant" },
  ...captureActions.map((a) => ({ id: `capture:${a.href}`, label: a.label, hint: a.hint, icon: a.icon, href: a.href, section: "Capture" })),
  ...navSections.flatMap((s) =>
    s.items
      .filter((i) => !captureActions.includes(i))
      .map((i) => ({ id: `go:${i.href}`, label: i.label, hint: i.hint, icon: i.icon, href: i.href, section: "Go to" }))
  ),
  { id: `go:${SETTINGS_ITEM.href}`, label: SETTINGS_ITEM.label, icon: Settings, href: SETTINGS_ITEM.href, section: "Go to" },
  ...THEMES.map((t) => ({ id: `theme:${t.value}`, label: `Theme: ${t.label}`, icon: t.icon, theme: t.value, section: "Appearance" })),
];

function filterCommands<T extends { label: string; hint?: string; section: string }>(commands: T[], query: string): T[] {
  const q = query.trim().toLowerCase();
  if (!q) return commands;
  return commands.filter(
    (c) => c.label.toLowerCase().includes(q) || c.hint?.toLowerCase().includes(q) || c.section.toLowerCase().includes(q)
  );
}

/** ⌘K palette over the app's own destinations and settings; no network. */
export function CommandMenu({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const { preference, setPreference } = useTheme();
  const assistant = useAssistant();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  const results = useMemo(() => filterCommands(allCommands, query), [query]);

  useEffect(() => {
    if (open) {
      setQuery("");
      setActive(0);
    }
  }, [open]);

  useEffect(() => setActive(0), [query]);

  useEffect(() => {
    listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const run = (cmd: Command) => {
    onClose();
    if (cmd.assistant) assistant.setOpen(true);
    else if (cmd.theme) setPreference(cmd.theme);
    else if (cmd.href) router.push(cmd.href);
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Home") {
      e.preventDefault();
      setActive(0);
    } else if (e.key === "End") {
      e.preventDefault();
      setActive(Math.max(results.length - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const cmd = results[active];
      if (cmd) run(cmd);
    }
  };

  let lastSection = "";

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent
        hideClose
        className="top-[14vh] max-w-lg translate-y-0 overflow-hidden p-0 sm:p-0"
      >
        <DialogTitle className="sr-only">Search and commands</DialogTitle>
        <DialogDescription className="sr-only">
          Type to filter, use the arrow keys to choose, Enter to run.
        </DialogDescription>
        <div className="flex items-center gap-2.5 border-b border-border px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Where to?"
            aria-label="Search pages and commands"
            className="h-12 w-full bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
            role="combobox"
            aria-expanded="true"
            aria-autocomplete="list"
            aria-controls="command-menu-list"
            aria-activedescendant={results[active] ? `command-item-${active}` : undefined}
          />
          <kbd className="kbd-hint shrink-0">esc</kbd>
        </div>

        <div ref={listRef} id="command-menu-list" role="listbox" aria-label="Results" className="max-h-[min(360px,60dvh)] overflow-y-auto p-2">
          {results.length === 0 && (
            <p className="px-3 py-8 text-center text-sm text-muted-foreground">Nothing matches &ldquo;{query}&rdquo;</p>
          )}
          {results.map((cmd, i) => {
            const showSection = cmd.section !== lastSection;
            lastSection = cmd.section;
            const current = cmd.theme != null && cmd.theme === preference;
            return (
              <div key={cmd.id} role="presentation">
                {showSection && (
                  <p role="presentation" className="px-3 pb-1.5 pt-3 text-overline first:pt-1.5">
                    {cmd.section}
                  </p>
                )}
                <div
                  id={`command-item-${i}`}
                  data-index={i}
                  role="option"
                  aria-selected={i === active}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => run(cmd)}
                  className={cn(
                    "flex cursor-default items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm transition-colors duration-fast",
                    i === active ? "bg-band-blue text-ink" : "text-ink-body"
                  )}
                >
                  <cmd.icon className="h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
                  <span className="flex-1 truncate font-medium">{cmd.label}</span>
                  {current && <span className="text-xs text-muted-foreground">Current</span>}
                  {cmd.hint && <span className="hidden truncate text-xs text-muted-foreground sm:block">{cmd.hint}</span>}
                  {i === active && <CornerDownLeft className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />}
                </div>
              </div>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
