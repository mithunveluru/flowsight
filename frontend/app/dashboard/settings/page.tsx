"use client";

import { TitleRow } from "@/components/layout/title-row";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  ChevronLeft,
  ChevronRight,
  Infinity as InfinityIcon,
  LogOut,
  Mail,
  Palette,
  Receipt,
  Shield,
  User as UserIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/ui/theme";
import { Reveal } from "@/components/motion/css-motion";
import { Surface } from "@/components/insight/page-kit";
import { ProgressBar } from "@/components/insight/progress";
import { accountApi } from "@/features/account/api";
import type {
  Account,
  AuditLogEntry,
  AuditLogPage,
} from "@/features/account/types";
import { useAuthStore } from "@/store/auth";
import { authApi } from "@/features/auth/api";
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
    // revoke the refresh token server-side; local logout proceeds regardless
    const refreshToken = useAuthStore.getState().refreshToken;
    if (refreshToken) void authApi.logout(refreshToken).catch(() => {});
    clearAuth();
    router.replace("/auth/login");
  };

  return (
    <div className="space-y-sp-max">
      <TitleRow
        rule={false}
        title="Settings"
        description="Your profile, appearance, receipt analysis usage, and recent account activity."
      />

      <AppearanceCard />

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
      icon={<UserIcon className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Profile"
      description="Your account details."
    >
      <div className="grid gap-sp-wide sm:grid-cols-2">
        <Field label="Full name" value={account.fullName} icon={<UserIcon className="h-3.5 w-3.5" aria-hidden="true" />} />
        <Field label="Email"     value={account.email}    icon={<Mail className="h-3.5 w-3.5" aria-hidden="true" />} />
        <Field label="Role"      value={account.role}     icon={<Shield className="h-3.5 w-3.5" aria-hidden="true" />} />
        <Field label="Member since" value={fmtDate(account.createdAt)} icon={<Activity className="h-3.5 w-3.5" aria-hidden="true" />} />
      </div>
      <div className="mt-sp-giant flex items-center justify-end">
        <Button variant="outline" size="sm" className="coarse:h-11" onClick={onSignOut}>
          <LogOut className="h-4 w-4" aria-hidden="true" />
          Sign out
        </Button>
      </div>
    </Card>
  );
}

function AppearanceCard() {
  return (
    <Card
      icon={<Palette className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Appearance"
      description="Light, dark, or follow your device setting."
    >
      <ThemeToggle />
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
      icon={<Receipt className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Receipt analysis"
      description="Every product feature is available to you. Only receipt analysis is metered."
    >
      {q.unlimited ? (
        <div className="flex items-center gap-sp-xl rounded-md bg-canvas px-sp-giant py-sp-wide">
          <InfinityIcon className="h-5 w-5 shrink-0 text-primary" strokeWidth={2} aria-hidden="true" />
          <div>
            <p className="text-ty-link font-medium text-ink">Unlimited</p>
            <p className="text-xs text-ink-meta">
              No cap on receipt analyses, granted by an administrator.
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-sp-xl">
          <div className="flex items-baseline justify-between">
            <p className="text-sm text-ink">
              <span className="font-medium tabular-nums">{q.used}</span>
              <span className="text-ink-meta"> of {q.limit} receipts</span>
            </p>
            <p className={cn(
              "text-xs tabular-nums",
              over ? "text-warning" : near ? "text-caution" : "text-ink-meta"
            )}>
              {q.remaining ?? 0} remaining
            </p>
          </div>
          <ProgressBar
            size="sm"
            value={pct}
            tone={over ? "warning" : "primary"}
            label="Receipt analyses used"
            valueText={`${q.used} of ${q.limit}`}
          />
          {over && (
            <p className="text-xs text-warning">
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
      icon={<Activity className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Recent activity"
      description={`${log.totalElements} action${log.totalElements === 1 ? "" : "s"} on your account`}
    >
      {log.content.length === 0 ? (
        <p className="py-sp-xl text-center text-sm text-ink-meta">No activity recorded yet.</p>
      ) : (
        <div className="overflow-x-auto rounded-md bg-canvas">
          <table>
            <thead>
              <tr>
                <th>Action</th>
                <th>Resource</th>
                <th>IP</th>
                <th>When</th>
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
        <div className="mt-sp-wide flex items-center justify-between">
          <p className="text-xs text-ink-meta">
            Page {currentPage + 1} of {log.totalPages}
          </p>
          <div className="flex items-center gap-sp-xs">
            <Button variant="outline" size="icon-sm" className="coarse:h-11 coarse:w-11" disabled={log.first}
              aria-label="Previous page"
              onClick={() => onPageChange(Math.max(0, currentPage - 1))}>
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button variant="outline" size="icon-sm" className="coarse:h-11 coarse:w-11" disabled={log.last}
              aria-label="Next page"
              onClick={() => onPageChange(currentPage + 1)}>
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function AuditRow({ entry }: { entry: AuditLogEntry }) {
  const label = ACTION_LABELS[entry.action] ?? entry.action.replace(/_/g, " ").toLowerCase();
  const failed = entry.action === "USER_LOGIN_FAILED";
  return (
    <tr>
      <td>
        <div className="flex items-center gap-sp-sm">
          <span aria-hidden="true" className={cn("h-1.5 w-1.5 rounded-full", failed ? "bg-warning" : "bg-positive")} />
          <span className="text-ink">{label}</span>
        </div>
      </td>
      <td className="text-ink-meta">{entry.resourceType ?? "—"}</td>
      <td className="font-mono text-ink-meta">{entry.ipAddress ?? "—"}</td>
      <td className="whitespace-nowrap text-ink-meta">{fmtTimestamp(entry.createdAt)}</td>
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
    <Reveal>
      <Surface as="section">
        <div className="mb-sp-giant flex items-start gap-sp-xl">
          <span className="mt-1 text-primary">{icon}</span>
          <div>
            <h2 className="text-ty-subhead text-ink">{title}</h2>
            <p className="mt-sp-xs text-sm text-ink-meta">{description}</p>
          </div>
        </div>
        {children}
      </Surface>
    </Reveal>
  );
}

function Field({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <div>
      <p className="mb-sp-xs flex items-center gap-sp-sm text-sm text-ink-meta">
        {icon}
        {label}
      </p>
      <p className="text-ty-link text-ink">{value}</p>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-sp-wide" aria-busy="true">
      {[1, 2, 3].map((i) => (
        <div key={i} className="skeleton h-32 rounded-md" />
      ))}
    </div>
  );
}
