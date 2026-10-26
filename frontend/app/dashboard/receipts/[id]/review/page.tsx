"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ArrowRight, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/states";
import { useReceipt } from "@/features/receipts/hooks";
import { receiptStage } from "@/features/receipts/review-logic";
import { SavedTransactionPanel } from "@/features/transactions/saved-transaction";
import { ApiError } from "@/lib/api";
import { ReceiptImagePanel } from "./receipt-image";
import { ReviewForm } from "./review-form";

const BackLink = () => (
  <Link
    href="/dashboard/receipts"
    className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
  >
    <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
    Back to receipt inbox
  </Link>
);

export default function ReceiptReviewPage() {
  const id = useParams().id as string;
  const { data: receipt, error, isPending, refetch } = useReceipt(id);

  if (isPending) return <LoadingState variant="card" rows={4} label="Loading the receipt" className="max-w-4xl" />;

  if (error || !receipt) {
    const missing = error instanceof ApiError && error.status === 404;
    return (
      <div className="max-w-2xl space-y-4">
        <BackLink />
        <ErrorState
          title={missing ? "Receipt not found" : "This receipt did not load"}
          description={missing ? "It may have been deleted." : undefined}
          error={missing ? undefined : error}
          onRetry={missing ? undefined : () => refetch()}
        />
      </div>
    );
  }

  const stage = receiptStage(receipt);

  if (stage === "processing") {
    return (
      <div className="max-w-2xl space-y-4">
        <BackLink />
        <LoadingState variant="card" rows={3} label="Reading the receipt. This can take up to a minute." />
        <p className="text-supporting">Reading the receipt. This page updates by itself when it is ready.</p>
      </div>
    );
  }

  if (stage === "confirmed") {
    return (
      <div className="max-w-xl animate-fade-in space-y-5">
        <BackLink />
        <h1 className="text-headline">Receipt confirmed</h1>
        <SavedTransactionPanel transaction={receipt.transaction!} title="Saved to your transactions">
          <Button asChild variant="outline">
            <Link href="/dashboard/transactions">
              View transactions
              <ArrowRight aria-hidden="true" />
            </Link>
          </Button>
        </SavedTransactionPanel>
      </div>
    );
  }

  if (stage === "failed") {
    return (
      <div className="animate-fade-in space-y-5">
        <BackLink />
        <h1 className="text-headline">Review receipt</h1>
        <div className="grid items-start gap-6 lg:grid-cols-2">
          <ReceiptImagePanel receipt={receipt} />
          <div className="space-y-4">
            <div role="alert" className="flex items-start gap-3 rounded-lg border border-warning/25 bg-warning-soft p-4">
              <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-warning" aria-hidden="true" />
              <div>
                <p className="text-sm font-medium text-warning">We could not read this receipt</p>
                <p className="mt-0.5 text-xs text-warning">
                  {receipt.errorMessage ?? "No details could be extracted from this image."}
                </p>
              </div>
            </div>
            <p className="text-supporting">
              You can still record it by typing the details from the image
              {receipt.imageAvailable ? " shown here" : ""}.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button asChild>
                <Link href="/dashboard/capture/manual">
                  Enter it manually
                  <ArrowRight aria-hidden="true" />
                </Link>
              </Button>
              <Button variant="outline" asChild>
                <Link href="/dashboard/capture/receipt">Try another photo</Link>
              </Button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return <ReviewForm receipt={receipt} />;
}
