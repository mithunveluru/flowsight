"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, Pencil, Plus, Target, XCircle } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/components/ui/confirm-button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/dialog";
import { CountUp, Reveal } from "@/components/motion/primitives";
import { Alert, ContentBand, PageTitle, Stat, Surface } from "@/components/insight/page-kit";
import { ProgressBar, type ProgressTone } from "@/components/insight/progress";
import { StatusMarker } from "@/components/ui/signals";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { budgetsApi } from "@/features/budgets/api";
import { useBudgetSummary } from "@/features/budgets/hooks";
import { paceView } from "@/features/budgets/pace";
import { forecastEvidence } from "@/features/insights/evidence";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
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

const STATUS_META: Record<BudgetStatus, { label: string; cls: string; tone: ProgressTone }> = {
  ON_TRACK:       { label: "On track",      cls: "text-positive", tone: "primary" },
  NEAR_LIMIT:     { label: "Near limit",    cls: "text-caution",  tone: "navy" },
  PROJECTED_OVER: { label: "Trending over", cls: "text-caution",  tone: "navy" },
  OVER:           { label: "Over budget",   cls: "text-warning",  tone: "warning" },
};

const schema = z.object({
  category:     z.string().optional(),
  monthlyLimit: z.coerce.number({ invalid_type_error: "Enter a valid amount" }).positive("Amount must be greater than zero"),
  rollover:     z.boolean().optional(),
});
type FormValues = z.infer<typeof schema>;

export default function BudgetsPage() {
  const query = useBudgetSummary();
  const summary = query.data ?? null;
  const loading = query.isPending;
  const [actionError, setActionError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing,  setEditing]  = useState<Budget | null>(null);
  const [formKey, setFormKey] = useState(0);

  const load = async () => {
    await query.refetch();
  };

  const handleSave = async () => {
    setShowForm(false);
    setEditing(null);
    await load();
  };

  const handleDelete = async (id: string) => {
    try {
      await budgetsApi.delete(id);
      await load();
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "Failed to delete budget");
    }
  };

  const handleEdit = (b: Budget) => {
    setEditing(b);
    setFormKey((k) => k + 1);
    setShowForm(true);
  };

  const openNew = () => { setEditing(null); setFormKey((k) => k + 1); setShowForm(true); };

  return (
    <div className="space-y-sp-max">
      <PageTitle
        title="Budgets"
        lead="Set monthly limits per category, and watch progress throughout the month."
        actions={
          <Button size="sm" className="coarse:h-11" onClick={openNew}>
            <Plus className="h-4 w-4" aria-hidden="true" /> New budget
          </Button>
        }
      />

      {actionError && <Alert>{actionError}</Alert>}

      {query.isError && !summary ? (
        <ErrorState title="Budgets did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : loading ? (
        <LoadingState variant="stats" label="Loading budgets" />
      ) : summary && summary.budgetCount > 0 ? (
        <>
          <SummaryCards summary={summary} />
          <ContentBand tone="blue" space="md">
            <ul className="grid gap-sp-wide sm:grid-cols-2 lg:grid-cols-3">
              {summary.budgets.map((b) => (
                <BudgetCard
                  key={b.id}
                  budget={b}
                  onEdit={() => handleEdit(b)}
                  onDelete={() => handleDelete(b.id)}
                />
              ))}
            </ul>
          </ContentBand>
        </>
      ) : (
        <EmptyState
          icon={<Target className="h-8 w-8 text-ink-meta" strokeWidth={1.5} aria-hidden="true" />}
          title="No budgets set"
          description="Define a monthly limit per category, or one overall cap, to keep your spending in view."
          action={
            <Button size="sm" className="coarse:h-11" onClick={openNew}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Create a budget
            </Button>
          }
          secondaryAction={
            <Button size="sm" variant="outline" className="coarse:h-11" asChild>
              <Link href="/dashboard/trends">Review your overview first</Link>
            </Button>
          }
        />
      )}

      <BudgetFormDrawer
        key={formKey}
        open={showForm}
        budget={editing}
        existingCategories={summary?.budgets.map((b) => b.category).filter(Boolean) as TransactionCategory[] ?? []}
        hasOverall={summary?.budgets.some((b) => b.category === null) ?? false}
        onClose={() => { setShowForm(false); setEditing(null); }}
        onSaved={handleSave}
      />
    </div>
  );
}

