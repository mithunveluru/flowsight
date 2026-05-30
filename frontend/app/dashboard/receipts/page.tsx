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
import { CATEGORY_META } from "@/features/transactions/types";
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
          <h1 className="text-xl font-semibold text-slate-900">Receipts</h1>
          <p className="mt-0.5 text-sm text-slate-500">
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
      <div className="rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  File
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Status
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Extracted
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wide">
                  Date
                </th>
                <th className="w-10 px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-slate-400">
                    <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                  </td>
                </tr>
              ) : !page?.content.length ? (
                <tr>
                  <td colSpan={5} className="px-4 py-16 text-center">
                    <FileImage className="mx-auto h-8 w-8 text-slate-200 mb-3" />
                    <p className="text-sm font-medium text-slate-900">No receipts yet</p>
                    <p className="mt-1 text-xs text-slate-400">
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
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3">
            <span className="text-xs text-slate-500">
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
  const category = tx?.category ? CATEGORY_META[tx.category] : null;

  return (
    <tr className="border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors">
      {/* File */}
      <td className="px-4 py-3">
        <Link href={`/dashboard/receipts/${receipt.id}`}
          className="flex items-center gap-2.5 hover:text-blue-600">
          <FileImage className="h-4 w-4 shrink-0 text-slate-400" />
          <span className="text-sm font-medium text-slate-900 truncate max-w-[200px]">
            {receipt.fileName}
          </span>
        </Link>
        <p className="mt-0.5 text-xs text-slate-400 ml-6.5">
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
              <span className="text-sm font-medium text-slate-900">
                {tx.currency} {Number(tx.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
              </span>
              {category && (
                <span className={cn("inline-flex items-center rounded-md border px-1.5 py-0.5 text-xs font-medium", category.color)}>
                  {category.label}
                </span>
              )}
            </div>
            {tx.merchant && (
              <p className="text-xs text-slate-500 mt-0.5">{tx.merchant}</p>
            )}
          </div>
        ) : receipt.status === "COMPLETED" ? (
          <Link
            href={`/dashboard/receipts/${receipt.id}/review`}
            className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 hover:text-blue-700"
          >
            Review &amp; confirm
          </Link>
        ) : (
          <span className="text-xs text-slate-400">—</span>
        )}
      </td>

      {/* Date */}
      <td className="px-4 py-3 text-xs text-slate-500 whitespace-nowrap">
        {new Date(receipt.createdAt).toLocaleDateString("en-IN", {
          day: "numeric", month: "short", year: "numeric",
        })}
      </td>

      {/* Delete */}
      <td className="px-4 py-3">
        <button
          onClick={() => onDelete(receipt.id)}
          disabled={deleting}
          className="text-slate-300 hover:text-red-500 transition-colors disabled:opacity-50"
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
    PENDING:    { icon: Clock,         label: "Pending",    cls: "border-slate-200 bg-slate-50 text-slate-600" },
    PROCESSING: { icon: Loader2,       label: "Processing", cls: "border-blue-200 bg-blue-50 text-blue-700" },
    COMPLETED:  { icon: CheckCircle2,  label: "Completed",  cls: "border-emerald-200 bg-emerald-50 text-emerald-700" },
    FAILED:     { icon: XCircle,       label: "Failed",     cls: "border-red-200 bg-red-50 text-red-700" },
  } as const;

  const { icon: Icon, label, cls } = map[status];
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs font-medium", cls)}>
      <Icon className={cn("h-3 w-3", status === "PROCESSING" && "animate-spin")} />
      {label}
    </span>
  );
}
