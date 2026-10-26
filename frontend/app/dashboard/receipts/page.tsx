"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { CheckCircle2, ChevronLeft, ChevronRight, Clock, FileImage, Loader2, Plus, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CategoryPill } from "@/components/ui/category-pill";
import { ConfirmButton } from "@/components/ui/confirm-button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { useAnnounce } from "@/components/ui/announcer";
import { RECENT_RECEIPTS_SIZE, useDeleteReceipt, useReceipts } from "@/features/receipts/hooks";
import { receiptStage, sortInbox, type ReceiptStage } from "@/features/receipts/review-logic";
import type { Receipt } from "@/features/receipts/types";
import { formatMoney } from "@/features/transactions/currency";
import { cn } from "@/lib/utils";

const STAGE: Record<ReceiptStage, { label: string; icon: typeof Clock; tone: string }> = {
  draft: { label: "Needs review", icon: Clock, tone: "border-caution/25 bg-caution-soft text-caution" },
  failed: { label: "Could not read", icon: XCircle, tone: "border-warning/25 bg-warning-soft text-warning" },
  processing: { label: "Reading", icon: Loader2, tone: "border-brand/25 bg-brand-soft text-brand" },
  confirmed: { label: "Confirmed", icon: CheckCircle2, tone: "border-positive/25 bg-positive-soft text-positive" },
};

export default function ReceiptInboxPage() {
  const [page, setPage] = useState(0);
  const announce = useAnnounce();
  // page 0 is the same request the sidebar badge makes, so it is already cached
  const { data, error, isPending, refetch } = useReceipts(page, RECENT_RECEIPTS_SIZE);
  const remove = useDeleteReceipt();
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const rows = useMemo(() => sortInbox(data?.content ?? []), [data]);
  const drafts = rows.filter((r) => receiptStage(r) === "draft").length;

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await remove.mutateAsync(id);
      announce("Receipt deleted.");
    } catch {
      announce("Could not delete that receipt.", "assertive");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="animate-fade-in space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-headline">Receipt inbox</h1>
          <p className="mt-0.5 text-supporting">
            {data ? `${data.totalElements} total${drafts ? ` · ${drafts} waiting for your review` : ""}` : " "}
          </p>
        </div>
        <Button size="sm" asChild>
          <Link href="/dashboard/capture/receipt">
            <Plus aria-hidden="true" />
            Scan receipt
          </Link>
        </Button>
      </div>

      {isPending ? (
        <LoadingState variant="table" rows={6} label="Loading receipts" />
      ) : error ? (
        <ErrorState title="Receipts did not load" error={error} onRetry={() => refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={<FileImage />}
          title="No receipts yet"
          description="Scan one to capture the merchant, amount and date in seconds."
          action={
            <Button asChild>
              <Link href="/dashboard/capture/receipt">Scan receipt</Link>
            </Button>
          }
        />
      ) : (
        <div className="card-refined overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <caption className="sr-only">Receipts, those waiting for review first</caption>
              <thead>
                <tr className="border-b border-border bg-muted/50 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="px-4 py-3">Receipt</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Details</th>
                  <th scope="col" className="px-4 py-3">Scanned</th>
                  <th scope="col" className="w-10 px-4 py-3"><span className="sr-only">Delete</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <ReceiptRow key={r.id} receipt={r} onDelete={handleDelete} deleting={deletingId === r.id} />
                ))}
              </tbody>
            </table>
          </div>

          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-border px-4 py-3">
              <span className="text-xs text-muted-foreground">
                Page {data.number + 1} of {data.totalPages}
              </span>
              <div className="flex items-center gap-1">
                <Button variant="outline" size="icon-sm" aria-label="Previous page" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                  <ChevronLeft aria-hidden="true" />
                </Button>
                <Button variant="outline" size="icon-sm" aria-label="Next page" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
                  <ChevronRight aria-hidden="true" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ReceiptRow({ receipt, onDelete, deleting }: { receipt: Receipt; onDelete: (id: string) => void; deleting: boolean }) {
  const stage = receiptStage(receipt);
  const { label, icon: Icon, tone } = STAGE[stage];
  const tx = receipt.transaction;
  const x = receipt.extraction;
  const review = `/dashboard/receipts/${receipt.id}/review`;
  const currency = tx?.currency ?? receipt.currency ?? x?.currency;

  return (
    <tr className="border-b border-border/60 transition-colors last:border-0 hover:bg-muted/50">
      <td className="px-4 py-3">
        <Link href={review} className="flex items-center gap-2.5 font-medium text-foreground hover:text-brand">
          <FileImage className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="max-w-[14rem] truncate">{receipt.fileName}</span>
        </Link>
        <p className="ml-[1.625rem] text-xs text-muted-foreground">{Math.max(1, Math.round(receipt.fileSize / 1024))} KB</p>
      </td>
      <td className="px-4 py-3">
        <span className={cn("inline-flex items-center gap-1 whitespace-nowrap rounded-md border px-2 py-0.5 text-xs font-medium", tone)}>
          <Icon className={cn("h-3 w-3", stage === "processing" && "animate-spin")} aria-hidden="true" />
          {label}
        </span>
      </td>
      <td className="px-4 py-3">
        {tx ? (
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium tabular-nums">{formatMoney(tx.amount, tx.currency)}</span>
              {tx.category && <CategoryPill category={tx.category} />}
            </div>
            {tx.merchant && <p className="mt-0.5 text-xs text-muted-foreground">{tx.merchant}</p>}
          </div>
        ) : stage === "draft" ? (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {(x?.merchant || x?.amount != null) && (
              <span className="text-xs text-muted-foreground">
                {[x?.merchant, x?.amount != null ? formatMoney(x.amount, currency) : null].filter(Boolean).join(" · ")}
              </span>
            )}
            <Link href={review} className="text-xs font-medium text-brand hover:underline">
              Review and confirm
            </Link>
          </div>
        ) : stage === "failed" ? (
          <Link href={review} className="text-xs font-medium text-brand hover:underline">
            Open and enter by hand
          </Link>
        ) : (
          <span className="text-xs text-muted-foreground">Reading the receipt</span>
        )}
      </td>
      <td className="whitespace-nowrap px-4 py-3 text-xs text-muted-foreground">
        {new Date(receipt.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
      </td>
      <td className="px-4 py-3">
        <ConfirmButton onConfirm={() => onDelete(receipt.id)} busy={deleting} label={`Delete ${receipt.fileName}`} />
      </td>
    </tr>
  );
}