function SummaryCards({ summary }: { summary: BudgetSummary }) {
  const remaining = Math.max(0, summary.totalBudgeted - summary.totalSpent);
  const rupee = (v: number) => formatINR(v);
  return (
    <Reveal>
      <Surface className="grid grid-cols-2 gap-sp-giant lg:grid-cols-4">
        <Stat label="Budgeted"><CountUp value={summary.totalBudgeted} format={rupee} /></Stat>
        <Stat label="Spent"><CountUp value={summary.totalSpent} format={rupee} /></Stat>
        <Stat label="Remaining"><CountUp value={remaining} format={rupee} /></Stat>
        <Stat label="Over budget" hint={summary.overBudgetCount > 0 ? "Needs a look" : "None over"}>
          <span className="inline-flex items-center gap-sp-xs">
            {summary.overBudgetCount > 0 && <AlertTriangle className="h-5 w-5 text-warning" aria-hidden="true" />}
            <CountUp value={summary.overBudgetCount} />
          </span>
        </Stat>
      </Surface>
    </Reveal>
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
  const pace = paceView(budget);
  const limit = budget.effectiveLimit ?? budget.monthlyLimit;

  return (
    <Surface as="li" tone="canvas" className="min-w-0">
      <div className="flex items-start justify-between gap-sp-xl">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-sp-sm">
            <p className="truncate text-ty-link font-medium text-ink">{budget.categoryDisplayName}</p>
            {budget.category && <CategoryPill category={budget.category} />}
          </div>
          <p className="mt-sp-xs text-xs text-ink-meta">{budget.daysRemaining} days left in month</p>
        </div>
        <StatusMarker label={meta.label} className={cn("shrink-0", meta.cls)} />
      </div>

      <div className="mt-sp-wide space-y-sp-sm">
        <ProgressBar
          value={pct}
          tone={meta.tone}
          label={`${budget.categoryDisplayName} budget used`}
          valueText={`${budget.percentUsed.toFixed(0)} percent of ${formatINR(limit)}`}
        />
        <div className="flex items-baseline justify-between">
          <p className="text-sm text-ink">
            <span className="font-medium tabular-nums">{formatINR(budget.currentSpend)}</span>
            <span className="ml-sp-xs text-xs text-ink-meta">of {formatINR(limit)}</span>
          </p>
          <p className="text-xs text-ink-meta tabular-nums">{budget.percentUsed.toFixed(0)}%</p>
        </div>
        {budget.rollover && budget.rolloverAmount != null && budget.rolloverAmount !== 0 && (
          <p className="text-xs text-ink-meta">
            Includes {formatINR(budget.rolloverAmount)} carried over from earlier months.
          </p>
        )}
        {pace && (
          <div className="pt-sp-xs">
            <div
              role="img"
              aria-label={`Spent ${formatINR(budget.currentSpend)}. Projected month-end ${formatINR(pace.projected)}, likely between ${formatINR(pace.lower80)} and ${formatINR(pace.upper80)}. Limit ${formatINR(pace.limit)}.`}
              className="relative h-2.5 rounded-sm bg-band"
            >
              <span
                aria-hidden="true"
                className="absolute inset-y-0 rounded-sm bg-primary/25"
                style={{ left: `${pace.lowAt}%`, width: `${Math.max(1, pace.highAt - pace.lowAt)}%` }}
              />
              <span aria-hidden="true" className="absolute inset-y-0 left-0 rounded-sm bg-primary" style={{ width: `${pace.spentAt}%` }} />
              <span aria-hidden="true" className="absolute -inset-y-0.5 w-0.5 bg-ink" style={{ left: `calc(${pace.limitAt}% - 1px)` }} />
            </div>
            <p className={cn("mt-sp-sm flex flex-wrap items-center gap-x-sp-xs text-xs", pace.risk === "none" ? "text-ink-meta" : "text-caution")}>
              {pace.risk !== "none" && <AlertTriangle className="h-3 w-3" aria-hidden="true" />}
              <span>
                {pace.risk === "likely" ? "Likely over: " : pace.risk === "possible" ? "Could go over: " : "Month-end: "}
                {formatINR(pace.projected)} projected (80% range {formatINR(pace.lower80)} to {formatINR(pace.upper80)})
              </span>
              {budget.pace?.evidence && (
                <EvidenceDrawer
                  title={`${budget.categoryDisplayName} month-end projection`}
                  value={formatINR(pace.projected)}
                  evidence={{
                    ...forecastEvidence(budget.pace.evidence),
                    inputs: [
                      { label: "Spent so far", value: formatINR(budget.currentSpend) },
                      { label: "Limit", value: formatINR(pace.limit) },
                      { label: "Pace method", value: budget.pace.method },
                      { label: "Share of month remaining", value: `${Math.round(budget.pace.remainingShare * 100)}%` },
                      ...(forecastEvidence(budget.pace.evidence).inputs ?? []),
                    ],
                  }}
                  trigger={<EvidenceTrigger label={`the ${budget.categoryDisplayName} projection`} className="coarse:h-11 coarse:min-w-11" />}
                />
              )}
            </p>
          </div>
        )}
      </div>

      <div className="mt-sp-wide flex items-center justify-end gap-sp-xs">
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onEdit}
          aria-label={`Edit ${budget.categoryDisplayName} budget`}
          className="coarse:h-11 coarse:w-11"
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </Button>
        <ConfirmButton onConfirm={onDelete} label="Delete budget" className="p-sp-sm coarse:min-h-11 coarse:min-w-11 coarse:justify-center" />
      </div>
    </Surface>
  );
}

