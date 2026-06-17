"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  FileImage,
  Loader2,
  PenLine,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { receiptApi } from "@/features/receipts/api";
import type { Receipt } from "@/features/receipts/types";
import {
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

const schema = z.object({
  merchant: z.string().min(1, "Merchant name is required").max(255),
  amount: z.coerce
    .number({ invalid_type_error: "Enter a valid amount" })
    .positive("Amount must be greater than zero"),
  date: z.string().min(1, "Date is required"),
  category: z.string().optional(),
  notes: z.string().max(500).optional(),
  currency: z.string().default("INR"),
});

type FormValues = z.infer<typeof schema>;

export default function ReceiptReviewPage() {
  const params = useParams();
  const id = params.id as string;
  const router = useRouter();

  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [serverError, setServerError] = useState<string | null>(null);

  useEffect(() => {
    receiptApi.getById(id)
      .then(setReceipt)
      .catch(() => setReceipt(null))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
      </div>
    );
  }

  if (!receipt) {
    return (
      <div className="max-w-2xl">
        <p className="text-sm text-slate-500">Receipt not found.</p>
        <Link href="/dashboard/receipts" className="mt-4 inline-flex items-center gap-1.5 text-sm text-blue-600 hover:underline">
          <ArrowLeft className="h-3.5 w-3.5" /> Back to receipts
        </Link>
      </div>
    );
  }

  // OCR failed completely — show fallback
  if (receipt.status === "FAILED") {
    return <OcrFailureFallback receipt={receipt} />;
  }

  // Already confirmed — show success state
  if (receipt.transaction) {
    return <AlreadyConfirmed receipt={receipt} />;
  }

  return (
    <ReviewForm
      receipt={receipt}
      serverError={serverError}
      onServerError={setServerError}
      onSuccess={() => router.push("/dashboard/transactions")}
    />
  );
}

function ReviewForm({
  receipt,
  serverError,
  onServerError,
  onSuccess,
}: {
  receipt: Receipt;
  serverError: string | null;
  onServerError: (e: string | null) => void;
  onSuccess: () => void;
}) {
  const extraction = receipt.extraction;
  const confidence = receipt.confidence;
  const requiresConfirmation = receipt.requiresConfirmation;

  const defaultDate = extraction?.date
    ? extraction.date.split("T")[0]   // ISO date from backend may include time
    : new Date().toISOString().split("T")[0];

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      merchant: extraction?.merchant ?? "",
      amount:   extraction?.amount   ?? undefined,
      date:     defaultDate,
      currency: "INR",
    },
  });

  const onSubmit = async (data: FormValues) => {
    onServerError(null);
    try {
      await receiptApi.confirm(receipt.id, {
        merchant: data.merchant,
        amount:   data.amount,
        date:     data.date,
        category: (data.category as TransactionCategory) || undefined,
        notes:    data.notes || undefined,
        currency: data.currency,
      });
      onSuccess();
    } catch (err) {
      onServerError(
        err instanceof ApiError ? err.message : "Failed to save transaction"
      );
    }
  };

  return (
    <div className="max-w-xl animate-fade-in space-y-6">
      {/* Back link + heading */}
      <div>
        <Link
          href="/dashboard/receipts"
          className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to receipts
        </Link>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-xl font-semibold text-slate-900">Review receipt</h1>
            <p className="mt-1 text-sm text-slate-500">
              Confirm the details below before saving the transaction.
            </p>
          </div>
          <ConfidenceBadge confidence={confidence} />
        </div>
      </div>

      {/* Receipt file info */}
      <div className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
        <FileImage className="h-4 w-4 shrink-0 text-slate-400" />
        <span className="text-sm text-slate-700 truncate">{receipt.fileName}</span>
        {receipt.ocrProvider && (
          <span className="ml-auto shrink-0 rounded border border-slate-200 bg-white px-1.5 py-0.5 text-xs text-slate-500">
            {receipt.ocrProvider === "RECEIPT_OCR_SERVICE" ? "Vision model" : "Text scan"}
          </span>
        )}
      </div>

      {/* Low-confidence warning */}
      {requiresConfirmation && (
        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600 mt-0.5" />
          <p className="text-sm text-amber-800">
            We are less certain about the amount we extracted. It may have caught a sub-total or tax line instead of the final total. Please confirm before saving.
          </p>
        </div>
      )}

      {/* Partial OCR warning */}
      {!extraction?.successful && receipt.status === "COMPLETED" && (
        <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <PenLine className="h-5 w-5 shrink-0 text-slate-500 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-slate-700">A few details need your review</p>
            <p className="mt-0.5 text-xs text-slate-500">
              Add the missing values below, or{" "}
              <Link href="/dashboard/transactions/new" className="text-blue-600 hover:underline">
                enter the transaction manually
              </Link>
              .
            </p>
          </div>
        </div>
      )}

      {/* Editable form */}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {/* Merchant */}
        <div className="space-y-1.5">
          <Label htmlFor="merchant">
            Merchant name
            {!extraction?.merchant && <RequiredDot />}
          </Label>
          <Input
            id="merchant"
            {...register("merchant")}
            placeholder="e.g. Walmart, Starbucks"
            className={cn(errors.merchant && "border-red-300")}
          />
          {errors.merchant && (
            <p className="text-xs text-red-600">{errors.merchant.message}</p>
          )}
        </div>

        {/* Amount + currency */}
        <div className="space-y-1.5">
          <Label htmlFor="amount">
            Total amount
            {requiresConfirmation && (
              <span className="ml-1.5 inline-flex items-center gap-1 rounded border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-700">
                <AlertTriangle className="h-3 w-3" /> Verify
              </span>
            )}
          </Label>
          <div className="flex items-center gap-2">
            <span className="text-sm text-slate-500 w-8">INR</span>
            <Input
              id="amount"
              type="number"
              step="0.01"
              min="0.01"
              {...register("amount")}
              placeholder="0.00"
              className={cn("flex-1", errors.amount && "border-red-300")}
            />
          </div>
          {errors.amount && (
            <p className="text-xs text-red-600">{errors.amount.message}</p>
          )}
        </div>

        {/* Date */}
        <div className="space-y-1.5">
          <Label htmlFor="date">Transaction date</Label>
          <Input
            id="date"
            type="date"
            {...register("date")}
            max={new Date().toISOString().split("T")[0]}
            className={cn(errors.date && "border-red-300")}
          />
          {errors.date && (
            <p className="text-xs text-red-600">{errors.date.message}</p>
          )}
        </div>

        {/* Category */}
        <div className="space-y-1.5">
          <Label>Category</Label>
          <Controller
            name="category"
            control={control}
            render={({ field }) => (
              <Select
                value={field.value ?? "__auto__"}
                onValueChange={(v) => field.onChange(v === "__auto__" ? undefined : v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Auto-detect" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__auto__">Auto-detect</SelectItem>
                  {CATEGORY_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </div>

        {/* Notes */}
        <div className="space-y-1.5">
          <Label htmlFor="notes">Notes (optional)</Label>
          <Input
            id="notes"
            {...register("notes")}
            placeholder="Add a note about this transaction"
            maxLength={500}
          />
        </div>

        {/* Line items (read-only) */}
        {extraction?.lineItems && extraction.lineItems.length > 0 && (
          <details className="rounded-lg border border-slate-200 bg-white">
            <summary className="cursor-pointer px-4 py-3 text-xs font-medium text-slate-500 hover:text-slate-700 select-none">
              Line items ({extraction.lineItems.length})
            </summary>
            <div className="divide-y divide-slate-100 px-4 pb-3">
              {extraction.lineItems.map((item, i) => (
                <div key={i} className="flex items-center justify-between py-2">
                  <span className="text-sm text-slate-700">{item.itemName ?? "—"}</span>
                  <span className="text-sm text-slate-500 tabular-nums">
                    {item.itemPrice != null
                      ? `INR ${Number(item.itemPrice).toFixed(2)}`
                      : "—"}
                  </span>
                </div>
              ))}
            </div>
          </details>
        )}

        {/* Server error */}
        {serverError && (
          <p className="flex items-center gap-1.5 text-sm text-red-600">
            <XCircle className="h-4 w-4 shrink-0" />
            {serverError}
          </p>
        )}

        {/* Actions */}
        <div className="flex items-center gap-3 pt-1">
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Saving
              </>
            ) : (
              <>
                <CheckCircle2 className="h-4 w-4" />
                Confirm and save
              </>
            )}
          </Button>
          <Button type="button" variant="outline" asChild>
            <Link href="/dashboard/transactions/new">Enter manually instead</Link>
          </Button>
        </div>
      </form>
    </div>
  );
}

function OcrFailureFallback({ receipt }: { receipt: Receipt }) {
  return (
    <div className="max-w-xl space-y-5 animate-fade-in">
      <Link
        href="/dashboard/receipts"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to receipts
      </Link>

      <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4">
        <XCircle className="h-5 w-5 shrink-0 text-red-500 mt-0.5" />
        <div>
          <p className="text-sm font-medium text-red-900">Receipt analysis could not complete</p>
          <p className="mt-0.5 text-xs text-red-700">
            {receipt.errorMessage ?? "We could not extract details from this image."}
          </p>
        </div>
      </div>

      <p className="text-sm text-slate-600">
        You can still record this transaction by entering the details manually.
      </p>

      <div className="flex items-center gap-3">
        <Button asChild>
          <Link href="/dashboard/transactions/new">
            Enter transaction manually
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Button>
        <Button variant="outline" asChild>
          <Link href="/dashboard/receipts/upload">Try uploading again</Link>
        </Button>
      </div>
    </div>
  );
}

function AlreadyConfirmed({ receipt }: { receipt: Receipt }) {
  const tx = receipt.transaction!;
  return (
    <div className="max-w-xl space-y-5 animate-fade-in">
      <Link
        href="/dashboard/receipts"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to receipts
      </Link>

      <div className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4">
        <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-600 mt-0.5" />
        <div>
          <p className="text-sm font-medium text-emerald-900">This receipt is confirmed</p>
          <p className="mt-0.5 text-xs text-emerald-700">
            The transaction for <strong>{tx.merchant ?? "unknown merchant"}</strong> has been saved.
          </p>
        </div>
      </div>

      <Button asChild>
        <Link href="/dashboard/transactions">
          View transactions
          <ArrowRight className="h-4 w-4" />
        </Link>
      </Button>
    </div>
  );
}

function ConfidenceBadge({ confidence }: { confidence: number | null }) {
  if (confidence == null) return null;

  const tier =
    confidence >= 0.65 ? "HIGH"
    : confidence >= 0.40 ? "MEDIUM"
    : "LOW";

  const styles = {
    HIGH:   "border-emerald-200 bg-emerald-50 text-emerald-700",
    MEDIUM: "border-amber-200  bg-amber-50  text-amber-700",
    LOW:    "border-red-200    bg-red-50    text-red-700",
  };

  const label = {
    HIGH:   "Confident",
    MEDIUM: "Review recommended",
    LOW:    "Needs review",
  };

  return (
    <span className={cn(
      "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium",
      styles[tier]
    )}>
      {label[tier]}
    </span>
  );
}

function RequiredDot() {
  return <span className="ml-1 text-amber-500 text-xs">*</span>;
}
