"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Settings } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { authApi } from "@/features/auth/api";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { SETTINGS_ITEM, isNavItemActive, navSections } from "./nav";

export function Sidebar({ open = false, onClose }: { open?: boolean; onClose?: () => void } = {}) {
  return (
    <>
      <aside
        aria-label="Sidebar"
        className="hidden w-sidebar shrink-0 flex-col border-r lg:flex"
        style={{
          backgroundColor: "hsl(var(--sidebar-bg))",
          borderColor: "hsl(var(--sidebar-border))",
        }}
      >
        <SidebarContent />
      </aside>

      <Sheet open={open} onOpenChange={(next) => !next && onClose?.()}>
        <SheetContent
          side="left"
          className="w-[min(82vw,288px)] sm:max-w-none lg:hidden"
          style={{ backgroundColor: "hsl(var(--sidebar-bg))" }}
        >
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <SidebarContent onNavigate={onClose} />
        </SheetContent>
      </Sheet>
    </>
  );
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, clearAuth } = useAuthStore();

  const handleSignOut = () => {
    // revoke the refresh token server-side; local logout proceeds regardless
    const refreshToken = useAuthStore.getState().refreshToken;
    if (refreshToken) void authApi.logout(refreshToken).catch(() => {});
    clearAuth();
    router.replace("/auth/login");
  };

  const initials = user?.fullName
    ? user.fullName.split(" ").map((s) => s[0]).slice(0, 2).join("").toUpperCase()
    : "U";
  const settingsActive = isNavItemActive(pathname, SETTINGS_ITEM.href);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex h-header shrink-0 items-center gap-2.5 px-5">
        <Logo />
        <span className="text-[15px] font-semibold tracking-tight text-foreground">FlowSight</span>
      </div>

      <nav aria-label="Main" className="flex-1 overflow-y-auto px-3 pb-4 pt-2">
        {navSections.map((group) => {
          const headingId = group.section ? `nav-${group.section.toLowerCase()}` : undefined;
          return (
            <div
              key={group.section ?? "home"}
              className="mb-6 last:mb-0"
              style={{ ["--section-accent" as string]: group.accent }}
            >
              {group.section && (
                <h2 id={headingId} className="nav-section-label">
                  {group.section}
                </h2>
              )}
              <ul className="space-y-px" aria-labelledby={headingId}>
                {group.items.map((item) => {
                  const active = isNavItemActive(pathname, item.href);
                                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        onClick={onNavigate}
                        aria-current={active ? "page" : undefined}
                        className={cn("nav-item", active ? "nav-item-active" : "nav-item-default")}
                      >
                        <item.icon className="h-[15px] w-[15px] shrink-0" strokeWidth={1.75} aria-hidden="true" />
                        <span className="flex-1 truncate">{item.label}</span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          );
        })}
      </nav>

      <div className="border-t px-3 py-3" style={{ borderColor: "hsl(var(--sidebar-border))" }}>
        <Link
          href={SETTINGS_ITEM.href}
          onClick={onNavigate}
          aria-current={settingsActive ? "page" : undefined}
          className={cn(
            "flex items-center gap-2.5 rounded-md px-2 py-2 transition-colors hover:bg-muted",
            settingsActive && "bg-muted"
          )}
        >
          <div
            aria-hidden="true"
            className="flex h-7 w-7 shrink-0 select-none items-center justify-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground"
          >
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-medium text-foreground">{user?.fullName ?? "Guest"}</p>
            <p className="truncate text-[11px] text-muted-foreground">{user?.email ?? ""}</p>
          </div>
          <Settings className="h-3.5 w-3.5 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
          <span className="sr-only">{SETTINGS_ITEM.label}</span>
        </Link>
        <button
          type="button"
          onClick={handleSignOut}
          className="mt-1 flex w-full items-center gap-2.5 rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <LogOut className="h-[15px] w-[15px] shrink-0" strokeWidth={1.75} aria-hidden="true" />
          Sign out
        </button>
      </div>
    </div>
  );
}

function Logo() {
  return (
    <svg width="22" height="22" viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="0.5" y="0.5" width="21" height="21" rx="6" fill="hsl(var(--primary))" />
      <path
        d="M5.5 14.75L9 10.75L12 13.25L16.5 7.75"
        stroke="hsl(var(--primary-foreground))"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="16.5" cy="7.75" r="1.25" fill="hsl(var(--primary-foreground))" />
    </svg>
  );
}
