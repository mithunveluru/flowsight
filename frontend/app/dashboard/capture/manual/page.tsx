"use client";

import { useState } from "react";
import Link from "next/link";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, Loader2, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAnnounce } from "@/components/ui/announcer";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useCreateTransaction } from "@/features/transactions/hooks";
import { CategoryDot } from "@/features/transactions/category-ui";
import { CurrencySelect } from "@/features/transactions/currency-select";
import { DEFAULT_CURRENCY, currencySymbol, parseAmount } from "@/features/transactions/currency";
import { SavedTransactionPanel } from "@/features/transactions/saved-transaction";
import { isoDate } from "@/features/query-core";
import {
  CATEGORY_OPTIONS,
  type Transaction,
  type TransactionCategory,
} from "@/features/transactions/types";
import { ApiError } from "@/lib/api";
import { TitleRow } from "@/components/layout/title-row";
import { markNewTransaction } from "@/features/transactions/new-row";
import { toast } from "@/components/ui/toast-store";

const schema = z.object({
  transactionDate: z.string().min(1, "Enter the date.").refine((d) => d <= isoDate(new Date()), "The date cannot be in the future."),
  description: z.string().trim().min(1, "Describe the transaction.").max(1000),
  merchant: z.string().max(255).optional(),
  amount: z.string().refine((v) => parseAmount(v) != null, "Enter an amount greater than zero, e.g. 249.50."),
  type: z.enum(["DEBIT", "CREDIT"]),
  currency: z.string().regex(/^[A-Z]{3}$/, "Choose the currency."),
  category: z.string().optional(),
  notes: z.string().max(2000).optional(),
});

type FormValues = z.infer<typeof schema>;

const AUTO = "__auto__";

export default function AddManuallyPage() {
  const announce = useAnnounce();
  const create = useCreateTransaction();
  const [serverError, setServerError] = useState<string | null>(null);
  const [saved, setSaved] = useState<Transaction | null>(null);

  const defaults: FormValues = {
    type: "DEBIT",
    currency: DEFAULT_CURRENCY,
    amount: "",
    description: "",
    transactionDate: isoDate(new Date()),
  };

  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: defaults });

  const currency = watch("currency");

  const onSubmit = async (data: FormValues) => {
    setServerError(null);
    try {
      const tx = await create.mutateAsync({
        amount: parseAmount(data.amount)!,
        currency: data.currency,
        transactionDate: data.transactionDate,
        description: data.description.trim(),
        merchant: data.merchant?.trim() || undefined,
        type: data.type,
        category: (data.category as TransactionCategory) || undefined,
        notes: data.notes || undefined,
      });
      setSaved(tx);
      markNewTransaction(tx.id);
      toast({ title: "Transaction added", tone: "success" });
      announce("Transaction saved.");
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Could not save the transaction.";
      setServerError(msg);
      announce(msg, "assertive");
    }
  };

  const addAnother = () => {
    reset({ ...defaults, currency, type: watch("type") });
    setSaved(null);
  };

  return (
    <div className="max-w-xl space-y-sp-giant">
      <TitleRow
        title="Add manually"
        description="Leave the category blank and we will suggest one, with how sure we are."
        actions={
          <Button variant="ghost" size="sm" asChild>
            <Link href="/dashboard/transactions">
              <ArrowLeft aria-hidden="true" />
              Back to transactions
            </Link>
          </Button>
        }
      />

      {saved ? (
        <SavedTransactionPanel transaction={saved} title="Transaction saved">
          <div className="flex flex-wrap gap-2">
            <Button onClick={addAnother}>
              <Plus aria-hidden="true" />
              Add another
            </Button>
            <Button variant="outline" asChild>
              <Link href="/dashboard/transactions">Go to transactions</Link>
            </Button>
          </div>
        </SavedTransactionPanel>
      ) : (
        <div className="rounded-md bg-chrome p-4 sm:p-6">
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
            {serverError && (
              <div role="alert" className="rounded-md border border-warning/25 bg-warning-soft px-3 py-2.5 text-sm text-warning">
                {serverError}
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Date" required error={errors.transactionDate?.message}>
                <Input type="date" max={isoDate(new Date())} {...register("transactionDate")} />
              </Field>
              <Field label="Type" error={errors.type?.message}>
                <Controller
                  name="type"
                  control={control}
                  render={({ field }) => (
                    <Select onValueChange={field.onChange} value={field.value}>
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

            <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
              <Field label="Amount" required error={errors.amount?.message}>
                <div className="relative">
                  <span aria-hidden="true" className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                    {currencySymbol(currency)}
                  </span>
                  <Input
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder="0.00"
                    autoFocus
                    className="pl-10 tabular-nums"
                    {...register("amount")}
                  />
                </div>
              </Field>
              <Field label="Currency" required error={errors.currency?.message}>
                <Controller
                  name="currency"
                  control={control}
                  render={({ field }) => <CurrencySelect value={field.value} onChange={field.onChange} />}
                />
              </Field>
            </div>

            <Field label="Description" required error={errors.description?.message}>
              <Input placeholder="e.g. Zomato order, Amazon purchase, Salary credit" {...register("description")} />
            </Field>

            <Field label="Merchant" hint="Optional. We work it out from the description when blank." error={errors.merchant?.message}>
              <Input placeholder="e.g. Zomato, Amazon, HDFC Bank" {...register("merchant")} />
            </Field>

            <Field label="Category" hint="Optional. Leave on Suggest for us to pick, and correct it after saving.">
              <Controller
                name="category"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={(v) => field.onChange(v === AUTO ? undefined : v)} value={field.value ?? AUTO}>
                    <SelectTrigger>
                      <SelectValue placeholder="Suggest a category" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={AUTO}>Suggest a category</SelectItem>
                      {CATEGORY_OPTIONS.map((opt) => (
                        <SelectItem key={opt.value} value={opt.value}>
                          <span className="flex items-center gap-2">
                            <CategoryDot category={opt.value} />
                            {opt.label}
                          </span>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>

            <Field label="Notes" hint="Optional." error={errors.notes?.message}>
              <Textarea className="min-h-[72px] resize-none" placeholder="Anything worth remembering" {...register("notes")} />
            </Field>

            <div className="flex flex-wrap items-center gap-3 pt-1">
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" aria-hidden="true" />}
                {isSubmitting ? "Saving" : "Save transaction"}
              </Button>
              <Button type="button" variant="outline" asChild>
                <Link href="/dashboard/transactions">Cancel</Link>
              </Button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
