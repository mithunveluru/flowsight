"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  BarChart3,
  Brain,
  CreditCard,
  FileText,
  Flag,
  LayoutDashboard,
  LogOut,
  Receipt,
  Repeat,
  Settings,
  Sliders,
  Target,
  TrendingDown,
  X,
} from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/utils";

/**
 * Each nav section carries an `accent` token name. The sidebar passes it
 * down as a CSS variable (`--section-accent`) so the active and hover
 * indicators within that area pick up the section's identity color.
 * Defined in globals.css under :root.
 */
const navSections = [
  {
    section: "Overview",
    accent: "var(--accent-overview)",
    items: [
      { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
    ],
  },
  {
    section: "Finances",
    accent: "var(--accent-transactions)",
    items: [
      { label: "Transactions", href: "/dashboard/transactions", icon: CreditCard },
      { label: "Receipts",     href: "/dashboard/receipts",     icon: Receipt    },
    ],
  },
  {
    section: "Planning",
    accent: "var(--accent-budgets)",
    items: [
      { label: "Budgets", href: "/dashboard/budgets", icon: Target },
      { label: "Goals",   href: "/dashboard/goals",   icon: Flag   },
    ],
  },
  {
    section: "Understand",
    accent: "var(--accent-insights)",
    items: [
      { label: "Trends",        href: "/dashboard/analytics", icon: BarChart3    },
      { label: "Subscriptions", href: "/dashboard/recurring", icon: Repeat       },
      { label: "Money drains",  href: "/dashboard/leaks",     icon: TrendingDown },
      { label: "Habits",        href: "/dashboard/insights",  icon: Brain        },
      { label: "What-if",       href: "/dashboard/simulate",  icon: Sliders      },
    ],
  },
  {
    section: "Reports",
    accent: "var(--accent-reports)",
    items: [
      { label: "Reports", href: "/dashboard/reports", icon: FileText },
    ],
  },
];

export function Sidebar({ open = false, onClose }: { open?: boolean; onClose?: () => void } = {}) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, clearAuth } = useAuthStore();

  // Auto-close the mobile drawer on route change; on desktop the prop has no
  // effect because the static layer ignores `open`/`onClose`.
  useEffect(() => {
    if (open && onClose) onClose();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  // Lock body scroll while the mobile drawer is open.
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = prev; };
  }, [open]);

  const handleSignOut = () => {
    clearAuth();
    router.replace("/auth/login");
  };

  const initials = user?.fullName
    ? user.fullName.split(" ").map((s) => s[0]).slice(0, 2).join("").toUpperCase()
    : "U";

  return (
    <>
      {/* Mobile scrim — covers the rest of the viewport while the drawer is open */}
      <div
        onClick={onClose}
        aria-hidden="true"
        className={cn(
          "fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-[2px] transition-opacity duration-200 lg:hidden",
          open ? "opacity-100" : "pointer-events-none opacity-0"
        )}
      />

      <aside
        aria-label="Primary navigation"
        className={cn(
          // Mobile: fixed off-canvas drawer that slides in from the left.
          "fixed left-0 top-0 z-50 flex h-full w-[min(82vw,288px)] flex-col border-r",
          "transition-transform duration-250 ease-out will-change-transform",
          open ? "translate-x-0" : "-translate-x-full",
          // Desktop: static, in-flow sidebar that always shows.
          "lg:static lg:z-auto lg:w-sidebar lg:shrink-0 lg:translate-x-0 lg:transition-none"
        )}
        style={{
          backgroundColor: "hsl(var(--sidebar-bg))",
          borderColor: "hsl(var(--sidebar-border))",
        }}
      >
        <div className="flex h-header shrink-0 items-center gap-2.5 px-5">
          <Logo />
          <span className="text-[15px] font-semibold tracking-tight text-foreground">
            FlowSight
          </span>
          {onClose && (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close navigation"
              className="ml-auto -mr-1 inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
            >
              <X className="h-4 w-4" strokeWidth={1.75} />
            </button>
          )}
        </div>

      <nav className="flex-1 overflow-y-auto px-3 pt-2 pb-4">
        {navSections.map((group) => (
          <div
            key={group.section}
            className="mb-6 last:mb-0"
            style={{ ["--section-accent" as string]: group.accent }}
          >
            <div className="nav-section-label">{group.section}</div>
            <ul className="space-y-px">
              {group.items.map((item) => {
                const active =
                  pathname === item.href ||
                  (item.href !== "/dashboard" && pathname.startsWith(item.href));
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      className={cn(
                        "nav-item",
                        active ? "nav-item-active" : "nav-item-default"
                      )}
                    >
                      <item.icon className="h-[15px] w-[15px] shrink-0" strokeWidth={1.75} />
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div
        className="border-t px-3 py-3"
        style={{ borderColor: "hsl(var(--sidebar-border))" }}
      >
        <Link
          href="/dashboard/settings"
          className="flex items-center gap-2.5 rounded-md px-2 py-2 transition-colors hover:bg-muted"
        >
          <div
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold text-white select-none"
            style={{ backgroundColor: "hsl(var(--primary))" }}
          >
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium text-foreground truncate">
              {user?.fullName ?? "Guest"}
            </p>
            <p className="text-[11px] text-muted-foreground truncate">
              {user?.email ?? ""}
            </p>
          </div>
          <Settings className="h-3.5 w-3.5 shrink-0 text-muted-foreground" strokeWidth={1.75} />
        </Link>
        <button
          onClick={handleSignOut}
          className="mt-1 flex w-full items-center gap-2.5 rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <LogOut className="h-[15px] w-[15px] shrink-0" strokeWidth={1.75} />
          Sign out
        </button>
      </div>
      </aside>
    </>
  );
}

function Logo() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 22 22"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <rect
        x="0.5"
        y="0.5"
        width="21"
        height="21"
        rx="6"
        fill="hsl(var(--primary))"
      />
      <path
        d="M5.5 14.75L9 10.75L12 13.25L16.5 7.75"
        stroke="white"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="16.5" cy="7.75" r="1.25" fill="white" />
    </svg>
  );
}
