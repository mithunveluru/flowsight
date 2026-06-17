"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  CheckCircle2,
  Plus,
  Target,
  Trash2,
  TrendingDown,
  TrendingUp,
  Wallet,
  X,
  XCircle,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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
import { budgetsApi } from "@/features/budgets/api";
import type { Budget, BudgetStatus, BudgetSummary } from "@/features/budgets/types";
import {
  CATEGORY_META,
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const STATUS_META: Record<BudgetStatus, { label: string; cls: string; bar: string; iconBg: string }> = {
  ON_TRACK:       { label: "On track",      cls: "border-emerald-200 bg-emerald-50 text-emerald-700", bar: "bg-emerald-500", iconBg: "bg-emerald-50 border-emerald-100 text-emerald-600" },
  NEAR_LIMIT:     { label: "Near limit",    cls: "border-amber-200   bg-amber-50   text-amber-700",   bar: "bg-amber-500",   iconBg: "bg-amber-50   border-amber-100   text-amber-600" },
  PROJECTED_OVER: { label: "Trending over", cls: "border-amber-200   bg-amber-50   text-amber-700",   bar: "bg-amber-500",   iconBg: "bg-amber-50   border-amber-100   text-amber-600" },
  OVER:           { label: "Over budget",   cls: "border-red-200     bg-red-50     text-red-700",     bar: "bg-red-500",     iconBg: "bg-red-50     border-red-100     text-red-600" },
};

const schema = z.object({
  category:     z.string().optional(),
  monthlyLimit: z.coerce.number({ invalid_type_error: "Enter a valid amount" }).positive("Amount must be greater than zero"),
  rollover:     z.boolean().optional(),
});
type FormValues = z.infer<typeof schema>;

export default function BudgetsPage() {
  const [summary,  setSummary]  = useState<BudgetSummary | null>(null);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing,  setEditing]  = useState<Budget | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSummary(await budgetsApi.summary());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load budgets");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleSave = async () => {
    setShowForm(false);
    setEditing(null);
    await load();
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this budget?")) return;
    try {
      await budgetsApi.delete(id);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to delete budget");
    }
  };

  const handleEdit = (b: Budget) => {
    setEditing(b);
    setShowForm(true);
  };

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Budgets</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            Set monthly limits per category, and watch progress throughout the month.
          </p>
        </div>
        <Button size="sm" onClick={() => { setEditing(null); setShowForm(true); }}>
          <Plus className="h-4 w-4" /> New budget
        </Button>
      </div>

      {error && (
        <p className="flex items-center gap-1.5 text-sm text-red-600">
          <XCircle className="h-4 w-4 shrink-0" /> {error}
        </p>
      )}

      {loading ? (
        <LoadingSkeleton />
      ) : summary && summary.budgetCount > 0 ? (
        <>
          {/* Summary header */}
          <SummaryCards summary={summary} />

          {/* Budget grid */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {summary.budgets.map((b) => (
              <BudgetCard
                key={b.id}
                budget={b}
                onEdit={() => handleEdit(b)}
                onDelete={() => handleDelete(b.id)}
              />
            ))}
          </div>
        </>
      ) : (
        <EmptyState onCreate={() => setShowForm(true)} />
      )}

      {/* Slide-over form */}
      {showForm && (
        <BudgetFormDrawer
          budget={editing}
          existingCategories={summary?.budgets.map((b) => b.category).filter(Boolean) as TransactionCategory[] ?? []}
          hasOverall={summary?.budgets.some((b) => b.category === null) ?? false}
          onClose={() => { setShowForm(false); setEditing(null); }}
          onSaved={handleSave}
        />
      )}
    </div>
  );
}

function SummaryCards({ summary }: { summary: BudgetSummary }) {
  const remaining = Math.max(0, summary.totalBudgeted - summary.totalSpent);
  const cards = [
    { label: "Budgeted",        value: formatINR(summary.totalBudgeted), icon: Wallet,        cls: "text-blue-500",    bg: "bg-blue-50 border-blue-100"        },
    { label: "Spent",           value: formatINR(summary.totalSpent),    icon: TrendingDown,  cls: "text-orange-500",  bg: "bg-orange-50 border-orange-100"     },
    { label: "Remaining",       value: formatINR(remaining),             icon: TrendingUp,    cls: "text-emerald-500", bg: "bg-emerald-50 border-emerald-100"   },
    { label: "Over budget",     value: String(summary.overBudgetCount),  icon: AlertTriangle, cls: "text-red-500",     bg: "bg-red-50 border-red-100"           },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((c) => (
        <div key={c.label} className="rounded-lg border border-slate-200 bg-white p-5">
          <div className="flex items-center gap-2 mb-2">
            <div className={cn("flex h-7 w-7 items-center justify-center rounded-md border", c.bg)}>
              <c.icon className={cn("h-3.5 w-3.5", c.cls)} />
            </div>
            <span className="text-xs font-medium text-slate-500">{c.label}</span>
          </div>
          <p className="text-2xl font-semibold tabular-nums text-slate-900">{c.value}</p>
        </div>
      ))}
    </div>
  );
}

