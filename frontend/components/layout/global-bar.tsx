"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Bell, LogOut, Menu as MenuIcon, Search, Settings, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Menu,
  MenuContent,
  MenuItem,
  MenuLabel,
  MenuRadioGroup,
  MenuRadioItem,
  MenuSeparator,
  MenuTrigger,
} from "@/components/ui/menu";
import { useTheme } from "@/components/ui/theme";
import { parseThemePreference } from "@/components/ui/theme-core";
import { RECENT_RECEIPTS_SIZE, useRecentReceipts } from "@/features/receipts/hooks";
import { countDrafts } from "@/features/today/logic";
import { useAuthStore } from "@/store/auth";
import { Logo } from "./logo";
import { RECEIPT_INBOX_HREF, SETTINGS_ITEM } from "./nav";
import { useSignOut } from "./use-sign-out";

interface GlobalBarProps {
  onOpenNav: () => void;
  onOpenSearch: () => void;
  onOpenAssistant: () => void;
  assistantOpen: boolean;
}

// The label depends on the platform, so it is filled in after hydration.
function useShortcutLabel(key: string): string {
  const [label, setLabel] = useState(`Ctrl ${key}`);
  useEffect(() => {
    if (/mac|iphone|ipad/i.test(navigator.platform)) setLabel(`⌘${key}`);
  }, [key]);
  return label;
}

/** The 60px global bar: logo, search, assistant, notifications and the account menu. */
export function GlobalBar({ onOpenNav, onOpenSearch, onOpenAssistant, assistantOpen }: GlobalBarProps) {
  const searchKeys = useShortcutLabel("K");
  const askKeys = useShortcutLabel("J");

  return (
    <header className="flex h-bar shrink-0 items-center gap-2 bg-chrome px-3 sm:px-6 lg:px-8">
      <Button
        variant="ghost"
        size="icon"
        onClick={onOpenNav}
        aria-label="Open navigation"
        className="-ml-2 text-ink-nav lg:hidden"
      >
        <MenuIcon className="!size-5" strokeWidth={1.75} aria-hidden="true" />
      </Button>

      <Link href="/dashboard" aria-label="FlowSight home" className="flex shrink-0 items-center gap-2.5 rounded-sm">
        <Logo />
        <span className="hidden text-ty-link font-medium text-ink sm:inline">FlowSight</span>
      </Link>

      <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-2">
        <button
          type="button"
          onClick={onOpenSearch}
          aria-keyshortcuts="Control+K Meta+K"
          className="press inline-flex h-11 items-center gap-2 rounded-md px-3 text-sm text-ink-nav hover:bg-band-blue md:h-10 md:w-64 md:justify-start md:border md:border-input md:bg-canvas md:text-muted-foreground md:hover:bg-canvas md:hover:border-ink"
        >
          <Search className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
          <span className="sr-only">Search and commands</span>
          <span aria-hidden="true" className="hidden flex-1 text-left md:inline">Search</span>
          <kbd className="kbd-hint hidden md:inline-flex" aria-hidden="true">
            {searchKeys}
          </kbd>
        </button>

        <button
          type="button"
          onClick={onOpenAssistant}
          aria-expanded={assistantOpen}
          aria-keyshortcuts="Control+J Meta+J"
          className="press inline-flex h-11 items-center gap-2 rounded-md px-3 text-sm text-ink-nav hover:bg-band-blue md:h-10"
        >
          <Sparkles className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
          <span className="sr-only lg:not-sr-only">Ask FlowSight</span>
          <kbd className="kbd-hint hidden lg:inline-flex" aria-hidden="true">
            {askKeys}
          </kbd>
        </button>

        <Notifications />
        <AccountMenu />
      </div>
    </header>
  );
}

function Notifications() {
  const receipts = useRecentReceipts();
  const drafts = receipts.data ? countDrafts(receipts.data.content) : 0;
  const shown = drafts >= RECENT_RECEIPTS_SIZE ? `${RECENT_RECEIPTS_SIZE}+` : String(drafts);

  return (
    <Menu>
      <MenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={drafts > 0 ? `Notifications, ${shown} new` : "Notifications"} className="relative text-ink-nav">
          <Bell className="!size-5" strokeWidth={1.75} aria-hidden="true" />
          {drafts > 0 && (
            <span
              aria-hidden="true"
              className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-sm bg-primary px-1 text-2xs font-medium tabular-nums text-primary-foreground"
            >
              {shown}
            </span>
          )}
        </Button>
      </MenuTrigger>
      <MenuContent>
        <MenuLabel>Notifications</MenuLabel>
        {drafts > 0 ? (
          <MenuItem asChild>
            <Link href={RECEIPT_INBOX_HREF}>
              {drafts === 1 ? "1 receipt is" : `${shown} receipts are`} waiting for your review
            </Link>
          </MenuItem>
        ) : (
          <p className="px-2.5 py-3 text-sm text-muted-foreground">Nothing needs you right now.</p>
        )}
      </MenuContent>
    </Menu>
  );
}

function AccountMenu() {
  const user = useAuthStore((s) => s.user);
  const signOut = useSignOut();
  const { preference, setPreference } = useTheme();
  const initials = user?.fullName
    ? user.fullName.split(" ").map((s) => s[0]).slice(0, 2).join("").toUpperCase()
    : "U";

  return (
    <Menu>
      <MenuTrigger asChild>
        <button
          type="button"
          className="press inline-flex h-11 w-11 items-center justify-center rounded-md hover:bg-band-blue"
        >
          <span
            aria-hidden="true"
            className="flex h-8 w-8 select-none items-center justify-center rounded-md bg-primary text-xs font-medium text-primary-foreground"
          >
            {initials}
          </span>
          <span className="sr-only">Account menu</span>
        </button>
      </MenuTrigger>
      <MenuContent>
        <div className="px-2.5 pb-2 pt-1.5">
          <p className="truncate text-sm font-medium text-ink">{user?.fullName ?? "Guest"}</p>
          <p className="truncate text-xs text-muted-foreground">{user?.email ?? ""}</p>
        </div>
        <MenuSeparator />
        <MenuItem asChild>
          <Link href={SETTINGS_ITEM.href}>
            <Settings className="h-4 w-4 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
            {SETTINGS_ITEM.label}
          </Link>
        </MenuItem>
        <MenuSeparator />
        <MenuLabel>Theme</MenuLabel>
        <MenuRadioGroup value={preference} onValueChange={(v) => setPreference(parseThemePreference(v))}>
          <MenuRadioItem value="light">Light</MenuRadioItem>
          <MenuRadioItem value="dark">Dark</MenuRadioItem>
          <MenuRadioItem value="system">Match device</MenuRadioItem>
        </MenuRadioGroup>
        <MenuSeparator />
        <MenuItem onSelect={signOut}>
          <LogOut className="h-4 w-4 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
          Sign out
        </MenuItem>
      </MenuContent>
    </Menu>
  );
}
