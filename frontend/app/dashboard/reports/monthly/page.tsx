"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
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
      <div className="flex h-64 items-center justify-center text-muted-foreground">
        <Loader2 className="h-6 w-6 animate-spin" />
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center">
        <p className="text-sm font-medium text-red-900">{error ?? "Report unavailable"}</p>
        <Link
          href="/dashboard/reports"
          className="mt-4 inline-block text-sm text-red-700 hover:underline"
        >
          Back to reports
        </Link>
      </div>
    );
  }

  const positiveNet = report.netCashflow >= 0;

  return (
    <div className="report-page mx-auto max-w-3xl">
      {/* Toolbar — hidden on print */}
      <div className="report-toolbar mb-8 flex items-center justify-between print:hidden">
        <Link
          href="/dashboard/reports"
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to reports
        </Link>
        <Button size="sm" onClick={() => window.print()}>
          <Printer className="h-4 w-4" />
          Print / Save as PDF
        </Button>
      </div>

      <article className="report-content space-y-10">
        {/* Cover */}
        <header className="border-b pb-8" style={{ borderColor: "hsl(var(--border))" }}>
          <div className="flex items-center gap-2">
            <Logo />
            <span className="text-xs font-semibold tracking-tight text-foreground">FlowSight</span>
          </div>
          <h1 className="mt-8 text-3xl font-semibold tracking-tight text-foreground">
            Monthly summary
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {report.periodLabel} · {fmtDate(report.periodStart)} – {fmtDate(report.periodEnd)}
          </p>
        </header>

        {/* Totals */}
        <section>
          <SectionLabel>Overview</SectionLabel>
          <div
            className="mt-4 grid grid-cols-2 lg:grid-cols-4 divide-x divide-y lg:divide-y-0 overflow-hidden rounded-xl border bg-card"
            style={{ borderColor: "hsl(var(--border))" }}
          >
            <Stat label="Total spend"   value={formatINR(report.totalSpend)} />
            <Stat label="Total income"  value={formatINR(report.totalIncome)} />
            <Stat
              label="Net cashflow"
              value={`${positiveNet ? "" : "−"}${formatINR(Math.abs(report.netCashflow))}`}
              tone={positiveNet ? "positive" : "negative"}
            />
            <Stat label="Transactions"  value={String(report.transactionCount)} />
          </div>
        </section>

        {/* Behavioral alerts */}
        {report.alerts.length > 0 && (
          <section>
            <SectionLabel>Behavioral alerts</SectionLabel>
            <div className="mt-4 space-y-2">
              {report.alerts.map((a, i) => (
                <div
                  key={i}
                  className="flex items-start gap-3 rounded-lg border p-3.5"
                  style={{ borderColor: "hsl(var(--border))" }}
                >
                  <span
                    className={cn(
                      "mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full",
                      a.severity === "HIGH"   && "bg-red-500",
                      a.severity === "MEDIUM" && "bg-amber-500",
                      a.severity === "LOW"    && "bg-blue-500"
                    )}
                  />
                  <div>
                    <p className="text-sm text-foreground">
                      <span className="font-medium">{a.categoryDisplayName}:</span> {a.message}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {formatINR(a.currentAmount)} this period · avg {formatINR(a.averageAmount)}/mo
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Category breakdown */}
        {report.categoryBreakdown.length > 0 && (
          <section>
            <SectionLabel>Spend by category</SectionLabel>
            <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b" style={{ borderColor: "hsl(var(--border))" }}>
                  <th className="py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Category</th>
                  <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Amount</th>
                  <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Share</th>
                  <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Count</th>
                </tr>
              </thead>
              <tbody>
                {report.categoryBreakdown.map((c) => {
                  const meta = CATEGORY_META[c.category as keyof typeof CATEGORY_META];
                  return (
                    <tr key={c.category} className="border-b last:border-0" style={{ borderColor: "hsl(var(--border))" }}>
                      <td className="py-2.5 text-foreground">
                        {meta?.label ?? c.displayName}
                      </td>
                      <td className="py-2.5 text-right font-medium tabular-nums text-foreground">
                        {formatINR(c.amount)}
                      </td>
                      <td className="py-2.5 text-right tabular-nums text-muted-foreground">
                        {c.percentage.toFixed(0)}%
                      </td>
                      <td className="py-2.5 text-right tabular-nums text-muted-foreground">
                        {c.transactionCount}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            </div>
          </section>
        )}

        {/* Top merchants */}
        {report.topMerchants.length > 0 && (
          <section>
            <SectionLabel>Top merchants</SectionLabel>
            <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b" style={{ borderColor: "hsl(var(--border))" }}>
                  <th className="py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Merchant</th>
                  <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Spend</th>
                  <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Visits</th>
                </tr>
              </thead>
              <tbody>
                {report.topMerchants.map((m) => (
                  <tr key={m.merchant} className="border-b last:border-0" style={{ borderColor: "hsl(var(--border))" }}>
                    <td className="py-2.5 text-foreground">{m.merchant}</td>
                    <td className="py-2.5 text-right font-medium tabular-nums text-foreground">
                      {formatINR(m.totalAmount)}
                    </td>
                    <td className="py-2.5 text-right tabular-nums text-muted-foreground">
                      {m.transactionCount}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          </section>
        )}

        {/* Tax-eligible deductions */}
        <section className="report-page-break-before">
          <SectionLabel>Tax-eligible deductions (FY {report.taxSummary.financialYear}–{(report.taxSummary.financialYear + 1).toString().slice(2)})</SectionLabel>
          <div className="mt-4 space-y-5">
            {report.taxSummary.sections.map((s) => (
              <div key={s.code}>
                <div className="flex items-baseline justify-between">
                  <p className="text-sm font-semibold text-foreground">
                    Section {s.code} — {s.name}
                  </p>
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {formatINR(s.totalAmount)}
                  </p>
                </div>
                <p className="mt-0.5 text-xs text-muted-foreground">{s.description}</p>
                {s.entries.length > 0 ? (
                  <div className="mt-3 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b" style={{ borderColor: "hsl(var(--border))" }}>
                        <th className="py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Date</th>
                        <th className="py-2 text-left text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Merchant</th>
                        <th className="py-2 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {s.entries.map((e, i) => (
                        <tr key={i} className="border-b last:border-0" style={{ borderColor: "hsl(var(--border))" }}>
                          <td className="py-2 text-muted-foreground">{e.date}</td>
                          <td className="py-2 text-foreground">{e.merchant}</td>
                          <td className="py-2 text-right tabular-nums text-foreground">
                            {formatINR(e.amount)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  </div>
                ) : (
                  <p className="mt-2 text-xs text-muted-foreground italic">
                    No transactions detected for this section in the current financial year.
                  </p>
                )}
              </div>
            ))}
          </div>
          <p className="mt-6 text-xs text-muted-foreground">
            Detection is keyword-based. This is a first-pass suggestion — confirm with your tax advisor before filing.
          </p>
        </section>

        {/* Footer */}
        <footer
          className="border-t pt-6 text-xs text-muted-foreground"
          style={{ borderColor: "hsl(var(--border))" }}
        >
          Generated by FlowSight · {new Date().toLocaleDateString("en-IN", {
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
  return (
    <h2 className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
      {children}
    </h2>
  );
}

function Stat({
  label, value, tone,
}: {
  label: string;
  value: string;
  tone?: "positive" | "negative";
}) {
  return (
    <div className="px-5 py-4">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <p
        className={cn(
          "mt-1.5 text-2xl font-semibold tabular-nums tracking-tight",
          tone === "positive" && "text-emerald-700",
          tone === "negative" && "text-red-700",
          !tone && "text-foreground"
        )}
      >
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