function BudgetCard({
  budget, onEdit, onDelete,
}: {
  budget: Budget;
  onEdit:   () => void;
  onDelete: () => void;
}) {
  const meta = STATUS_META[budget.status];
  const cat = budget.category ? CATEGORY_META[budget.category] : null;
  const pct = Math.min(100, budget.percentUsed);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 group hover:border-slate-300 transition-colors">
      <div className="flex items-start justify-between mb-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-slate-900 truncate">
              {budget.categoryDisplayName}
            </p>
            {cat && (
              <span className={cn("inline-flex items-center rounded-md border px-1.5 py-0.5 text-xs font-medium", cat.color)}>
                {cat.label}
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs text-slate-400">
            {budget.daysRemaining} days left in month
          </p>
        </div>
        <span className={cn("inline-flex items-center shrink-0 rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap", meta.cls)}>
          {meta.label}
        </span>
      </div>

      {/* Progress bar */}
      <div className="space-y-2">
        <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
          <div
            className={cn("h-full transition-all duration-500", meta.bar)}
            style={{ width: `${pct}%` }}
          />
        </div>
        <div className="flex items-baseline justify-between">
          <p className="text-sm text-slate-900">
            <span className="font-semibold tabular-nums">{formatINR(budget.currentSpend)}</span>
            <span className="ml-1 text-xs text-slate-400">
              of {formatINR(budget.monthlyLimit)}
            </span>
          </p>
          <p className="text-xs font-medium text-slate-600 tabular-nums">
            {budget.percentUsed.toFixed(0)}%
          </p>
        </div>
        {budget.projectedTotal > budget.monthlyLimit && (
          <p className="text-xs text-amber-600 flex items-center gap-1">
            <AlertTriangle className="h-3 w-3" />
            Projected {formatINR(budget.projectedTotal)} at current pace
          </p>
        )}
      </div>

      {/* Hover actions */}
      <div className="mt-4 flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={onEdit}
          className="rounded p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100"
        >
          <Wallet className="h-3.5 w-3.5" />
        </button>
        <button
          onClick={onDelete}
          className="rounded p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function BudgetFormDrawer({
  budget,
  existingCategories,
  hasOverall,
  onClose,
  onSaved,
}: {
  budget: Budget | null;
  existingCategories: TransactionCategory[];
  hasOverall: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      category:     budget?.category ?? undefined,
      monthlyLimit: budget?.monthlyLimit ?? 0,
      rollover:     budget?.rollover ?? false,
    },
  });

  const selectedCategory = watch("category");

  const onSubmit = async (data: FormValues) => {
    setServerError(null);
    try {
      const payload = {
        category:     data.category === "__overall__" || !data.category ? undefined : (data.category as TransactionCategory),
        monthlyLimit: data.monthlyLimit,
        rollover:     data.rollover ?? false,
      };
      if (budget) {
        await budgetsApi.update(budget.id, payload);
      } else {
        await budgetsApi.create(payload);
      }
      onSaved();
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : "Failed to save budget");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-slate-900/20" onClick={onClose} />
      <div className="w-full max-w-md bg-white shadow-xl flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-slate-200">
          <h2 className="text-base font-semibold text-slate-900">
            {budget ? "Edit budget" : "New budget"}
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Category */}
          <div className="space-y-1.5">
            <Label>Category</Label>
            <Select
              value={selectedCategory ?? "__overall__"}
              onValueChange={(v) => setValue("category", v === "__overall__" ? undefined : v, { shouldValidate: true })}
              disabled={!!budget}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__overall__" disabled={hasOverall && !budget}>
                  Overall (all categories)
                </SelectItem>
                {CATEGORY_OPTIONS.map((opt) => (
                  <SelectItem
                    key={opt.value}
                    value={opt.value}
                    disabled={existingCategories.includes(opt.value) && budget?.category !== opt.value}
                  >
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {budget && (
              <p className="text-xs text-slate-400">Category cannot be changed after creation.</p>
            )}
          </div>

          {/* Monthly limit */}
          <div className="space-y-1.5">
            <Label htmlFor="monthlyLimit">Monthly limit</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-500">INR</span>
              <Input
                id="monthlyLimit"
                type="number"
                step="0.01"
                min="0.01"
                {...register("monthlyLimit")}
                placeholder="0.00"
                className={cn("flex-1", errors.monthlyLimit && "border-red-300")}
              />
            </div>
            {errors.monthlyLimit && (
              <p className="text-xs text-red-600">{errors.monthlyLimit.message}</p>
            )}
          </div>

          {/* Rollover */}
          <div className="flex items-start gap-2 rounded-md border border-slate-200 p-3">
            <input
              id="rollover"
              type="checkbox"
              {...register("rollover")}
              className="mt-0.5 h-4 w-4 rounded border-slate-300"
            />
            <div>
              <Label htmlFor="rollover" className="text-sm font-medium text-slate-900">
                Rollover unused amount
              </Label>
              <p className="mt-0.5 text-xs text-slate-500">
                If you stay under budget, the remainder carries forward to next month.
              </p>
            </div>
          </div>

          {serverError && (
            <p className="flex items-center gap-1.5 text-sm text-red-600">
              <XCircle className="h-4 w-4 shrink-0" /> {serverError}
            </p>
          )}
        </form>

        <div className="border-t border-slate-200 p-5 flex items-center gap-2">
          <Button type="submit" form="" onClick={handleSubmit(onSubmit)} disabled={isSubmitting} className="flex-1">
            {budget ? "Save changes" : "Create budget"}
          </Button>
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-24 rounded-lg skeleton" />
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-32 rounded-lg skeleton" />
        ))}
      </div>
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-6 py-16 text-center">
      <Target className="mx-auto h-10 w-10 text-slate-200 mb-3" />
      <p className="text-sm font-medium text-slate-900">No budgets set</p>
      <p className="mt-1 text-xs text-slate-400 max-w-xs mx-auto">
        Define a monthly limit per category, or one overall cap, to keep your spending in view.
      </p>
      <div className="mt-5 flex justify-center gap-3">
        <Button size="sm" onClick={onCreate}>
          <Plus className="h-4 w-4" /> Create a budget
        </Button>
        <Button size="sm" variant="outline" asChild>
          <Link href="/dashboard/analytics">Review your overview first</Link>
        </Button>
      </div>
    </div>
  );
}
