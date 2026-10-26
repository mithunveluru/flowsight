"use client";

import { ImageOff, Maximize2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { ErrorState, Skeleton } from "@/components/ui/states";
import { useReceiptImage } from "@/features/receipts/hooks";
import { imageRetention } from "@/features/receipts/review-logic";
import type { Receipt } from "@/features/receipts/types";

const formatWhen = (d: Date) =>
  d.toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

/** The review copy of the receipt, fetched with the bearer token. Missing or expired images are a normal state. */
export function ReceiptImagePanel({ receipt }: { receipt: Receipt }) {
  const retention = imageRetention(receipt);
  const image = useReceiptImage(receipt.id, retention.state === "kept");

  let body: React.ReactNode;
  if (retention.state !== "kept" || image.status === "missing") {
    body = (
      <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
        <ImageOff className="mb-3 h-6 w-6 text-muted-foreground" aria-hidden="true" />
        <p className="text-sm font-medium text-foreground">The image is no longer available</p>
        <p className="mt-1 max-w-xs text-xs text-muted-foreground">
          {retention.state === "none" && image.status !== "missing"
            ? "No copy was kept for this receipt, so check the values against the paper receipt."
            : "We keep a copy for 7 days, or 24 hours after you confirm. Check the values against the paper receipt."}
        </p>
      </div>
    );
  } else if (image.status === "loading") {
    body = (
      <div role="status" aria-busy="true" className="p-4">
        <span className="sr-only">Loading the receipt image</span>
        <Skeleton className="mx-auto aspect-[3/4] w-full max-w-sm" />
      </div>
    );
  } else if (image.status === "error") {
    body = (
      <ErrorState
        compact
        title="The image did not load"
        description="You can still review the values. Try loading the image again."
        error={image.error}
        onRetry={image.retry}
      />
    );
  } else {
    body = (
      <Dialog>
        <div className="relative flex justify-center bg-chrome">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={image.url} alt="Photo of the receipt" className="max-h-[70vh] w-auto max-w-full object-contain" />
          <DialogTrigger asChild>
            <Button size="sm" variant="outline" className="absolute right-2 top-2 bg-card/90">
              <Maximize2 aria-hidden="true" />
              Full size
            </Button>
          </DialogTrigger>
        </div>
        <DialogContent className="max-w-[min(64rem,calc(100vw-2rem))] p-3 sm:p-4">
          <DialogTitle className="sr-only">Receipt image, full size</DialogTitle>
          <DialogDescription className="sr-only">Scroll to see all of the receipt.</DialogDescription>
          <div className="max-h-[calc(100dvh-6rem)] overflow-auto">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={image.url} alt="Photo of the receipt, full size" className="mx-auto max-w-none" />
          </div>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <figure className="overflow-hidden rounded-lg bg-chrome">
      {body}
      <figcaption className="flex flex-wrap items-center justify-between gap-2 border-t border-border px-3 py-2 text-xs text-muted-foreground">
        <span className="min-w-0 truncate">{receipt.fileName}</span>
        {retention.state === "kept" && retention.until && image.status !== "missing" && (
          <span className="shrink-0">Image kept until {formatWhen(retention.until)}</span>
        )}
      </figcaption>
    </figure>
  );
}
