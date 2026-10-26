"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut, Settings } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { RECENT_RECEIPTS_SIZE, useRecentReceipts } from "@/features/receipts/hooks";
import { countDrafts } from "@/features/today/logic";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { Logo } from "./logo";
import { RECEIPT_INBOX_HREF, SETTINGS_ITEM, isNavItemActive, navSections } from "./nav";
import { useSignOut } from "./use-sign-out";

/** Full navigation for narrow screens: every section and route, the same source as the rail and palette. */
export function NavDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent
        side="left"
        className="w-[min(82vw,288px)] bg-chrome sm:max-w-none"
      >
        <SheetTitle className="sr-only">Navigation</SheetTitle>
        <DrawerContent onNavigate={onClose} />
      </SheetContent>
    </Sheet>
  );
}

function DrawerContent({ onNavigate }: { onNavigate: () => void }) {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const signOut = useSignOut();
  const receipts = useRecentReceipts();
  const drafts = receipts.data ? countDrafts(receipts.data.content) : 0;
  const settingsActive = isNavItemActive(pathname, SETTINGS_ITEM.href);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex h-bar shrink-0 items-center gap-2.5 px-5">
        <Logo />
        <span className="text-ty-link font-medium text-ink">FlowSight</span>
      </div>

      <nav aria-label="All pages" className="flex-1 overflow-y-auto px-3 pb-4 pt-2">
        {navSections.map((group) => {
          const headingId = group.section ? `nav-${group.section.toLowerCase()}` : undefined;
          return (
            <div key={group.section ?? "home"} className="mb-6 last:mb-0">
              {group.section && (
                <h2 id={headingId} className="nav-section-label">
                  {group.section}
                </h2>
              )}
              <ul className="space-y-px" aria-labelledby={headingId}>
                {group.items.map((item) => {
                  const active = isNavItemActive(pathname, item.href);
                  const badge = item.href === RECEIPT_INBOX_HREF && drafts > 0 ? drafts : null;
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        onClick={onNavigate}
                        aria-current={active ? "page" : undefined}
                        className={cn("nav-item", active ? "nav-item-active" : "nav-item-default")}
                      >
                        <item.icon className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
                        <span className="flex-1 truncate">{item.label}</span>
                        {badge != null && (
                          <span className="ml-auto rounded-sm bg-band-blue px-1.5 text-xs font-medium tabular-nums text-primary">
                            {badge >= RECENT_RECEIPTS_SIZE ? `${RECENT_RECEIPTS_SIZE}+` : badge}
                            <span className="sr-only"> {badge === 1 ? "draft" : "drafts"} awaiting review</span>
                          </span>
                        )}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
      </nav>

      <div className="border-t border-border px-3 py-3">
        <Link
          href={SETTINGS_ITEM.href}
          onClick={onNavigate}
          aria-current={settingsActive ? "page" : undefined}
          className={cn("nav-item", settingsActive ? "nav-item-active" : "nav-item-default")}
        >
          <Settings className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
          <span className="min-w-0 flex-1">
            <span className="block truncate">{SETTINGS_ITEM.label}</span>
            {user?.email && <span className="block truncate text-xs font-normal text-muted-foreground">{user.email}</span>}
          </span>
        </Link>
        <button type="button" onClick={signOut} className="nav-item nav-item-default w-full">
          <LogOut className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />
          Sign out
        </button>
      </div>
    </div>
  );
}
