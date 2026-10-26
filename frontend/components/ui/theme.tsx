"use client";

import * as React from "react";
import { Monitor, Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  DEFAULT_THEME,
  THEME_STORAGE_KEY,
  parseThemePreference,
  resolveTheme,
  type ResolvedTheme,
  type ThemePreference,
} from "./theme-core";

interface ThemeContextValue {
  preference: ThemePreference;
  resolved: ResolvedTheme;
  setPreference: (preference: ThemePreference) => void;
  // false until the stored preference has been read on the client
  ready: boolean;
}

const ThemeContext = React.createContext<ThemeContextValue | null>(null);

const DARK_QUERY = "(prefers-color-scheme: dark)";

function readStoredPreference(): ThemePreference {
  try {
    return parseThemePreference(window.localStorage.getItem(THEME_STORAGE_KEY));
  } catch {
    return DEFAULT_THEME;
  }
}

function applyTheme(theme: ResolvedTheme) {
  const root = document.documentElement;
  // Suppress transitions for one frame so every surface switches at once.
  const style = document.createElement("style");
  style.textContent = "*,*::before,*::after{transition:none!important}";
  document.head.appendChild(style);
  root.classList.toggle("dark", theme === "dark");
  root.style.colorScheme = theme;
  void window.getComputedStyle(root).opacity;
  requestAnimationFrame(() => style.remove());
}

/**
 * Holds the light/dark/system preference. The inline THEME_INIT_SCRIPT in the
 * root layout applies the stored theme before paint; this provider keeps it in
 * sync afterwards (toggle, OS change, other tabs).
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, setPreferenceState] = React.useState<ThemePreference>(DEFAULT_THEME);
  const [systemDark, setSystemDark] = React.useState(false);
  const [ready, setReady] = React.useState(false);

  React.useEffect(() => {
    setPreferenceState(readStoredPreference());
    const media = window.matchMedia(DARK_QUERY);
    setSystemDark(media.matches);
    setReady(true);

    const onMedia = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    const onStorage = (e: StorageEvent) => {
      if (e.key === THEME_STORAGE_KEY) setPreferenceState(parseThemePreference(e.newValue));
    };
    media.addEventListener("change", onMedia);
    window.addEventListener("storage", onStorage);
    return () => {
      media.removeEventListener("change", onMedia);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  const resolved = resolveTheme(preference, systemDark);

  React.useEffect(() => {
    if (ready) applyTheme(resolved);
  }, [ready, resolved]);

  const setPreference = React.useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // storage blocked: the choice still applies for this session
    }
  }, []);

  const value = React.useMemo(
    () => ({ preference, resolved, setPreference, ready }),
    [preference, resolved, setPreference, ready]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = React.useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used inside <ThemeProvider>");
  return ctx;
}

const OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

/** Three-way theme switch. `compact` hides the text labels (they stay available to screen readers). */
export function ThemeToggle({ compact, className }: { compact?: boolean; className?: string }) {
  const { preference, setPreference, ready } = useTheme();
  return (
    <div
      role="group"
      aria-label="Color theme"
      className={cn("inline-flex items-center gap-0.5 rounded-md bg-band p-0.5", className)}
    >
      {OPTIONS.map(({ value, label, icon: Icon }) => {
        const active = ready && preference === value;
        return (
          <button
            key={value}
            type="button"
            aria-pressed={active}
            onClick={() => setPreference(value)}
            className={cn(
              "inline-flex h-7 coarse:h-11 items-center gap-1.5 rounded-sm px-2 text-xs font-medium transition-colors duration-fast",
              active
                ? "bg-card text-primary"
                : "text-muted-foreground hover:text-ink"
            )}
          >
            <Icon className="h-3.5 w-3.5" strokeWidth={1.75} aria-hidden="true" />
            <span className={compact ? "sr-only" : undefined}>{label}</span>
          </button>
        );
      })}
    </div>
  );
}
