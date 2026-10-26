"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { AlertTriangle, ArrowDown, ArrowLeft, ArrowUp, Loader2, Printer } from "lucide-react";
import { Button } from "@/components/ui/button";
import { reportsApi } from "@/features/reports/api";
import type { MonthlyReport } from "@/features/reports/types";
import { CATEGORY_META } from "@/features/transactions/types";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric", month: "long", year: "numeric",
  });
}

export default function MonthlyReportPage() {
  const params  = useSearchParams();
  const from    = params.get("from") ?? "";
  const to      = params.get("to")   ?? "";

  const [report,  setReport]  = useState<MonthlyReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  useEffect(() => {
    if (!from || !to) {
      setError("Missing report date range");
      setLoading(false);
      return;
    }
    reportsApi.getMonthlyReport(from, to)
      .then(setReport)
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load report"))
      .finally(() => setLoading(false));
  }, [from, to]);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center text-ink-meta" role="status" aria-label="Loading report">
        <Loader2 className="h-6 w-6 animate-spin" aria-hidden="true" />
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="rounded-md bg-warning-soft p-sp-max text-center">
        <p role="alert" className="flex items-center justify-center gap-sp-sm text-sm font-medium text-warning">
          <AlertTriangle className="h-4 w-4" aria-hidden="true" /> {error ?? "Report unavailable"}
        </p>
        <Link href="/dashboard/reports" className="mt-sp-wide inline-flex min-h-11 items-center text-sm text-warning underline">
          Back to reports
        </Link>
      </div>
    );
  }

  const positiveNet = report.netCashflow >= 0;

  return (
    <div className="report-page mx-auto max-w-3xl">
      <div className="report-toolbar mb-sp-max flex items-center justify-between print:hidden">
        <Link
          href="/dashboard/reports"
          className="inline-flex min-h-11 items-center gap-sp-sm text-sm text-ink-meta hover:text-ink"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Back to reports
        </Link>
        <Button size="sm" className="coarse:h-11" onClick={() => window.print()}>
          <Printer className="h-4 w-4" aria-hidden="true" />
          Print / Save as PDF
        </Button>
      </div>

      <article className="report-content space-y-sp-max">
        <header className="border-b border-border pb-sp-max">
          <div className="flex items-center gap-sp-sm">
            <Logo />
            <span className="text-sm font-medium text-ink">FlowSight</span>
          </div>
          <h1 className="mt-sp-max text-ty-section-title text-ink">Monthly summary</h1>
          <p className="mt-sp-sm text-sm text-ink-meta">
            {report.periodLabel} · {fmtDate(report.periodStart)} – {fmtDate(report.periodEnd)}
          </p>
        </header>

        <section>
          <SectionLabel>Overview</SectionLabel>
          <div className="mt-sp-wide grid grid-cols-2 gap-sp-wide rounded-md bg-chrome p-sp-giant lg:grid-cols-4">
            <Stat label="Total spend"   value={formatINR(report.totalSpend)} />
            <Stat label="Total income"  value={formatINR(report.totalIncome)} />
            <Stat
              label="Net cashflow"
              value={`${positiveNet ? "+" : "−"}${formatINR(Math.abs(report.netCashflow))}`}
              icon={positiveNet ? ArrowUp : ArrowDown}
              tone={positiveNet ? "positive" : "negative"}
            />
            <Stat label="Transactions"  value={String(report.transactionCount)} />
          </div>
        </section>

        {report.alerts.length > 0 && (
          <section>
            <SectionLabel>Behavioral alerts</SectionLabel>
            <ul className="mt-sp-wide space-y-sp-sm">
              {report.alerts.map((a, i) => (
                <li key={i} className="flex items-start gap-sp-xl rounded-md bg-chrome p-sp-xl">
                  <span
                    aria-hidden="true"
                    className={cn(
                      "mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full",
                      a.severity === "HIGH"   && "bg-warning",
                      a.severity === "MEDIUM" && "bg-caution",
                      a.severity === "LOW"    && "bg-primary"
                    )}
                  />
                  <div>
                    <p className="text-sm text-ink">
                      <span className="font-medium">{a.categoryDisplayName}:</span> {a.message}
                    </p>
                    <p className="mt-sp-xs text-xs text-ink-meta">
                      {formatINR(a.currentAmount)} this period · avg {formatINR(a.averageAmount)}/mo
                      <span className="sr-only"> · severity {a.severity.toLowerCase()}</span>
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {report.categoryBreakdown.length > 0 && (
          <section>
            <SectionLabel>Spend by category</SectionLabel>
            <div className="mt-sp-wide overflow-x-auto">
              <table>
                <thead>
                  <tr>
                    <th>Category</th>
                    <th className="text-right">Amount</th>
                    <th className="text-right">Share</th>
                    <th className="text-right">Count</th>
                  </tr>
                </thead>
                <tbody>
                  {report.categoryBreakdown.map((c) => {
                    const meta = CATEGORY_META[c.category as keyof typeof CATEGORY_META];
                    return (
                      <tr key={c.category}>
                        <td className="text-ink">{meta?.label ?? c.displayName}</td>
                        <td className="text-right font-medium tabular-nums text-ink">{formatINR(c.amount)}</td>
                        <td className="text-right tabular-nums text-ink-meta">{c.percentage.toFixed(0)}%</td>
                        <td className="text-right tabular-nums text-ink-meta">{c.transactionCount}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {report.topMerchants.length > 0 && (
          <section>
            <SectionLabel>Top merchants</SectionLabel>
            <div className="mt-sp-wide overflow-x-auto">
              <table>
                <thead>
                  <tr>
                    <th>Merchant</th>
                    <th className="text-right">Spend</th>
                    <th className="text-right">Visits</th>
                  </tr>
                </thead>
                <tbody>
                  {report.topMerchants.map((m) => (
                    <tr key={m.merchant}>
                      <td className="text-ink">{m.merchant}</td>
                      <td className="text-right font-medium tabular-nums text-ink">{formatINR(m.totalAmount)}</td>
                      <td className="text-right tabular-nums text-ink-meta">{m.transactionCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        <section className="report-page-break-before">
          <SectionLabel>Tax-eligible deductions (FY {report.taxSummary.financialYear}–{(report.taxSummary.financialYear + 1).toString().slice(2)})</SectionLabel>
          <div className="mt-sp-wide space-y-sp-giant">
            {report.taxSummary.sections.map((s) => (
              <div key={s.code}>
                <div className="flex items-baseline justify-between gap-sp-wide">
                  <h3 className="text-ty-link font-medium text-ink">
                    Section {s.code} — {s.name}
                  </h3>
                  <p className="text-ty-link font-medium tabular-nums text-ink">{formatINR(s.totalAmount)}</p>
                </div>
                <p className="mt-sp-xs text-xs text-ink-meta">{s.description}</p>
                {s.entries.length > 0 ? (
                  <div className="mt-sp-xl overflow-x-auto">
                    <table>
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Merchant</th>
                          <th className="text-right">Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {s.entries.map((e, i) => (
                          <tr key={i}>
                            <td className="text-ink-meta">{e.date}</td>
                            <td className="text-ink">{e.merchant}</td>
                            <td className="text-right tabular-nums text-ink">{formatINR(e.amount)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="mt-sp-sm text-xs italic text-ink-meta">
                    No transactions detected for this section in the current financial year.
                  </p>
                )}
              </div>
            ))}
          </div>
          <p className="mt-sp-giant text-xs text-ink-meta">
            Detection is keyword-based. This is a first-pass suggestion, so confirm with your tax advisor before filing.
          </p>
        </section>

        <footer className="border-t border-border pt-sp-giant text-xs text-ink-meta">
          Prepared by FlowSight · {new Date().toLocaleDateString("en-IN", {
            day: "numeric", month: "long", year: "numeric",
          })}
        </footer>
      </article>

      {/* Print stylesheet — clean PDF output */}
      <style jsx global>{`
        @media print {
          html, body {
            background: white !important;
          }
          /* Hide everything outside the report */
          aside, header.flex.h-header, nav, .report-toolbar {
            display: none !important;
          }
          main {
            overflow: visible !important;
            padding: 0 !important;
          }
          main > div {
            max-width: 100% !important;
            padding: 0 !important;
          }
          .report-page {
            max-width: 100% !important;
            margin: 0 !important;
          }
          .report-content {
            padding: 2cm !important;
          }
          /* Page break before tax section */
          .report-page-break-before {
            page-break-before: always;
          }
          /* Prevent table rows from splitting across pages */
          table { page-break-inside: auto; }
          tr    { page-break-inside: avoid; page-break-after: auto; }
          thead { display: table-header-group; }
        }
        @page {
          size: A4;
          margin: 0;
        }
      `}</style>
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <h2 className="text-ty-subhead text-ink">{children}</h2>;
}

function Stat({
  label, value, tone, icon: Icon,
}: {
  label: string;
  value: string;
  tone?: "positive" | "negative";
  icon?: React.ElementType;
}) {
  return (
    <div className="min-w-0">
      <p className="text-sm text-ink-meta">{label}</p>
      <p
        className={cn(
          "mt-sp-xs flex items-center gap-sp-xs text-ty-lead tabular-nums",
          tone === "positive" && "text-positive",
          tone === "negative" && "text-warning",
          !tone && "text-ink"
        )}
      >
        {Icon && <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />}
        {value}
      </p>
    </div>
  );
}

function Logo() {
  return (
    <svg width="18" height="18" viewBox="0 0 22 22" fill="none" aria-hidden="true">
      <rect x="0.5" y="0.5" width="21" height="21" rx="6" fill="hsl(var(--primary))" />
      <path
        d="M5.5 14.75L9 10.75L12 13.25L16.5 7.75"
        stroke="white" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"
      />
      <circle cx="16.5" cy="7.75" r="1.25" fill="white" />
    </svg>
  );
}
