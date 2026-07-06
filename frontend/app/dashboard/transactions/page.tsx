"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Plus,
  ReceiptText,
  Trash2,
  Upload,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { CategoryPill } from "@/components/ui/category-pill";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { transactionApi } from "@/features/transactions/api";
import {
  CATEGORY_OPTIONS,
  type BulkImportResult,
  type Transaction,
  type TransactionCategory,
  type TransactionPage,
} from "@/features/transactions/types";
import { cn } from "@/lib/utils";
import { ApiError } from "@/lib/api";

export default function TransactionsPage() {
  const [page, setPage] = useState<TransactionPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<TransactionCategory | "__all__">("__all__");
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<BulkImportResult | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transactionApi.list({
        page: currentPage,
        size: 25,
        category: categoryFilter === "__all__" ? undefined : categoryFilter,
      });
      setPage(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
  }, [currentPage, categoryFilter]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await transactionApi.delete(id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete the transaction");
    } finally {
      setDeletingId(null);
    }
  };

  const handleCsvUpload = async (file: File) => {
    setImporting(true);
    setImportResult(null);
    try {
      const result = await transactionApi.importCsv(file);
      setImportResult(result);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    } finally {
      setImporting(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleCsvUpload(file);
    e.target.value = "";
  };

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-foreground">Transactions</h1>
          <p className="mt-0.5 text-sm text-muted-foreground tabular-nums">
            {page?.totalElements != null
              ? `${page.totalElements.toLocaleString("en-IN")} total`
              : " "}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            className="hidden"
            onChange={handleFileChange}
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={importing}
          >
            {importing ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Upload className="h-4 w-4" />
            )}
            Import CSV
          </Button>
          <Button size="sm" asChild>
            <Link href="/dashboard/transactions/new">
              <Plus className="h-4 w-4" />
              Add transaction
            </Link>
          </Button>
        </div>
      </div>

      {/* Import result banner — shows imported date range + CTA to view analytics */}
      {importResult && (
        <ImportResultBanner result={importResult} onDismiss={() => setImportResult(null)} />
      )}

      {/* Error banner */}
      {error && (
        <div className="rounded-lg border border-warning/25 bg-warning-soft px-4 py-3 text-sm text-warning">
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-3">
        <Select
          value={categoryFilter}
          onValueChange={(v: string) => {
            setCategoryFilter(v as TransactionCategory | "__all__");
            setCurrentPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-44 text-xs">
            <SelectValue placeholder="All categories" />
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
      </div>

      {/* Table */}
      <div className="card-refined overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Date
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Description
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Category
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Source
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Amount
                </th>
                <th className="w-10 px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <SkeletonRows />
              ) : !page?.content.length ? (
                <EmptyState filtered={categoryFilter !== "__all__"} />
              ) : (
                page.content.map((tx) => (
                  <TransactionRow
                    key={tx.id}
                    tx={tx}
                    onDelete={handleDelete}
                    deleting={deletingId === tx.id}
                  />
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {page && page.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-4 py-3">
            <span className="text-xs text-muted-foreground tabular-nums">
              Page {page.number + 1} of {page.totalPages}
            </span>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="icon-sm"
                disabled={page.first}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                aria-label="Previous page"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="icon-sm"
                disabled={page.last}
                onClick={() => setCurrentPage((p) => p + 1)}
                aria-label="Next page"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 8 }).map((_, i) => (
        <tr key={i} className="border-b border-border/60 last:border-0">
          <td className="px-4 py-3.5"><div className="skeleton h-3.5 w-20 rounded" /></td>
          <td className="px-4 py-3.5">
            <div className="skeleton h-3.5 w-40 rounded" />
            <div className="skeleton mt-1.5 h-2.5 w-24 rounded" />
          </td>
          <td className="px-4 py-3.5"><div className="skeleton h-5 w-24 rounded-full" /></td>
          <td className="px-4 py-3.5"><div className="skeleton h-5 w-16 rounded-md" /></td>
          <td className="px-4 py-3.5"><div className="skeleton ml-auto h-3.5 w-24 rounded" /></td>
          <td className="px-4 py-3.5" />
        </tr>
      ))}
    </>
  );
}

function TransactionRow({
  tx,
  onDelete,
  deleting,
}: {
  tx: Transaction;
  onDelete: (id: string) => void;
  deleting: boolean;
}) {
  const isDebit = tx.type === "DEBIT";
  // two-step destructive action: first click arms, second confirms
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (!confirming) return;
    const t = setTimeout(() => setConfirming(false), 3000);
    return () => clearTimeout(t);
  }, [confirming]);

  return (
    <tr className="group border-b border-border/60 transition-colors last:border-0 hover:bg-muted/50">
      <td className="whitespace-nowrap px-4 py-3 text-sm tabular-nums text-muted-foreground">
        {new Date(tx.transactionDate).toLocaleDateString("en-IN", {
          day: "numeric",
          month: "short",
          year: "numeric",
        })}
      </td>
      <td className="px-4 py-3">
        <div className="line-clamp-1 text-sm font-medium text-foreground">
          {tx.merchant ?? tx.description}
        </div>
        {tx.merchant && tx.description !== tx.merchant && (
          <div className="mt-0.5 line-clamp-1 text-xs text-muted-foreground/80">
            {tx.description}
          </div>
        )}
      </td>
      <td className="px-4 py-3">
        <CategoryPill category={tx.category} />
      </td>
      <td className="px-4 py-3">
        <Badge variant="slate" className="text-xs capitalize">
          {tx.source.toLowerCase()}
        </Badge>
      </td>
      <td className="px-4 py-3 text-right">
        {/* Debits stay neutral — a page of ordinary spending shouldn't scream red.
            Income is the exception worth color. */}
        <span
          className={cn(
            "text-sm font-medium tabular-nums",
            isDebit ? "text-foreground" : "text-positive"
          )}
        >
          {isDebit ? "−" : "+"}
          {tx.currency} {Number(tx.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
        </span>
      </td>
      <td className="px-4 py-3">
        <button
          onClick={() => (confirming ? onDelete(tx.id) : setConfirming(true))}
          onBlur={() => setConfirming(false)}
          disabled={deleting}
          className={cn(
            "rounded-md p-1 transition-all disabled:opacity-50",
            confirming
              ? "bg-warning-soft text-warning"
              : "text-muted-foreground/0 hover:text-warning group-hover:text-muted-foreground/50 group-hover:hover:text-warning focus-visible:text-muted-foreground"
          )}
          aria-label={confirming ? "Confirm delete" : "Delete transaction"}
          title={confirming ? "Click again to delete" : "Delete"}
        >
          {deleting ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Trash2 className="h-4 w-4" />
          )}
        </button>
      </td>
    </tr>
  );
}

function EmptyState({ filtered }: { filtered: boolean }) {
  return (
    <tr>
      <td colSpan={6} className="px-4 py-20 text-center">
        <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-muted">
          <ReceiptText className="h-5 w-5 text-muted-foreground/60" strokeWidth={1.75} />
        </div>
        <p className="mt-4 text-sm font-medium text-foreground">
          {filtered ? "Nothing in this category" : "Nothing recorded yet"}
        </p>
        <p className="mx-auto mt-1 max-w-xs text-xs text-muted-foreground">
          {filtered
            ? "Try a different category, or clear the filter to see everything."
            : "Import a bank statement CSV or add your first transaction by hand."}
        </p>
        {!filtered && (
          <div className="mt-5 flex justify-center gap-2">
            <Button size="sm" asChild>
              <Link href="/dashboard/transactions/new">
                <Plus className="h-4 w-4" />
                Add transaction
              </Link>
            </Button>
          </div>
        )}
      </td>
    </tr>
  );
}

// CSV import success banner: shows the imported date range and links to that analytics view
function ImportResultBanner({
  result,
  onDismiss,
}: {
  result: BulkImportResult;
  onDismiss: () => void;
}) {
  const hasErrors = result.skipped > 0;
  const formatINR = (v: number | null) =>
    v == null ? "" : `₹${Number(v).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;
  const formatDate = (iso: string | null) =>
    iso ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "";

  // Build the date-range summary
  const dateRangeText =
    result.firstTransactionDate && result.lastTransactionDate
      ? result.firstTransactionDate === result.lastTransactionDate
        ? `on ${formatDate(result.firstTransactionDate)}`
        : `from ${formatDate(result.firstTransactionDate)} to ${formatDate(result.lastTransactionDate)}`
      : "";

  return (
    <div
      className={cn(
        "animate-slide-up rounded-lg border px-4 py-3 text-sm",
        hasErrors
          ? "border-caution/25 bg-caution-soft text-caution"
          : "border-positive/25 bg-positive-soft text-positive"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-1 items-start gap-2">
          {hasErrors ? (
            <XCircle className="mt-0.5 h-4 w-4 shrink-0" />
          ) : (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
          )}
          <div className="flex-1">
            <p>
              <span className="font-medium">
                {result.imported} transaction{result.imported === 1 ? "" : "s"} imported
              </span>
              {dateRangeText && <span className="ml-1">{dateRangeText}</span>}
              {result.totalAmountImported != null && result.totalAmountImported > 0 && (
                <span className="ml-1 opacity-80">
                  · total {formatINR(result.totalAmountImported)}
                </span>
              )}
              {result.skipped > 0 && (
                <span className="ml-1">({result.skipped} skipped)</span>
              )}
            </p>

            {result.errors.length > 0 && (
              <ul className="mt-1 list-inside list-disc text-xs opacity-80">
                {result.errors.slice(0, 3).map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
                {result.errors.length > 3 && (
                  <li>...and {result.errors.length - 3} more</li>
                )}
              </ul>
            )}

            {/* CTA: view analytics for the imported date range */}
            {result.firstTransactionDate && result.lastTransactionDate && result.imported > 0 && (
              <Link
                href={`/dashboard/analytics?from=${result.firstTransactionDate}&to=${result.lastTransactionDate}`}
                className="mt-2 inline-flex items-center gap-1 text-xs font-medium underline-offset-2 hover:underline"
              >
                View analytics for this period
                <ArrowUpRight className="h-3 w-3" />
              </Link>
            )}
          </div>
        </div>
        <button
          onClick={onDismiss}
          className="text-current opacity-60 transition-opacity hover:opacity-100"
          aria-label="Dismiss"
        >
          ×
        </button>
      </div>
    </div>
  );
}
