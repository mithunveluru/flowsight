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
import { StatusMarker } from "@/components/ui/signals";
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
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import { CategoryPill } from "@/components/ui/category-pill";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const STATUS_META: Record<BudgetStatus, { label: string; cls: string; bar: string; iconBg: string }> = {
  ON_TRACK:       { label: "On track",      cls: "text-positive", bar: "bg-positive", iconBg: "bg-positive-soft border-positive/20 text-positive" },
  NEAR_LIMIT:     { label: "Near limit",    cls: "text-caution",   bar: "bg-caution",   iconBg: "bg-caution-soft   border-caution/20   text-caution" },
  PROJECTED_OVER: { label: "Trending over", cls: "text-caution",   bar: "bg-caution",   iconBg: "bg-caution-soft   border-caution/20   text-caution" },
  OVER:           { label: "Over budget",   cls: "text-warning",     bar: "bg-warning",     iconBg: "bg-warning-soft     border-warning/20     text-warning" },
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
          <h1 className="text-xl font-semibold text-foreground">Budgets</h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            Set monthly limits per category, and watch progress throughout the month.
          </p>
        </div>
        <Button size="sm" onClick={() => { setEditing(null); setShowForm(true); }}>
          <Plus className="h-4 w-4" /> New budget
        </Button>
      </div>

      {error && (
        <p className="flex items-center gap-1.5 text-sm text-warning">
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
    { label: "Budgeted",        value: formatINR(summary.totalBudgeted), icon: Wallet,        cls: "text-brand",    bg: "bg-brand-soft border-brand/20"        },
    { label: "Spent",           value: formatINR(summary.totalSpent),    icon: TrendingDown,  cls: "text-caution",  bg: "bg-caution-soft border-caution/20"     },
    { label: "Remaining",       value: formatINR(remaining),             icon: TrendingUp,    cls: "text-positive", bg: "bg-positive-soft border-positive/20"   },
    { label: "Over budget",     value: String(summary.overBudgetCount),  icon: AlertTriangle, cls: "text-warning",     bg: "bg-warning-soft border-warning/20"           },
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((c) => (
        <div key={c.label} className="rounded-lg border border-border bg-card p-5">
          <div className="flex items-center gap-2 mb-2">
            <div className={cn("flex h-7 w-7 items-center justify-center rounded-md border", c.bg)}>
              <c.icon className={cn("h-3.5 w-3.5", c.cls)} />
            </div>
            <span className="text-xs font-medium text-muted-foreground">{c.label}</span>
          </div>
          <p className="text-2xl font-semibold tabular-nums text-foreground">{c.value}</p>
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
  const pct = Math.min(100, budget.percentUsed);

  return (
    <div className="relative overflow-hidden rounded-lg border border-border bg-card p-5 group hover:border-muted-foreground/30 transition-colors">
      <span aria-hidden="true" className={cn("absolute inset-y-0 left-0 w-[3px]", meta.bar)} />
      <div className="flex items-start justify-between mb-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-foreground truncate">
              {budget.categoryDisplayName}
            </p>
            {budget.category && <CategoryPill category={budget.category} />}
          </div>
          <p className="mt-0.5 text-xs text-muted-foreground/70">
            {budget.daysRemaining} days left in month
          </p>
        </div>
        <StatusMarker label={meta.label} className={cn("shrink-0", meta.cls)} />
      </div>

      {/* Progress bar */}
      <div className="space-y-2">
        <div className="h-2 rounded-full bg-muted overflow-hidden">
          <div
            className={cn("h-full transition-all duration-500", meta.bar)}
            style={{ width: `${pct}%` }}
          />
        </div>
        <div className="flex items-baseline justify-between">
          <p className="text-sm text-foreground">
            <span className="font-semibold tabular-nums">{formatINR(budget.currentSpend)}</span>
            <span className="ml-1 text-xs text-muted-foreground/70">
              of {formatINR(budget.monthlyLimit)}
            </span>
          </p>
          <p className="text-xs font-medium text-muted-foreground tabular-nums">
            {budget.percentUsed.toFixed(0)}%
          </p>
        </div>
        {budget.projectedTotal > budget.monthlyLimit && (
          <p className="text-xs text-caution flex items-center gap-1">
            <AlertTriangle className="h-3 w-3" />
            Projected {formatINR(budget.projectedTotal)} at current pace
          </p>
        )}
      </div>

      {/* Hover actions */}
      <div className="mt-4 flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={onEdit}
          className="rounded p-1.5 text-muted-foreground/70 hover:text-foreground/80 hover:bg-muted"
        >
          <Wallet className="h-3.5 w-3.5" />
        </button>
        <button
          onClick={onDelete}
          className="rounded p-1.5 text-muted-foreground/70 hover:text-warning hover:bg-warning-soft"
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
      <div className="flex-1 bg-primary/20" onClick={onClose} />
      <div className="w-full max-w-md bg-card shadow-xl flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-border">
          <h2 className="text-base font-semibold text-foreground">
            {budget ? "Edit budget" : "New budget"}
          </h2>
          <button onClick={onClose} className="text-muted-foreground/70 hover:text-foreground/80">
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
              <p className="text-xs text-muted-foreground/70">Category cannot be changed after creation.</p>
            )}
          </div>

          {/* Monthly limit */}
          <div className="space-y-1.5">
            <Label htmlFor="monthlyLimit">Monthly limit</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">INR</span>
              <Input
                id="monthlyLimit"
                type="number"
                step="0.01"
                min="0.01"
                {...register("monthlyLimit")}
                placeholder="0.00"
                className={cn("flex-1", errors.monthlyLimit && "border-warning/40")}
              />
            </div>
            {errors.monthlyLimit && (
              <p className="text-xs text-warning">{errors.monthlyLimit.message}</p>
            )}
          </div>

          {/* Rollover */}
          <div className="flex items-start gap-2 rounded-md border border-border p-3">
            <input
              id="rollover"
              type="checkbox"
              {...register("rollover")}
              className="mt-0.5 h-4 w-4 rounded border-muted-foreground/30"
            />
            <div>
              <Label htmlFor="rollover" className="text-sm font-medium text-foreground">
                Rollover unused amount
              </Label>
              <p className="mt-0.5 text-xs text-muted-foreground">
                If you stay under budget, the remainder carries forward to next month.
              </p>
            </div>
          </div>

          {serverError && (
            <p className="flex items-center gap-1.5 text-sm text-warning">
              <XCircle className="h-4 w-4 shrink-0" /> {serverError}
            </p>
          )}
        </form>

        <div className="border-t border-border p-5 flex items-center gap-2">
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
    <div className="rounded-lg border border-border bg-card px-6 py-16 text-center">
      <Target className="mx-auto h-10 w-10 text-muted-foreground/30 mb-3" />
      <p className="text-sm font-medium text-foreground">No budgets set</p>
      <p className="mt-1 text-xs text-muted-foreground/70 max-w-xs mx-auto">
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
