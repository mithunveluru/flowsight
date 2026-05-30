"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Download,
  Loader2,
  Plus,
  Search,
  Trash2,
  Upload,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { transactionApi } from "@/features/transactions/api";
import {
  CATEGORY_META,
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
    } catch {
      // swallow — could show toast in Phase 10
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Transactions</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            {page?.totalElements != null
              ? `${page.totalElements.toLocaleString()} total`
              : ""}
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
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-3">
        <div className="relative w-64">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input className="pl-8 h-8 text-xs" placeholder="Search transactions..." disabled />
        </div>
        <Select
          value={categoryFilter}
          onValueChange={(v: string) => {
            setCategoryFilter(v as TransactionCategory | "__all__");
            setCurrentPage(0);
          }}
        >
          <SelectTrigger className="w-44 h-8 text-xs">
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
      <div className="rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Date
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Description
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Category
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Source
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Amount
                </th>
                <th className="w-10 px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-sm text-slate-400">
                    <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                  </td>
                </tr>
              ) : !page?.content.length ? (
                <EmptyState />
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
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3">
            <span className="text-xs text-slate-500">
              Page {page.number + 1} of {page.totalPages}
            </span>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="icon-sm"
                disabled={page.first}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="icon-sm"
                disabled={page.last}
                onClick={() => setCurrentPage((p) => p + 1)}
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

function TransactionRow({
  tx,
  onDelete,
  deleting,
}: {
  tx: Transaction;
  onDelete: (id: string) => void;
  deleting: boolean;
}) {
  const categoryMeta = tx.category ? CATEGORY_META[tx.category] : null;
  const isDebit = tx.type === "DEBIT";

  return (
    <tr className="border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors">
      <td className="px-4 py-3 text-sm text-slate-600 tabular-nums whitespace-nowrap">
        {new Date(tx.transactionDate).toLocaleDateString("en-IN", {
          day: "numeric",
          month: "short",
          year: "numeric",
        })}
      </td>
      <td className="px-4 py-3">
        <div className="text-sm font-medium text-slate-900 line-clamp-1">
          {tx.merchant ?? tx.description}
        </div>
        {tx.merchant && (
          <div className="text-xs text-slate-400 line-clamp-1 mt-0.5">
            {tx.description}
          </div>
        )}
      </td>
      <td className="px-4 py-3">
        {categoryMeta ? (
          <span
            className={cn(
              "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium",
              categoryMeta.color
            )}
          >
            {categoryMeta.label}
          </span>
        ) : (
          <span className="text-xs text-slate-400">—</span>
        )}
      </td>
      <td className="px-4 py-3">
        <Badge variant="slate" className="text-xs capitalize">
          {tx.source.toLowerCase()}
        </Badge>
      </td>
      <td className="px-4 py-3 text-right">
        <div
          className={cn(
            "flex items-center justify-end gap-1 text-sm font-medium tabular-nums",
            isDebit ? "text-red-600" : "text-emerald-600"
          )}
        >
          {isDebit ? (
            <ArrowDownLeft className="h-3.5 w-3.5" />
          ) : (
            <ArrowUpRight className="h-3.5 w-3.5" />
          )}
          {tx.currency} {Number(tx.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
        </div>
      </td>
      <td className="px-4 py-3">
        <button
          onClick={() => onDelete(tx.id)}
          disabled={deleting}
          className="text-slate-300 hover:text-red-500 transition-colors disabled:opacity-50"
          aria-label="Delete transaction"
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

function EmptyState() {
  return (
    <tr>
      <td colSpan={6} className="px-4 py-16 text-center">
        <Download className="mx-auto h-8 w-8 text-slate-200 mb-3" />
        <p className="text-sm font-medium text-slate-900">Nothing recorded yet</p>
        <p className="mt-1 text-xs text-slate-400">
          Import a CSV or add your first transaction by hand.
        </p>
        <div className="mt-4 flex justify-center gap-2">
          <Button size="sm" asChild>
            <Link href="/dashboard/transactions/new">
              <Plus className="h-4 w-4" />
              Add transaction
            </Link>
          </Button>
        </div>
      </td>
    </tr>
  );
}

/**
 * Success banner shown after a CSV import. Crucially, it surfaces the *date range*
 * the imported transactions cover and links straight to the analytics view for
 * that period — preventing the "imports landed in last month but I'm looking at
 * this month" confusion that the user reported.
 */
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
        "rounded-md border px-4 py-3 text-sm",
        hasErrors
          ? "border-amber-200 bg-amber-50 text-amber-800"
          : "border-emerald-200 bg-emerald-50 text-emerald-800"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2 flex-1">
          {hasErrors ? (
            <XCircle className="h-4 w-4 shrink-0 mt-0.5" />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
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
              <ul className="mt-1 list-disc list-inside text-xs opacity-80">
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
                View analytics for this period →
              </Link>
            )}
          </div>
        </div>
        <button
          onClick={onDismiss}
          className="text-current opacity-60 hover:opacity-100"
          aria-label="Dismiss"
        >
          ×
        </button>
      </div>
    </div>
  );
}
