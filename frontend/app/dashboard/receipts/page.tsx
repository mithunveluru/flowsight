"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  FileImage,
  Loader2,
  Plus,
  Trash2,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { receiptApi } from "@/features/receipts/api";
import type { Receipt, ReceiptPage } from "@/features/receipts/types";
import { CategoryPill } from "@/components/ui/category-pill";
import { cn } from "@/lib/utils";

export default function ReceiptsPage() {
  const [page, setPage] = useState<ReceiptPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPage(await receiptApi.list(currentPage));
    } finally {
      setLoading(false);
    }
  }, [currentPage]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await receiptApi.delete(id);
      await load();
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-foreground">Receipts</h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            {page?.totalElements != null ? `${page.totalElements} total` : ""}
          </p>
        </div>
        <Button size="sm" asChild>
          <Link href="/dashboard/receipts/upload">
            <Plus className="h-4 w-4" />
            Scan a receipt
          </Link>
        </Button>
      </div>

      {/* Table */}
      <div className="card-refined overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="px-4 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  File
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Status
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Extracted
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Date
                </th>
                <th className="w-10 px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {loading ? (
                Array.from({ length: 6 }).map((_, i) => (
                  <tr key={i} className="border-b border-border/60 last:border-0">
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2.5">
                        <div className="skeleton h-4 w-4 rounded" />
                        <div className="skeleton h-3.5 w-36 rounded" />
                      </div>
                    </td>
                    <td className="px-4 py-3.5"><div className="skeleton h-5 w-20 rounded-md" /></td>
                    <td className="px-4 py-3.5"><div className="skeleton h-3.5 w-28 rounded" /></td>
                    <td className="px-4 py-3.5"><div className="skeleton h-3.5 w-20 rounded" /></td>
                    <td className="px-4 py-3.5" />
                  </tr>
                ))
              ) : !page?.content.length ? (
                <tr>
                  <td colSpan={5} className="px-4 py-16 text-center">
                    <FileImage className="mx-auto h-8 w-8 text-muted-foreground/30 mb-3" />
                    <p className="text-sm font-medium text-foreground">No receipts yet</p>
                    <p className="mt-1 text-xs text-muted-foreground/70">
                      Scan one to capture the merchant, amount, and date in seconds.
                    </p>
                    <Button size="sm" className="mt-4" asChild>
                      <Link href="/dashboard/receipts/upload">Scan a receipt</Link>
                    </Button>
                  </td>
                </tr>
              ) : (
                page.content.map((r) => (
                  <ReceiptRow
                    key={r.id}
                    receipt={r}
                    onDelete={handleDelete}
                    deleting={deletingId === r.id}
                  />
                ))
              )}
            </tbody>
          </table>
        </div>

        {page && page.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-4 py-3">
            <span className="text-xs text-muted-foreground">
              Page {page.number + 1} of {page.totalPages}
            </span>
            <div className="flex items-center gap-1">
              <Button variant="outline" size="icon-sm" disabled={page.first}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}>
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button variant="outline" size="icon-sm" disabled={page.last}
                onClick={() => setCurrentPage((p) => p + 1)}>
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function ReceiptRow({
  receipt,
  onDelete,
  deleting,
}: {
  receipt: Receipt;
  onDelete: (id: string) => void;
  deleting: boolean;
}) {
  const tx = receipt.transaction;

  return (
    <tr className="border-b border-border/60 last:border-0 hover:bg-muted/50 transition-colors">
      {/* File */}
      <td className="px-4 py-3">
        <Link href={`/dashboard/receipts/${receipt.id}`}
          className="flex items-center gap-2.5 hover:text-brand">
          <FileImage className="h-4 w-4 shrink-0 text-muted-foreground/70" />
          <span className="text-sm font-medium text-foreground truncate max-w-[200px]">
            {receipt.fileName}
          </span>
        </Link>
        <p className="mt-0.5 text-xs text-muted-foreground/70 ml-6.5">
          {(receipt.fileSize / 1024).toFixed(0)} KB
        </p>
      </td>

      {/* Status */}
      <td className="px-4 py-3">
        <StatusBadge status={receipt.status} />
      </td>

      {/* Extracted data */}
      <td className="px-4 py-3">
        {tx ? (
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-foreground">
                {tx.currency} {Number(tx.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
              </span>
              {tx.category && <CategoryPill category={tx.category} />}
            </div>
            {tx.merchant && (
              <p className="text-xs text-muted-foreground mt-0.5">{tx.merchant}</p>
            )}
          </div>
        ) : receipt.status === "COMPLETED" ? (
          <Link
            href={`/dashboard/receipts/${receipt.id}/review`}
            className="inline-flex items-center gap-1 text-xs font-medium text-brand hover:text-brand"
          >
            Review &amp; confirm
          </Link>
        ) : (
          <span className="text-xs text-muted-foreground/70">—</span>
        )}
      </td>

      {/* Date */}
      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
        {new Date(receipt.createdAt).toLocaleDateString("en-IN", {
          day: "numeric", month: "short", year: "numeric",
        })}
      </td>

      {/* Delete */}
      <td className="px-4 py-3">
        <button
          onClick={() => onDelete(receipt.id)}
          disabled={deleting}
          className="text-muted-foreground/50 hover:text-warning transition-colors disabled:opacity-50"
          aria-label="Delete receipt"
        >
          {deleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
        </button>
      </td>
    </tr>
  );
}

function StatusBadge({ status }: { status: Receipt["status"] }) {
  const map = {
    PENDING:    { icon: Clock,         label: "Pending",    cls: "border-border bg-muted/50 text-muted-foreground" },
    PROCESSING: { icon: Loader2,       label: "Processing", cls: "border-brand/25 bg-brand-soft text-brand" },
    COMPLETED:  { icon: CheckCircle2,  label: "Completed",  cls: "border-positive/25 bg-positive-soft text-positive" },
    FAILED:     { icon: XCircle,       label: "Failed",     cls: "border-warning/25 bg-warning-soft text-warning" },
  } as const;

  const { icon: Icon, label, cls } = map[status];
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium", cls)}>
      <Icon className={cn("h-3 w-3", status === "PROCESSING" && "animate-spin")} />
      {label}
    </span>
  );
}
