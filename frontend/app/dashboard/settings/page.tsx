"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  ChevronLeft,
  ChevronRight,
  Infinity as InfinityIcon,
  LogOut,
  Mail,
  Receipt,
  Shield,
  User as UserIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { accountApi } from "@/features/account/api";
import type {
  Account,
  AuditLogEntry,
  AuditLogPage,
} from "@/features/account/types";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/utils";

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
  PASSWORD_RESET_REQUESTED: "Password reset requested",
  PASSWORD_RESET_COMPLETED: "Password reset completed",
};

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
          Your profile, receipt analysis usage, and recent account activity.
        </p>
      </div>

      {loading || !account ? (
        <LoadingSkeleton />
      ) : (
        <>
          <ProfileCard account={account} onSignOut={handleSignOut} />
          <ReceiptQuotaCard account={account} />
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

function ProfileCard({ account, onSignOut }: { account: Account; onSignOut: () => void }) {
  return (
    <Card
      icon={<UserIcon className="h-4 w-4" strokeWidth={1.75} />}
      title="Profile"
      description="Your account details."
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

function ReceiptQuotaCard({ account }: { account: Account }) {
  const q = account.receiptQuota;
  const pct = q.unlimited ? 0 : Math.min(100, (q.used / Math.max(1, q.limit)) * 100);
  const near = !q.unlimited && pct >= 80;
  const over = !q.unlimited && pct >= 100;

  return (
    <Card
      icon={<Receipt className="h-4 w-4" strokeWidth={1.75} />}
      title="Receipt analysis"
      description="Every product feature is available to you. Only receipt analysis is metered."
    >
      {q.unlimited ? (
        <div className="flex items-center gap-3 rounded-lg bg-muted/40 px-4 py-4">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet-50">
            <InfinityIcon className="h-4 w-4 text-violet-700" strokeWidth={2} />
          </div>
          <div>
            <p className="text-sm font-semibold text-foreground">Unlimited</p>
            <p className="text-xs text-muted-foreground">
              No cap on receipt analyses, granted by an administrator.
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex items-baseline justify-between">
            <p className="text-sm text-foreground">
              <span className="font-semibold tabular-nums">{q.used}</span>
              <span className="text-muted-foreground"> of {q.limit} receipts</span>
            </p>
            <p className={cn(
              "text-xs tabular-nums",
              over ? "text-red-700" : near ? "text-amber-700" : "text-muted-foreground"
            )}>
              {q.remaining ?? 0} remaining
            </p>
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
          {over && (
            <p className="text-xs text-red-700">
              You have reached your receipt analysis limit. Existing receipts and analytics remain fully available, only new uploads are paused.
            </p>
          )}
        </div>
      )}
    </Card>
  );
}

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
      title="Recent activity"
      description={`${log.totalElements} action${log.totalElements === 1 ? "" : "s"} on your account`}
    >
      {log.content.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4 text-center">No activity recorded yet.</p>
      ) : (
        <div className="-mx-6 overflow-x-auto">
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
      {[1, 2, 3].map((i) => (
        <div key={i} className="skeleton h-32 rounded-xl" />
      ))}
    </div>
  );
}