function BudgetFormDrawer({
  open,
  budget,
  existingCategories,
  hasOverall,
  onClose,
  onSaved,
}: {
  open: boolean;
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
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent>
        <SheetHeader className="p-sp-giant pb-0">
          <SheetTitle>{budget ? "Edit budget" : "New budget"}</SheetTitle>
          <SheetDescription>A monthly limit for one category, or one cap across everything.</SheetDescription>
        </SheetHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-sp-wide overflow-y-auto p-sp-giant">
            <Field label="Category" hint={budget ? "Category cannot be changed after creation." : undefined}>
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
            </Field>

            <Field label="Monthly limit" error={errors.monthlyLimit?.message}>
              <div className="flex items-center gap-sp-sm">
                <span className="text-sm text-ink-meta">INR</span>
                <Input
                  type="number"
                  step="0.01"
                  min="0.01"
                  {...register("monthlyLimit")}
                  placeholder="0.00"
                  className="flex-1"
                />
              </div>
            </Field>

            <div className="flex items-start gap-sp-xl rounded-md bg-chrome p-sp-wide">
              <input
                id="rollover"
                type="checkbox"
                {...register("rollover")}
                className="mt-0.5 h-5 w-5 accent-primary"
              />
              <div>
                <label htmlFor="rollover" className="text-sm font-medium text-ink">
                  Rollover unused amount
                </label>
                <p className="mt-sp-xs text-xs text-ink-meta">
                  If you stay under budget, the remainder carries forward to next month.
                </p>
              </div>
            </div>

            {serverError && <Alert>{serverError}</Alert>}
          </div>

          <SheetFooter className="mt-0 flex-row gap-sp-sm p-sp-giant pt-sp-xl">
            <Button type="submit" disabled={isSubmitting} className="flex-1 coarse:h-11">
              {budget ? "Save changes" : "Create budget"}
            </Button>
            <Button type="button" variant="outline" className="coarse:h-11" onClick={onClose}>Cancel</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
