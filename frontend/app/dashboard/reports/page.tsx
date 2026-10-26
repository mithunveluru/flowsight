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
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { HeroBand } from "@/components/ui/band";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FullBleed } from "@/components/layout/full-bleed";
import { Alert, ContentBand, SectionHead, Surface } from "@/components/insight/page-kit";
import { ProgressBar, type ProgressTone } from "@/components/insight/progress";
import { CountUp, Reveal } from "@/components/motion/primitives";
import { ConfirmButton } from "@/components/ui/confirm-button";
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
import { AnimatedSwitch, PulseDot } from "@/components/motion/primitives";
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
    <div className="space-y-sp-hero">
      <FullBleed flushTop>
        <HeroBand
          title="Reports"
          lead="Generate financial reviews, export your data, and surface tax-eligible deductions."
        />
      </FullBleed>

      <IntelligenceReportsCard />
      <CsvExportCard />
      <MonthlyReportsCard />
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
      tone="lavender"
      icon={<Download className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Export transactions"
      description="Download raw transaction data for spreadsheets, accountants, and tax filing."
    >
      <div className="space-y-sp-wide">
        <div className="grid gap-sp-wide sm:grid-cols-3">
          <Field label="From">
            <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </Field>
          <Field label="To">
            <Input type="date" value={to} max={isoToday()} onChange={(e) => setTo(e.target.value)} />
          </Field>
          <Field label="Category">
            <Select value={category} onValueChange={(v) => setCategory(v as TransactionCategory | "__all__")}>
              <SelectTrigger>
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

        {error && <Alert>{error}</Alert>}

        <div className="flex items-center justify-end">
          <Button className="coarse:h-11" onClick={handleDownload} disabled={busy}>
            {busy ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                Preparing your file
              </>
            ) : (
              <>
                <Download className="h-4 w-4" aria-hidden="true" />
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
      tone="blue"
      icon={<Calendar className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Monthly summary"
      description="A printable view with totals, category breakdown, and tax-eligible deductions."
    >
      <div className="grid gap-sp-wide sm:grid-cols-2">
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
      className="press group flex min-h-11 items-center gap-sp-xl rounded-md bg-canvas px-sp-giant py-sp-wide transition-colors duration-fast hover:bg-chrome"
    >
      <FileText className="h-5 w-5 shrink-0 text-primary" strokeWidth={1.75} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="text-ty-link font-medium text-ink">{label}</p>
        <p className="text-xs text-ink-meta">{period}</p>
      </div>
      <ArrowRight
        className="h-4 w-4 shrink-0 text-ink-meta transition-transform duration-fast group-hover:translate-x-0.5"
        strokeWidth={1.75}
        aria-hidden="true"
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
      tone="lavender"
      icon={<Receipt className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Tax-eligible deductions"
      description="Investments and premiums detected from your transactions, mapped to sections 80C, 80D, and 80E."
    >
      {loading ? (
        <div className="skeleton h-20 rounded-md" />
      ) : !summary ? (
        <p className="text-sm text-ink-meta">We could not load the tax summary right now.</p>
      ) : (
        <div className="space-y-sp-wide">
          <p className="text-xs text-ink-meta">
            {fyLabel} · April {summary.financialYear} – March {summary.financialYear + 1}
          </p>
          <div className="grid gap-sp-wide sm:grid-cols-3">
            {summary.sections.map((s) => (
              <Reveal key={s.code}>
                <div className="rounded-md bg-canvas p-sp-giant">
                  <p className="text-sm text-ink-meta">Section {s.code}</p>
                  <p className="mt-sp-xs text-ty-lead tabular-nums text-ink">
                    <CountUp value={s.totalAmount} format={formatINR} />
                  </p>
                  <p className="mt-sp-xs text-xs text-ink-meta">
                    {s.entries.length} transaction{s.entries.length === 1 ? "" : "s"}
                    {s.limit && s.remainingLimit !== null && (
                      <> · {formatINR(s.remainingLimit)} of limit remaining</>
                    )}
                  </p>
                </div>
              </Reveal>
            ))}
          </div>
          {summary.totalEligible > 0 && (
            <p className="text-sm text-ink">
              Total eligible:{" "}
              <span className="font-medium tabular-nums">{formatINR(summary.totalEligible)}</span>
            </p>
          )}
          <p className="text-xs text-ink-meta">
            Detection is heuristic-based on merchant names. Confirm with your tax advisor before filing.
          </p>
        </div>
      )}
    </Section>
  );
}

function Section({
  icon, title, description, tone, children,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
  tone: "blue" | "lavender";
  children: React.ReactNode;
}) {
  return (
    <ContentBand tone={tone} space="md">
      <div className="mb-sp-giant flex items-start gap-sp-xl">
        <span className="mt-1 text-primary">{icon}</span>
        <div>
          <h2 className="text-ty-subhead text-ink">{title}</h2>
          <p className="mt-sp-xs max-w-prose text-sm text-ink-meta">{description}</p>
        </div>
      </div>
      {children}
    </ContentBand>
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
      tone="blue"
      icon={<Sparkles className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />}
      title="Financial review"
      description="A readable PDF covering your spending, your subscriptions, the money worth recovering, and what to do next."
    >
      <div className="space-y-sp-giant">
        <div className="grid gap-sp-wide sm:grid-cols-3">
          <Field label="Time range">
            <Select value={preset} onValueChange={(v) => setPreset(v as ReportPreset)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
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
                <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
              </Field>
              <Field label="To">
                <Input type="date" value={to} max={isoToday()} onChange={(e) => setTo(e.target.value)} />
              </Field>
            </>
          ) : (
            <div className="sm:col-span-2 self-end">
              <p className="text-xs leading-relaxed text-ink-meta">
                Each review covers behavioral patterns, recurring obligations, recoverable spend, category trends, long-term consequences, and prioritized next steps.
              </p>
            </div>
          )}
        </div>

        {error && <Alert>{error}</Alert>}

        <div className="flex items-center justify-end">
          <Button className="coarse:h-11" onClick={handleGenerate} disabled={creating || (activeJob != null && activeJob.status !== "READY" && activeJob.status !== "FAILED")}>
            {creating ? (
              <><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Starting</>
            ) : (
              <><Sparkles className="h-4 w-4" aria-hidden="true" /> Generate review</>
            )}
          </Button>
        </div>

        {activeJob && <ActiveJobCard job={activeJob} onDownload={handleDownload} />}

        {history && history.content.length > 0 && (
          <div>
            <h3 className="mb-sp-sm text-ty-link font-medium text-ink">Previously prepared</h3>
            <div className="divide-y divide-border overflow-hidden rounded-md bg-canvas">
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

// rotating step labels shown during GENERATING
const ANALYSIS_STEPS = [
  "Reviewing your transactions",
  "Tracing spending patterns",
  "Rounding up your subscriptions",
  "Finding money worth recovering",
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
  const tone: ProgressTone = isFailed ? "warning" : "primary";

  return (
    <div className="rounded-md bg-canvas p-sp-giant">
      <div className="flex items-start justify-between gap-sp-wide">
        <div className="flex min-w-0 flex-1 items-start gap-sp-sm">
          {inProgress && <PulseDot className="mt-1.5 shrink-0" color="hsl(var(--primary))" />}
          <div>
            <p className="text-ty-link font-medium text-ink">{job.periodLabel}</p>
            <p className="mt-sp-xs text-xs text-ink-meta">
              {job.periodStart} → {job.periodEnd}
            </p>
          </div>
        </div>
        {isReady && (
          <Button size="sm" className="coarse:h-11" onClick={() => onDownload(job)}>
            <Download className="h-4 w-4" aria-hidden="true" /> Download
          </Button>
        )}
      </div>

      <div className="mt-sp-wide space-y-sp-sm">
        <div className="flex h-4 items-center justify-between text-xs" aria-live="polite">
          <AnimatedSwitch
            viewKey={isReady ? "ready" : isFailed ? "failed" : `step-${stepIdx}`}
            className="flex items-center gap-sp-sm"
          >
            {isReady ? (
              <span className="flex items-center gap-sp-sm text-positive">
                <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                Ready
              </span>
            ) : isFailed ? (
              <span className="flex items-center gap-sp-sm text-warning">
                <XCircle className="h-3 w-3" aria-hidden="true" />
                Preparation could not complete
              </span>
            ) : (
              <span className="flex items-center gap-sp-sm text-ink-meta">
                <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
                {ANALYSIS_STEPS[stepIdx]}
              </span>
            )}
          </AnimatedSwitch>
          {job.pdfSizeBytes != null && <span className="text-ink-meta">{fmtBytes(job.pdfSizeBytes)}</span>}
        </div>
        <ProgressBar
          size="sm"
          tone={tone}
          value={targetPct}
          label={`Report progress, ${job.periodLabel}`}
          valueText={isReady ? "Ready" : isFailed ? "Failed" : ANALYSIS_STEPS[stepIdx]}
        />
        {job.errorMessage && <p className="text-xs text-warning">{job.errorMessage}</p>}
      </div>
    </div>
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
    <div className="flex items-center gap-sp-xl px-sp-giant py-sp-xl">
      <FileText className="h-5 w-5 shrink-0 text-ink-meta" strokeWidth={1.75} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-ink">
          {job.periodLabel}
          {isActive && <span className="ml-sp-sm text-xs text-primary">Active</span>}
        </p>
        <p className="text-xs text-ink-meta">
          {fmtTimestamp(job.createdAt)}
          {job.status === "READY" && job.pdfSizeBytes != null && <> · {fmtBytes(job.pdfSizeBytes)}</>}
        </p>
      </div>
      <span className={cn(
        "text-xs font-medium",
        job.status === "READY"        ? "text-positive"
        : job.status === "FAILED"     ? "text-warning"
        : job.status === "GENERATING" ? "text-primary"
        : "text-ink-meta"
      )}>
        {job.status === "GENERATING" ? "Preparing" : job.status === "READY" ? "Ready" : job.status === "FAILED" ? "Failed" : "Queued"}
      </span>
      <div className="flex items-center gap-sp-xs">
        {job.status === "READY" && (
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => onDownload(job)}
            aria-label={`Download ${job.periodLabel} report`}
            className="coarse:h-11 coarse:w-11"
          >
            <Download className="h-4 w-4" aria-hidden="true" />
          </Button>
        )}
        <ConfirmButton onConfirm={() => onDelete(job)} label="Delete report" className="p-sp-sm coarse:min-h-11 coarse:min-w-11 coarse:justify-center" />
      </div>
    </div>
  );
}
