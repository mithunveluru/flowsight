"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  Crown,
  Gauge,
  LogOut,
  Mail,
  Shield,
  Sparkles,
  User as UserIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { accountApi } from "@/features/account/api";
import type {
  Account,
  AuditLogEntry,
  AuditLogPage,
  SubscriptionTier,
} from "@/features/account/types";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/utils";

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function fmtUsage(used: number, limit: number) {
  if (limit >= Number.MAX_SAFE_INTEGER || limit === 2147483647) return `${used} of ∞`;
  return `${used} of ${limit}`;
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  });
}

function fmtTimestamp(iso: string) {
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

const TIER_META: Record<SubscriptionTier, { icon: React.ElementType; bg: string; fg: string }> = {
  FREE:       { icon: UserIcon, bg: "bg-muted",        fg: "text-foreground/80" },
  PRO:        { icon: Sparkles, bg: "bg-blue-50",      fg: "text-blue-700" },
  ENTERPRISE: { icon: Crown,    bg: "bg-violet-50",    fg: "text-violet-700" },
};

const ACTION_LABELS: Record<string, string> = {
  USER_REGISTERED:     "Account created",
  USER_LOGIN:          "Signed in",
  USER_LOGIN_FAILED:   "Failed sign-in attempt",
  TRANSACTION_CREATED: "Transaction added",
  TRANSACTION_DELETED: "Transaction deleted",
  CSV_IMPORTED:        "CSV imported",
  RECEIPT_UPLOADED:    "Receipt uploaded",
  RECEIPT_CONFIRMED:   "Receipt confirmed",
  BUDGET_CREATED:      "Budget created",
  GOAL_CREATED:        "Goal created",
};

// -------------------------------------------------------------------------
// Page
// -------------------------------------------------------------------------

export default function SettingsPage() {
  const router = useRouter();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const [account, setAccount] = useState<Account | null>(null);
  const [auditLog, setAuditLog] = useState<AuditLogPage | null>(null);
  const [auditPage, setAuditPage] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([accountApi.get(), accountApi.getAuditLog(0)])
      .then(([a, l]) => { setAccount(a); setAuditLog(l); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (auditPage === 0) return;
    accountApi.getAuditLog(auditPage).then(setAuditLog).catch(() => {});
  }, [auditPage]);

  const handleSignOut = () => {
    clearAuth();
    router.replace("/auth/login");
  };

  return (
    <div className="space-y-10 animate-fade-in">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">Settings</h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Account profile, subscription, usage, and activity history.
        </p>
      </div>

      {loading || !account ? (
        <LoadingSkeleton />
      ) : (
        <>
          <ProfileCard account={account} onSignOut={handleSignOut} />
          <SubscriptionCard account={account} />
          <UsageCard account={account} />
          <AuditLogCard
            log={auditLog}
            currentPage={auditPage}
            onPageChange={setAuditPage}
          />
        </>
      )}
    </div>
  );
}

// -------------------------------------------------------------------------
// Profile
// -------------------------------------------------------------------------

function ProfileCard({ account, onSignOut }: { account: Account; onSignOut: () => void }) {
  return (
    <Card
      icon={<UserIcon className="h-4 w-4" strokeWidth={1.75} />}
      title="Profile"
      description="Your account details"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name" value={account.fullName} icon={<UserIcon className="h-3.5 w-3.5" />} />
        <Field label="Email"     value={account.email}    icon={<Mail     className="h-3.5 w-3.5" />} />
        <Field label="Role"      value={account.role}     icon={<Shield   className="h-3.5 w-3.5" />} />
        <Field label="Member since" value={fmtDate(account.createdAt)} icon={<Activity className="h-3.5 w-3.5" />} />
      </div>
      <div className="mt-6 flex items-center justify-end">
        <Button variant="outline" size="sm" onClick={onSignOut}>
          <LogOut className="h-4 w-4" />
          Sign out
        </Button>
      </div>
    </Card>
  );
}

// -------------------------------------------------------------------------
// Subscription
// -------------------------------------------------------------------------

function SubscriptionCard({ account }: { account: Account }) {
  const tier = account.subscription.tier;
  const meta = TIER_META[tier];
  const Icon = meta.icon;

  return (
    <Card
      icon={<CreditCard className="h-4 w-4" strokeWidth={1.75} />}
      title="Subscription"
      description="Your current plan"
    >
      <div className="flex items-start justify-between gap-6">
        <div className="flex items-start gap-4">
          <div className={cn("flex h-12 w-12 shrink-0 items-center justify-center rounded-lg", meta.bg)}>
            <Icon className={cn("h-5 w-5", meta.fg)} strokeWidth={1.75} />
          </div>
          <div>
            <p className="text-headline">{account.subscription.tierDisplayName} plan</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {account.subscription.monthlyPriceInr > 0
                ? `${formatINR(account.subscription.monthlyPriceInr)}/month`
                : "Free forever, with sensible limits"}
            </p>
            <p className="mt-3 text-xs text-muted-foreground">
              Started {fmtDate(account.subscription.startedAt)}
            </p>
          </div>
        </div>

        {tier === "FREE" && (
          <div className="hidden sm:block">
            <Button size="sm" disabled title="Payment processing not yet enabled">
              Upgrade
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}

// -------------------------------------------------------------------------
// Usage
// -------------------------------------------------------------------------

function UsageCard({ account }: { account: Account }) {
  const u = account.usage;
  const rows = [
    { label: "Budgets",            used: u.budgets,           limit: u.budgetLimit         },
    { label: "Financial goals",    used: u.goals,             limit: u.goalLimit           },
    { label: "Receipts this month",used: u.receiptsThisMonth, limit: u.receiptUploadLimit  },
  ];

  return (
    <Card
      icon={<Gauge className="h-4 w-4" strokeWidth={1.75} />}
      title="Usage"
      description="Your activity vs plan limits"
    >
      <div className="space-y-4">
        {rows.map((r) => (
          <UsageRow key={r.label} {...r} />
        ))}
      </div>
    </Card>
  );
}

function UsageRow({ label, used, limit }: { label: string; used: number; limit: number }) {
  const unlimited = limit === 2147483647 || limit >= Number.MAX_SAFE_INTEGER;
  const pct = unlimited ? 0 : Math.min(100, (used / Math.max(1, limit)) * 100);
  const near  = !unlimited && pct >= 80;
  const over  = !unlimited && pct >= 100;

  return (
    <div>
      <div className="flex items-baseline justify-between mb-1.5">
        <p className="text-sm text-foreground">{label}</p>
        <p className="text-sm tabular-nums text-muted-foreground">{fmtUsage(used, limit)}</p>
      </div>
      <div className="h-1.5 rounded-full bg-muted overflow-hidden">
        <div
          className={cn(
            "h-full transition-all duration-500",
            over ? "bg-red-500" : near ? "bg-amber-500" : "bg-emerald-500"
          )}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------
// Audit log
// -------------------------------------------------------------------------

function AuditLogCard({
  log, currentPage, onPageChange,
}: {
  log: AuditLogPage | null;
  currentPage: number;
  onPageChange: (p: number) => void;
}) {
  if (!log) return null;
  return (
    <Card
      icon={<Activity className="h-4 w-4" strokeWidth={1.75} />}
      title="Activity log"
      description={`${log.totalElements} action${log.totalElements === 1 ? "" : "s"} recorded on your account`}
    >
      {log.content.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4 text-center">No activity recorded yet.</p>
      ) : (
        <div className="-mx-6">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b" style={{ borderColor: "hsl(var(--border))" }}>
                <th className="px-6 py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Action</th>
                <th className="px-6 py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Resource</th>
                <th className="px-6 py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">IP</th>
                <th className="px-6 py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">When</th>
              </tr>
            </thead>
            <tbody>
              {log.content.map((e) => (
                <AuditRow key={e.id} entry={e} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {log.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between border-t pt-3" style={{ borderColor: "hsl(var(--border))" }}>
          <p className="text-xs text-muted-foreground">
            Page {currentPage + 1} of {log.totalPages}
          </p>
          <div className="flex items-center gap-1">
            <Button variant="outline" size="icon-sm" disabled={log.first}
              onClick={() => onPageChange(Math.max(0, currentPage - 1))}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="icon-sm" disabled={log.last}
              onClick={() => onPageChange(currentPage + 1)}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function AuditRow({ entry }: { entry: AuditLogEntry }) {
  const label = ACTION_LABELS[entry.action] ?? entry.action.replace(/_/g, " ").toLowerCase();
  return (
    <tr className="border-b last:border-0 hover:bg-muted/30 transition-colors"
        style={{ borderColor: "hsl(var(--border))" }}>
      <td className="px-6 py-3">
        <div className="flex items-center gap-2">
          {entry.action === "USER_LOGIN_FAILED"
            ? <span className="h-1.5 w-1.5 rounded-full bg-red-500" />
            : <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />}
          <span className="text-sm text-foreground">{label}</span>
        </div>
      </td>
      <td className="px-6 py-3 text-xs text-muted-foreground">
        {entry.resourceType ?? "—"}
      </td>
      <td className="px-6 py-3 text-xs text-muted-foreground font-mono">
        {entry.ipAddress ?? "—"}
      </td>
      <td className="px-6 py-3 text-xs text-muted-foreground whitespace-nowrap">
        {fmtTimestamp(entry.createdAt)}
      </td>
    </tr>
  );
}

// -------------------------------------------------------------------------
// Shared
// -------------------------------------------------------------------------

function Card({
  icon, title, description, children,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <section
      className="rounded-xl border bg-card p-6"
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="mb-5 flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
          {icon}
        </div>
        <div>
          <p className="text-sm font-semibold text-foreground">{title}</p>
          <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
        </div>
      </div>
      {children}
    </section>
  );
}

function Field({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <div>
      <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-1">
        <span className="text-muted-foreground/70">{icon}</span>
        {label}
      </p>
      <p className="text-sm text-foreground font-medium">{value}</p>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-6">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="h-32 rounded-xl border bg-card animate-pulse"
             style={{ borderColor: "hsl(var(--border))" }} />
      ))}
    </div>
  );
}
