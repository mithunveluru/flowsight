"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, Loader2, Sparkles } from "lucide-react";
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
import { transactionApi } from "@/features/transactions/api";
import {
  CATEGORY_META,
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

const schema = z.object({
  transactionDate: z.string().min(1, "Date is required"),
  description: z.string().min(1, "Description is required").max(1000),
  merchant: z.string().max(255).optional(),
  amount: z.coerce
    .number({ invalid_type_error: "Enter a valid amount" })
    .positive("Amount must be greater than zero"),
  type: z.enum(["DEBIT", "CREDIT"]),
  currency: z.string().default("INR"),
  category: z.string().optional(),
  notes: z.string().max(2000).optional(),
});

type FormValues = z.infer<typeof schema>;

export default function NewTransactionPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: "DEBIT",
      currency: "INR",
      transactionDate: new Date().toISOString().split("T")[0],
    },
  });

  const description = watch("description", "");

  const onSubmit = async (data: FormValues) => {
    setServerError(null);
    try {
      await transactionApi.create({
        amount: data.amount,
        currency: data.currency,
        transactionDate: data.transactionDate,
        description: data.description,
        merchant: data.merchant || undefined,
        type: data.type,
        category: (data.category as TransactionCategory) || undefined,
        notes: data.notes || undefined,
      });
      router.push("/dashboard/transactions");
    } catch (err) {
      setServerError(
        err instanceof ApiError ? err.message : "Failed to save transaction"
      );
    }
  };

  return (
    <div className="max-w-xl animate-fade-in">
      {/* Breadcrumb */}
      <Link
        href="/dashboard/transactions"
        className="mb-6 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to transactions
      </Link>

      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">Add transaction</h1>
        <p className="mt-1 text-sm text-slate-500">
          FlowSight will auto-categorize based on the description you enter.
        </p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-6">
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          {serverError && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
              {serverError}
            </div>
          )}

          {/* Row: Date + Type */}
          <div className="grid grid-cols-2 gap-4">
            <Field label="Date" error={errors.transactionDate?.message}>
              <Input type="date" {...register("transactionDate")} />
            </Field>
            <Field label="Type" error={errors.type?.message}>
              <Controller
                name="type"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} defaultValue={field.value}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="DEBIT">Debit (expense)</SelectItem>
                      <SelectItem value="CREDIT">Credit (income)</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
          </div>

          {/* Row: Amount + Currency */}
          <div className="grid grid-cols-3 gap-4">
            <div className="col-span-2">
              <Field label="Amount" error={errors.amount?.message}>
                <Input
                  type="number"
                  step="0.01"
                  min="0.01"
                  placeholder="0.00"
                  {...register("amount")}
                />
              </Field>
            </div>
            <Field label="Currency" error={errors.currency?.message}>
              <Controller
                name="currency"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} defaultValue={field.value}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INR">INR</SelectItem>
                      <SelectItem value="USD">USD</SelectItem>
                      <SelectItem value="EUR">EUR</SelectItem>
                      <SelectItem value="GBP">GBP</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
          </div>

          {/* Description */}
          <Field label="Description" error={errors.description?.message}>
            <Input
              placeholder="e.g. Zomato order, Amazon purchase, Salary credit"
              {...register("description")}
            />
            {description.length > 2 && (
              <p className="mt-1.5 flex items-center gap-1 text-xs text-slate-400">
                <Sparkles className="h-3 w-3" />
                FlowSight will auto-categorize this transaction
              </p>
            )}
          </Field>

          {/* Merchant (optional) */}
          <Field
            label="Merchant"
            optional
            error={errors.merchant?.message}
            hint="Override the auto-extracted merchant name"
          >
            <Input placeholder="e.g. Zomato, Amazon, HDFC Bank" {...register("merchant")} />
          </Field>

          {/* Category (optional override) */}
          <Field
            label="Category"
            optional
            hint="Leave blank to use auto-categorization"
          >
            <Controller
              name="category"
              control={control}
              render={({ field }) => (
                <Select
                  onValueChange={(v) => field.onChange(v === "__auto__" ? undefined : v)}
                  value={field.value ?? "__auto__"}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Auto-detect" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__auto__">Auto-detect</SelectItem>
                    {CATEGORY_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        <div className="flex items-center gap-2">
                          <span
                            className={cn(
                              "h-2 w-2 rounded-full",
                              CATEGORY_META[opt.value].color.split(" ")[0]
                            )}
                          />
                          {opt.label}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </Field>

          {/* Notes */}
          <Field label="Notes" optional error={errors.notes?.message}>
            <textarea
              className={cn(
                "flex min-h-[72px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm",
                "placeholder:text-muted-foreground resize-none",
                "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              )}
              placeholder="Optional notes..."
              {...register("notes")}
            />
          </Field>

          <div className="flex items-center gap-3 pt-1">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              {isSubmitting ? "Saving..." : "Save transaction"}
            </Button>
            <Button type="button" variant="outline" asChild>
              <Link href="/dashboard/transactions">Cancel</Link>
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({
  label,
  error,
  hint,
  optional,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  optional?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-1.5">
        <Label>{label}</Label>
        {optional && (
          <span className="text-xs text-slate-400">(optional)</span>
        )}
      </div>
      {children}
      {hint && !error && (
        <p className="text-xs text-slate-400">{hint}</p>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
