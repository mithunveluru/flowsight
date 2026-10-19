import {
  BarChart3,
  Brain,
  CreditCard,
  FileText,
  Flag,
  Inbox,
  PenLine,
  Receipt,
  Repeat,
  Sliders,
  Sun,
  Target,
  TrendingDown,
  Upload,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  // equals the page's h1
  label: string;
  href: string;
  icon: LucideIcon;
  // short description for the command menu
  hint?: string;
}

export interface NavSection {
  section: string | null;
  accent: string;
  items: NavItem[];
}

// The one navigation source for the sidebar, mobile drawer, command menu and breadcrumb.
export const navSections: NavSection[] = [
  {
    section: null,
    accent: "var(--accent-overview)",
    items: [{ label: "Today", href: "/dashboard", icon: Sun, hint: "What changed and what needs you" }],
  },
  {
    section: "Capture",
    accent: "var(--accent-receipts)",
    items: [
      { label: "Scan receipt", href: "/dashboard/capture/receipt", icon: Receipt, hint: "Photo to draft, you confirm" },
      { label: "Import statement", href: "/dashboard/capture/import", icon: Upload, hint: "Bank statement CSV" },
      { label: "Add manually", href: "/dashboard/capture/manual", icon: PenLine, hint: "Record a purchase or income" },
      { label: "Receipt inbox", href: "/dashboard/receipts", icon: Inbox, hint: "Drafts awaiting review" },
    ],
  },
  {
    section: "Understand",
    accent: "var(--accent-insights)",
    items: [
      { label: "Transactions", href: "/dashboard/transactions", icon: CreditCard },
      { label: "Trends", href: "/dashboard/trends", icon: BarChart3 },
      { label: "Habits", href: "/dashboard/habits", icon: Brain },
    ],
  },
  {
    section: "Act",
    accent: "var(--accent-leaks)",
    items: [
      { label: "Money drains", href: "/dashboard/drains", icon: TrendingDown },
      { label: "Subscriptions", href: "/dashboard/subscriptions", icon: Repeat },
      { label: "Budgets", href: "/dashboard/budgets", icon: Target },
    ],
  },
  {
    section: "Plan",
    accent: "var(--accent-goals)",
    items: [
      { label: "Goals", href: "/dashboard/goals", icon: Flag },
      { label: "What-if", href: "/dashboard/what-if", icon: Sliders },
      { label: "Reports", href: "/dashboard/reports", icon: FileText },
    ],
  },
];

export const SETTINGS_ITEM = { label: "Settings", href: "/dashboard/settings" } as const;

export const RECEIPT_INBOX_HREF = "/dashboard/receipts";

// The three ways in, used by the capture split button and the command menu.
export const captureActions: NavItem[] = navSections[1].items.filter((i) => i.href.startsWith("/dashboard/capture/"));

export function isNavItemActive(pathname: string | null, href: string): boolean {
  if (!pathname) return false;
  if (href === "/dashboard") return pathname === "/dashboard";
  return pathname === href || pathname.startsWith(`${href}/`);
}

// Breadcrumb for the header: section and page label, or null off the map.
export function locate(pathname: string | null): { section: string | null; label: string } | null {
  if (!pathname) return null;
  if (isNavItemActive(pathname, SETTINGS_ITEM.href)) return { section: null, label: SETTINGS_ITEM.label };
  if (pathname === "/dashboard/welcome") return { section: null, label: "Welcome" };
  for (const group of navSections) {
    for (const item of group.items) {
      if (isNavItemActive(pathname, item.href)) return { section: group.section, label: item.label };
    }
  }
  return null;
}

const ACCENTS: [string, string][] = [
  ["/dashboard/capture", "var(--accent-receipts)"],
  ["/dashboard/receipts", "var(--accent-receipts)"],
  ["/dashboard/transactions", "var(--accent-transactions)"],
  ["/dashboard/trends", "var(--accent-insights)"],
  ["/dashboard/habits", "var(--accent-insights)"],
  ["/dashboard/drains", "var(--accent-leaks)"],
  ["/dashboard/subscriptions", "var(--accent-recurring)"],
  ["/dashboard/budgets", "var(--accent-budgets)"],
  ["/dashboard/goals", "var(--accent-goals)"],
  ["/dashboard/what-if", "var(--accent-simulate)"],
  ["/dashboard/reports", "var(--accent-reports)"],
];

export function accentForPath(pathname: string | null): string {
  if (!pathname) return "var(--brand)";
  const match = ACCENTS.find(([prefix]) => isNavItemActive(pathname, prefix));
  return match ? match[1] : "var(--accent-overview)";
}
