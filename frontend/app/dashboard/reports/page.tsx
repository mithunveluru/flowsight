"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Calendar,
  CheckCircle2,
  Download,
  FileText,
  Loader2,
  Receipt,
  Sparkles,
  Trash2,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { reportsApi } from "@/features/reports/api";
import type {
  ReportJob,
  ReportJobPage,
  ReportPreset,
  TaxSummary,
} from "@/features/reports/types";
import {
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import {
  AnimatePresence,
  AnimatedSwitch,
  FadeIn,
  motion,
  PulseDot,
} from "@/components/motion/primitives";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function isoToday(): string {
  return new Date().toISOString().split("T")[0];
}

function isoMonthStart(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split("T")[0];
}

function previousMonthRange(): { from: string; to: string } {
  const now = new Date();
  const firstOfThis = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastOfPrev  = new Date(firstOfThis.getTime() - 86400000);
  const firstOfPrev = new Date(lastOfPrev.getFullYear(), lastOfPrev.getMonth(), 1);
  const fmt = (d: Date) => d.toISOString().split("T")[0];
  return { from: fmt(firstOfPrev), to: fmt(lastOfPrev) };
}

export default function ReportsPage() {
  return (
    <div className="space-y-10 animate-fade-in">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">Reports</h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Generate financial reviews, export your data, and surface tax-eligible deductions.
        </p>
      </div>

      {/* Phase 12 — premium AI-narrated PDF intelligence reports */}
      <IntelligenceReportsCard />

      {/* CSV export */}
      <CsvExportCard />

      {/* Monthly report links */}
      <MonthlyReportsCard />

      {/* Tax summary preview */}
      <TaxSummaryCard />
    </div>
  );
}

function CsvExportCard() {
  const [from,      setFrom]      = useState(isoMonthStart());
  const [to,        setTo]        = useState(isoToday());
  const [category,  setCategory]  = useState<TransactionCategory | "__all__">("__all__");
  const [busy,      setBusy]      = useState(false);
  const [error,     setError]     = useState<string | null>(null);

  const handleDownload = async () => {
    setBusy(true);
    setError(null);
    try {
      await reportsApi.downloadCsv(
        from, to,
        category === "__all__" ? undefined : category
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Download failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Section
      icon={<Download className="h-4 w-4" strokeWidth={1.75} />}
      title="Export transactions"
      description="Download raw transaction data for spreadsheets, accountants, and tax filing."
    >
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="From">
            <input
              type="date"
              value={from}
              max={to}
              onChange={(e) => setFrom(e.target.value)}
              className="input-base"
            />
          </Field>
          <Field label="To">
            <input
              type="date"
              value={to}
              max={isoToday()}
              onChange={(e) => setTo(e.target.value)}
              className="input-base"
            />
          </Field>
          <Field label="Category">
            <Select value={category} onValueChange={(v) => setCategory(v as TransactionCategory | "__all__")}>
              <SelectTrigger className="h-10">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">All categories</SelectItem>
                {CATEGORY_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>

        {error && (
          <p className="flex items-center gap-1.5 text-sm text-red-600">
            <XCircle className="h-4 w-4 shrink-0" /> {error}
          </p>
        )}

        <div className="flex items-center justify-end">
          <Button onClick={handleDownload} disabled={busy}>
            {busy ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Preparing your file
              </>
            ) : (
              <>
                <Download className="h-4 w-4" />
                Download CSV
              </>
            )}
          </Button>
        </div>
      </div>
    </Section>
  );
}

function MonthlyReportsCard() {
  const thisMonth = { from: isoMonthStart(), to: isoToday() };
  const lastMonth = previousMonthRange();

  const fmtMonth = (iso: string) =>
    new Date(iso).toLocaleDateString("en-IN", { month: "long", year: "numeric" });

  return (
    <Section
      icon={<Calendar className="h-4 w-4" strokeWidth={1.75} />}
      title="Monthly summary"
      description="A printable view with totals, category breakdown, and tax-eligible deductions."
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <ReportLink
          label="This month"
          period={fmtMonth(thisMonth.from)}
          href={`/dashboard/reports/monthly?from=${thisMonth.from}&to=${thisMonth.to}`}
        />
        <ReportLink
          label="Last month"
          period={fmtMonth(lastMonth.from)}
          href={`/dashboard/reports/monthly?from=${lastMonth.from}&to=${lastMonth.to}`}
        />
      </div>
    </Section>
  );
}

function ReportLink({ label, period, href }: { label: string; period: string; href: string }) {
  return (
    <Link
      href={href}
      className={cn(
        "group flex items-center gap-3 rounded-xl border bg-card px-4 py-4 transition-colors",
        "hover:bg-muted/40"
      )}
      style={{ borderColor: "hsl(var(--border))" }}
    >
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
        <FileText className="h-4 w-4" strokeWidth={1.75} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-foreground">{label}</p>
        <p className="text-xs text-muted-foreground">{period}</p>
      </div>
      <ArrowRight
        className="h-3.5 w-3.5 shrink-0 text-muted-foreground/40 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-muted-foreground"
        strokeWidth={1.75}
      />
    </Link>
  );
}

function TaxSummaryCard() {
  const [summary, setSummary] = useState<TaxSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    reportsApi.getTaxSummary()
      .then(setSummary)
      .catch(() => setSummary(null))
      .finally(() => setLoading(false));
  }, []);

  const fyLabel = summary ? `FY ${summary.financialYear}–${(summary.financialYear + 1).toString().slice(2)}` : "";

  return (
    <Section
      icon={<Receipt className="h-4 w-4" strokeWidth={1.75} />}
      title="Tax-eligible deductions"
      description="Investments and premiums detected from your transactions, mapped to sections 80C, 80D, and 80E."
    >
      {loading ? (
        <div className="skeleton h-20 rounded-lg" />
      ) : !summary ? (
        <p className="text-sm text-muted-foreground">
          We could not load the tax summary right now.
        </p>
      ) : (
        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">
            {fyLabel} · April {summary.financialYear} – March {summary.financialYear + 1}
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            {summary.sections.map((s) => (
              <div
                key={s.code}
                className="rounded-xl border bg-card p-4"
                style={{ borderColor: "hsl(var(--border))" }}
              >
                <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Section {s.code}
                </p>
                <p className="mt-1.5 text-xl font-semibold tabular-nums text-foreground">
                  {formatINR(s.totalAmount)}
                </p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {s.entries.length} transaction{s.entries.length === 1 ? "" : "s"}
                  {s.limit && s.remainingLimit !== null && (
                    <> · {formatINR(s.remainingLimit)} of limit remaining</>
                  )}
                </p>
              </div>
            ))}
          </div>
          {summary.totalEligible > 0 && (
            <p className="text-sm text-foreground">
              Total eligible:{" "}
              <span className="font-semibold tabular-nums">{formatINR(summary.totalEligible)}</span>
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Detection is heuristic-based on merchant names. Confirm with your tax advisor before filing.
          </p>
        </div>
      )}
    </Section>
  );
}

function Section({
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs font-medium text-muted-foreground mb-1.5 block">{label}</label>
      {children}
    </div>
  );
}

const PRESET_OPTIONS: { value: ReportPreset; label: string }[] = [
  { value: "LAST_7_DAYS",  label: "Last 7 days"  },
  { value: "LAST_30_DAYS", label: "Last 30 days" },
  { value: "LAST_90_DAYS", label: "Last 90 days" },
  { value: "THIS_MONTH",   label: "This month"   },
  { value: "LAST_MONTH",   label: "Last month"   },
  { value: "THIS_YEAR",    label: "This year"    },
  { value: "CUSTOM",       label: "Custom range" },
];

function fmtBytes(n: number | null): string {
  if (n == null) return "";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

function fmtTimestamp(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function IntelligenceReportsCard() {
  const [preset,   setPreset]   = useState<ReportPreset>("LAST_30_DAYS");
  const [from,     setFrom]     = useState(isoMonthStart());
  const [to,       setTo]       = useState(isoToday());
  const [history,  setHistory]  = useState<ReportJobPage | null>(null);
  const [creating, setCreating] = useState(false);
  const [error,    setError]    = useState<string | null>(null);
  const [activeJob, setActiveJob] = useState<ReportJob | null>(null);

  // Initial load of history
  const refreshHistory = async () => {
    try {
      const page = await reportsApi.listReportJobs(0, 5);
      setHistory(page);
    } catch {
      // silent — history is non-critical
    }
  };
  useEffect(() => { refreshHistory(); }, []);

  // Poll the active job every 1.2s until READY or FAILED
  useEffect(() => {
    if (!activeJob || activeJob.status === "READY" || activeJob.status === "FAILED") return;
    const timer = setInterval(async () => {
      try {
        const updated = await reportsApi.getReportJob(activeJob.id);
        setActiveJob(updated);
        if (updated.status === "READY" || updated.status === "FAILED") {
          refreshHistory();
        }
      } catch {
        // ignore — will retry
      }
    }, 1200);
    return () => clearInterval(timer);
  }, [activeJob]);

  const handleGenerate = async () => {
    setCreating(true);
    setError(null);
    setActiveJob(null);
    try {
      const req = preset === "CUSTOM" ? { preset, from, to } : { preset };
      const job = await reportsApi.createIntelligenceReport(req);
      setActiveJob(job);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to start report generation");
    } finally {
      setCreating(false);
    }
  };

  const handleDownload = async (job: ReportJob) => {
    const filename = `flowsight-report-${job.periodStart}-to-${job.periodEnd}.pdf`;
    try {
      await reportsApi.downloadIntelligenceReport(job.id, filename);
      refreshHistory();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Download failed");
    }
  };

  const handleDelete = async (job: ReportJob) => {
    if (!confirm("Delete this report?")) return;
    try {
      await reportsApi.deleteReportJob(job.id);
      if (activeJob?.id === job.id) setActiveJob(null);
      refreshHistory();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Delete failed");
    }
  };

  return (
    <Section
      icon={<Sparkles className="h-4 w-4" strokeWidth={1.75} />}
      title="Financial review"
      description="A narrative PDF covering your spending behavior, recurring commitments, recoverable spend, and what to do next."
    >
      <div className="space-y-5">
        {/* Generator */}
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Time range">
            <Select value={preset} onValueChange={(v) => setPreset(v as ReportPreset)}>
              <SelectTrigger className="h-10"><SelectValue /></SelectTrigger>
              <SelectContent>
                {PRESET_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>

          {preset === "CUSTOM" ? (
            <>
              <Field label="From">
                <input type="date" value={from} max={to}
                  onChange={(e) => setFrom(e.target.value)} className="input-base" />
              </Field>
              <Field label="To">
                <input type="date" value={to} max={isoToday()}
                  onChange={(e) => setTo(e.target.value)} className="input-base" />
              </Field>
            </>
          ) : (
            <div className="sm:col-span-2 self-end">
              <p className="text-xs text-muted-foreground leading-relaxed">
                Each review covers behavioral patterns, recurring obligations, recoverable spend, category trends, long-term consequences, and prioritized next steps.
              </p>
            </div>
          )}
        </div>

        {error && (
          <p className="flex items-center gap-1.5 text-sm text-red-600">
            <XCircle className="h-4 w-4 shrink-0" /> {error}
          </p>
        )}

        <div className="flex items-center justify-end">
          <Button onClick={handleGenerate} disabled={creating || (activeJob != null && activeJob.status !== "READY" && activeJob.status !== "FAILED")}>
            {creating ? (
              <><Loader2 className="h-4 w-4 animate-spin" /> Starting</>
            ) : (
              <><Sparkles className="h-4 w-4" /> Generate review</>
            )}
          </Button>
        </div>

        {/* Active job progress card */}
        {activeJob && <ActiveJobCard job={activeJob} onDownload={handleDownload} />}

        {/* History */}
        {history && history.content.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-medium text-muted-foreground">Previously generated</p>
            <div
              className="rounded-xl border bg-card divide-y overflow-hidden"
              style={{ borderColor: "hsl(var(--border))" }}
            >
              {history.content.map((job) => (
                <HistoryRow
                  key={job.id}
                  job={job}
                  isActive={activeJob?.id === job.id}
                  onDownload={handleDownload}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </Section>
  );
}

// Multi-step labels shown while the system "is understanding" the data.
// Each step rotates every 1.5s during GENERATING — gives the user the sense
// of intentional analysis rather than a spinner that says "loading".
const ANALYSIS_STEPS = [
  "Reviewing your transactions",
  "Tracing spending patterns",
  "Mapping recurring commitments",
  "Surfacing recoverable spend",
  "Drafting the summary",
  "Composing the document",
] as const;

function ActiveJobCard({
  job, onDownload,
}: {
  job: ReportJob;
  onDownload: (j: ReportJob) => void;
}) {
  const isReady    = job.status === "READY";
  const isFailed   = job.status === "FAILED";
  const inProgress = job.status === "PENDING" || job.status === "GENERATING";

  // Rotate through the analysis steps during GENERATING to create the
  // perception of intelligent work happening behind the scenes.
  const [stepIdx, setStepIdx] = useState(0);
  useEffect(() => {
    if (!inProgress) return;
    const t = setInterval(() => setStepIdx((i) => (i + 1) % ANALYSIS_STEPS.length), 1500);
    return () => clearInterval(t);
  }, [inProgress]);

  const targetPct = isReady ? 100 : isFailed ? 100 : job.status === "PENDING" ? 25 : 70;
  const progressColor = isReady ? "bg-emerald-500" : isFailed ? "bg-red-500" : "bg-blue-500";

  return (
    <motion.div
      layout
      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
      className={cn(
        "rounded-xl border p-5",
        isReady ? "border-emerald-200 bg-emerald-50/30" : "bg-card"
      )}
      style={!isReady ? { borderColor: "hsl(var(--border))" } : undefined}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0 flex items-start gap-2">
          {inProgress && <PulseDot className="mt-1.5 shrink-0" />}
          <div>
            <p className="text-sm font-semibold text-foreground">{job.periodLabel}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {job.periodStart} → {job.periodEnd}
            </p>
          </div>
        </div>
        {isReady && (
          <Button size="sm" onClick={() => onDownload(job)}>
            <Download className="h-4 w-4" /> Download
          </Button>
        )}
      </div>

      <div className="mt-4 space-y-1.5">
        <div className="flex items-center justify-between text-xs h-4">
          <AnimatedSwitch
            viewKey={isReady ? "ready" : isFailed ? "failed" : `step-${stepIdx}`}
            className="flex items-center gap-1.5"
          >
            {isReady ? (
              <span className="flex items-center gap-1.5 text-emerald-700">
                <CheckCircle2 className="h-3 w-3" />
                Ready
              </span>
            ) : isFailed ? (
              <span className="flex items-center gap-1.5 text-red-700">
                <XCircle className="h-3 w-3" />
                Generation could not complete
              </span>
            ) : (
              <span className="flex items-center gap-1.5 text-muted-foreground">
                <Loader2 className="h-3 w-3 animate-spin" />
                {ANALYSIS_STEPS[stepIdx]}
              </span>
            )}
          </AnimatedSwitch>
          {job.pdfSizeBytes != null && <span className="text-muted-foreground">{fmtBytes(job.pdfSizeBytes)}</span>}
        </div>
        <div className="h-1 rounded-full bg-muted overflow-hidden">
          <motion.div
            className={cn("h-full", progressColor)}
            initial={{ width: 0 }}
            animate={{ width: `${targetPct}%` }}
            transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          />
        </div>
        {job.errorMessage && (
          <p className="mt-2 text-xs text-red-700">{job.errorMessage}</p>
        )}
      </div>
    </motion.div>
  );
}

function HistoryRow({
  job, isActive, onDownload, onDelete,
}: {
  job: ReportJob;
  isActive: boolean;
  onDownload: (j: ReportJob) => void;
  onDelete: (j: ReportJob) => void;
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3 hover:bg-muted/30 transition-colors">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
        <FileText className="h-4 w-4" strokeWidth={1.75} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground truncate">
          {job.periodLabel}
          {isActive && <span className="ml-2 text-[10px] uppercase tracking-wider text-blue-700">Active</span>}
        </p>
        <p className="text-xs text-muted-foreground">
          {fmtTimestamp(job.createdAt)}
          {job.status === "READY" && job.pdfSizeBytes != null && <> · {fmtBytes(job.pdfSizeBytes)}</>}
        </p>
      </div>
      <span className={cn(
        "text-[10px] uppercase tracking-wider font-medium",
        job.status === "READY"      ? "text-emerald-700"
        : job.status === "FAILED"   ? "text-red-700"
        : job.status === "GENERATING" ? "text-blue-700"
        : "text-muted-foreground"
      )}>
        {job.status === "GENERATING" ? "Generating" : job.status.toLowerCase()}
      </span>
      <div className="flex items-center gap-1">
        {job.status === "READY" && (
          <button
            onClick={() => onDownload(job)}
            className="rounded p-1.5 text-slate-400 hover:text-foreground hover:bg-muted transition-colors"
            title="Download"
          >
            <Download className="h-3.5 w-3.5" />
          </button>
        )}
        <button
          onClick={() => onDelete(job)}
          className="rounded p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 transition-colors"
          title="Delete"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
