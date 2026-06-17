"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  Flag,
  Plus,
  Sparkles,
  Trash2,
  TrendingUp,
  X,
  XCircle,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { goalsApi } from "@/features/goals/api";
import type { Goal, GoalStatus, PaceStatus } from "@/features/goals/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const PACE_META: Record<PaceStatus, { label: string; cls: string }> = {
  ON_PACE:   { label: "On pace",     cls: "border-blue-200    bg-blue-50    text-blue-700"    },
  AHEAD:     { label: "Ahead",       cls: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  BEHIND:    { label: "Behind",      cls: "border-amber-200   bg-amber-50   text-amber-700"   },
  COMPLETED: { label: "Completed",   cls: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  OVERDUE:   { label: "Overdue",     cls: "border-red-200     bg-red-50     text-red-700"     },
  ABANDONED: { label: "Abandoned",   cls: "border-slate-200   bg-slate-100  text-slate-500"   },
};

const ICON_CHOICES = ["🏠", "✈️", "🚗", "💍", "🎓", "💼", "🏖️", "💰", "📱", "💻", "🎯", "🎁"];

const goalSchema = z.object({
  name:          z.string().min(1, "Name is required").max(255),
  targetAmount:  z.coerce.number({ invalid_type_error: "Enter a valid amount" }).positive("Target must be greater than zero"),
  currentAmount: z.coerce.number().min(0).optional(),
  targetDate:    z.string().min(1, "Target date is required"),
  icon:          z.string().optional(),
});
type GoalFormValues = z.infer<typeof goalSchema>;

const contributionSchema = z.object({
  amount: z.coerce.number({ invalid_type_error: "Enter an amount" }).positive("Amount must be greater than zero"),
});
type ContributionValues = z.infer<typeof contributionSchema>;

export default function GoalsPage() {
  const [goals,    setGoals]    = useState<Goal[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing,  setEditing]  = useState<Goal | null>(null);
  const [contributingId, setContributingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setGoals(await goalsApi.list());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load goals");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this goal?")) return;
    await goalsApi.delete(id);
    await load();
  };

  const active    = goals.filter((g) => g.status === "ACTIVE");
  const completed = goals.filter((g) => g.status === "COMPLETED");

  const totalTarget   = active.reduce((s, g) => s + Number(g.targetAmount), 0);
  const totalSaved    = active.reduce((s, g) => s + Number(g.currentAmount), 0);
  const overallPct    = totalTarget > 0 ? (totalSaved / totalTarget) * 100 : 0;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Goals</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            Save toward what matters, and watch your progress over time.
          </p>
        </div>
        <Button size="sm" onClick={() => { setEditing(null); setShowForm(true); }}>
          <Plus className="h-4 w-4" /> New goal
        </Button>
      </div>

      {error && (
        <p className="flex items-center gap-1.5 text-sm text-red-600">
          <XCircle className="h-4 w-4 shrink-0" /> {error}
        </p>
      )}

      {loading ? (
        <LoadingSkeleton />
      ) : goals.length === 0 ? (
        <EmptyState onCreate={() => setShowForm(true)} />
      ) : (
        <>
          {/* Overall progress */}
          {active.length > 0 && (
            <div className="rounded-lg border border-slate-200 bg-white p-5">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                    Overall progress
                  </p>
                  <p className="mt-1 text-2xl font-semibold text-slate-900 tabular-nums">
                    {formatINR(totalSaved)}{" "}
                    <span className="text-sm font-normal text-slate-400">of {formatINR(totalTarget)}</span>
                  </p>
                </div>
                <p className="text-sm font-medium text-slate-600 tabular-nums">
                  {overallPct.toFixed(0)}%
                </p>
              </div>
              <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                <div
                  className="h-full bg-emerald-500 transition-all duration-500"
                  style={{ width: `${Math.min(100, overallPct)}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-slate-500">
                {active.length} active goal{active.length === 1 ? "" : "s"} · {completed.length} completed
              </p>
            </div>
          )}

          {/* Active goals */}
          {active.length > 0 && (
            <section className="space-y-3">
              <p className="text-sm font-semibold text-slate-900">Active goals</p>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {active.map((g) => (
                  <GoalCard
                    key={g.id}
                    goal={g}
                    onEdit={() => { setEditing(g); setShowForm(true); }}
                    onDelete={() => handleDelete(g.id)}
                    onContribute={() => setContributingId(g.id)}
                  />
                ))}
              </div>
            </section>
          )}

          {/* Completed goals */}
          {completed.length > 0 && (
            <section className="space-y-3">
              <p className="text-sm font-semibold text-slate-900">Completed</p>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {completed.map((g) => (
                  <GoalCard
                    key={g.id}
                    goal={g}
                    onEdit={() => { setEditing(g); setShowForm(true); }}
                    onDelete={() => handleDelete(g.id)}
                  />
                ))}
              </div>
            </section>
          )}
        </>
      )}

      {showForm && (
        <GoalFormDrawer
          goal={editing}
          onClose={() => { setShowForm(false); setEditing(null); }}
          onSaved={async () => { setShowForm(false); setEditing(null); await load(); }}
        />
      )}

      {contributingId && (
        <ContributeDrawer
          goalId={contributingId}
          onClose={() => setContributingId(null)}
          onSaved={async () => { setContributingId(null); await load(); }}
        />
      )}
    </div>
  );
}

function GoalCard({
  goal, onEdit, onDelete, onContribute,
}: {
  goal: Goal;
  onEdit:       () => void;
  onDelete:     () => void;
  onContribute?: () => void;
}) {
  const paceMeta = PACE_META[goal.paceStatus];
  const pct      = Math.min(100, goal.percentComplete);
  const targetDate = new Date(goal.targetDate).toLocaleDateString("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  });
  const isCompleted = goal.status === "COMPLETED" || goal.paceStatus === "COMPLETED";

  return (
    <div className={cn(
      "rounded-lg border bg-white p-5 group transition-colors",
      isCompleted ? "border-emerald-200" : "border-slate-200 hover:border-slate-300"
    )}>
      <div className="flex items-start justify-between mb-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            {goal.icon && <span className="text-lg">{goal.icon}</span>}
            <p className="text-sm font-semibold text-slate-900 truncate">{goal.name}</p>
          </div>
          <p className="mt-0.5 text-xs text-slate-400">Target {targetDate}</p>
        </div>
        <span className={cn("inline-flex items-center shrink-0 rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap", paceMeta.cls)}>
          {paceMeta.label}
        </span>
      </div>

      <div className="space-y-2">
        <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
          <div
            className={cn("h-full transition-all duration-500",
              isCompleted ? "bg-emerald-500" : "bg-blue-500")}
            style={{ width: `${pct}%` }}
          />
        </div>
        <div className="flex items-baseline justify-between">
          <p className="text-sm text-slate-900">
            <span className="font-semibold tabular-nums">{formatINR(goal.currentAmount)}</span>
            <span className="ml-1 text-xs text-slate-400">
              of {formatINR(goal.targetAmount)}
            </span>
          </p>
          <p className="text-xs font-medium text-slate-600 tabular-nums">
            {goal.percentComplete.toFixed(0)}%
          </p>
        </div>
        {!isCompleted && goal.daysRemaining > 0 && (
          <p className="text-xs text-slate-500">
            {formatINR(goal.dailyPaceRequired)}/day for {goal.daysRemaining} days
          </p>
        )}
      </div>

      <div className="mt-4 flex items-center justify-between gap-2">
        {!isCompleted && onContribute && (
          <Button size="sm" variant="outline" onClick={onContribute} className="flex-1">
            <Plus className="h-3 w-3" /> Contribute
          </Button>
        )}
        <div className={cn("flex items-center gap-1", !isCompleted && "opacity-0 group-hover:opacity-100 transition-opacity")}>
          <button onClick={onEdit}   className="rounded p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100"><Sparkles className="h-3.5 w-3.5" /></button>
          <button onClick={onDelete} className="rounded p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50"><Trash2 className="h-3.5 w-3.5" /></button>
        </div>
      </div>
    </div>
  );
}

function GoalFormDrawer({
  goal, onClose, onSaved,
}: {
  goal: Goal | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [serverError, setServerError] = useState<string | null>(null);
  const [selectedIcon, setSelectedIcon] = useState<string>(goal?.icon ?? ICON_CHOICES[0]);

  const tomorrow = new Date(Date.now() + 86400000).toISOString().split("T")[0];

  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<GoalFormValues>({
      resolver: zodResolver(goalSchema),
      defaultValues: {
        name:          goal?.name ?? "",
        targetAmount:  goal?.targetAmount ?? 0,
        currentAmount: goal?.currentAmount ?? 0,
        targetDate:    goal?.targetDate?.split("T")[0] ?? "",
        icon:          goal?.icon ?? ICON_CHOICES[0],
      },
    });

  const onSubmit = async (data: GoalFormValues) => {
    setServerError(null);
    try {
      const payload = {
        name:          data.name,
        targetAmount:  data.targetAmount,
        currentAmount: data.currentAmount,
        targetDate:    data.targetDate,
        icon:          selectedIcon,
      };
      if (goal) await goalsApi.update(goal.id, payload);
      else      await goalsApi.create(payload);
      onSaved();
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : "Failed to save goal");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-slate-900/20" onClick={onClose} />
      <div className="w-full max-w-md bg-white shadow-xl flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-slate-200">
          <h2 className="text-base font-semibold text-slate-900">
            {goal ? "Edit goal" : "New goal"}
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Icon picker */}
          <div className="space-y-1.5">
            <Label>Icon</Label>
            <div className="grid grid-cols-6 gap-2">
              {ICON_CHOICES.map((ic) => (
                <button
                  key={ic}
                  type="button"
                  onClick={() => setSelectedIcon(ic)}
                  className={cn(
                    "flex h-10 w-10 items-center justify-center rounded-md border text-lg transition-colors",
                    selectedIcon === ic
                      ? "border-blue-400 bg-blue-50"
                      : "border-slate-200 hover:border-slate-300 hover:bg-slate-50"
                  )}
                >
                  {ic}
                </button>
              ))}
            </div>
          </div>

          {/* Name */}
          <div className="space-y-1.5">
            <Label htmlFor="name">Goal name</Label>
            <Input id="name" {...register("name")} placeholder="e.g. New laptop" className={cn(errors.name && "border-red-300")} />
            {errors.name && <p className="text-xs text-red-600">{errors.name.message}</p>}
          </div>

          {/* Target amount */}
          <div className="space-y-1.5">
            <Label htmlFor="targetAmount">Target amount</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-500">INR</span>
              <Input id="targetAmount" type="number" step="0.01" min="0.01" {...register("targetAmount")} placeholder="0.00" className={cn("flex-1", errors.targetAmount && "border-red-300")} />
            </div>
            {errors.targetAmount && <p className="text-xs text-red-600">{errors.targetAmount.message}</p>}
          </div>

          {/* Current amount */}
          <div className="space-y-1.5">
            <Label htmlFor="currentAmount">Already saved (optional)</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-500">INR</span>
              <Input id="currentAmount" type="number" step="0.01" min="0" {...register("currentAmount")} placeholder="0.00" className="flex-1" />
            </div>
          </div>

          {/* Target date */}
          <div className="space-y-1.5">
            <Label htmlFor="targetDate">Target date</Label>
            <Input id="targetDate" type="date" min={tomorrow} {...register("targetDate")} className={cn(errors.targetDate && "border-red-300")} />
            {errors.targetDate && <p className="text-xs text-red-600">{errors.targetDate.message}</p>}
          </div>

          {serverError && (
            <p className="flex items-center gap-1.5 text-sm text-red-600">
              <XCircle className="h-4 w-4 shrink-0" /> {serverError}
            </p>
          )}
        </form>

        <div className="border-t border-slate-200 p-5 flex items-center gap-2">
          <Button type="button" onClick={handleSubmit(onSubmit)} disabled={isSubmitting} className="flex-1">
            {goal ? "Save changes" : "Create goal"}
          </Button>
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

function ContributeDrawer({
  goalId, onClose, onSaved,
}: {
  goalId: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [serverError, setServerError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<ContributionValues>({ resolver: zodResolver(contributionSchema) });

  const onSubmit = async (data: ContributionValues) => {
    setServerError(null);
    try {
      await goalsApi.contribute(goalId, { amount: data.amount });
      onSaved();
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : "Failed to record contribution");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-slate-900/20" onClick={onClose} />
      <div className="w-full max-w-sm bg-white shadow-xl flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-slate-200">
          <h2 className="text-base font-semibold text-slate-900">Record contribution</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700"><X className="h-4 w-4" /></button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex-1 p-5 space-y-4">
          <p className="text-sm text-slate-500">
            How much did you save toward this goal?
          </p>
          <div className="space-y-1.5">
            <Label htmlFor="amount">Amount</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-500">INR</span>
              <Input id="amount" type="number" step="0.01" min="0.01" {...register("amount")} placeholder="0.00" className={cn("flex-1", errors.amount && "border-red-300")} />
            </div>
            {errors.amount && <p className="text-xs text-red-600">{errors.amount.message}</p>}
          </div>

          {serverError && (
            <p className="flex items-center gap-1.5 text-sm text-red-600">
              <XCircle className="h-4 w-4 shrink-0" /> {serverError}
            </p>
          )}
        </form>

        <div className="border-t border-slate-200 p-5 flex items-center gap-2">
          <Button type="button" onClick={handleSubmit(onSubmit)} disabled={isSubmitting} className="flex-1">
            <CheckCircle2 className="h-4 w-4" /> Add contribution
          </Button>
          <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
        </div>
      </div>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {[1, 2, 3].map((i) => (
        <div key={i} className="h-40 rounded-lg skeleton" />
      ))}
    </div>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-6 py-16 text-center">
      <Flag className="mx-auto h-10 w-10 text-slate-200 mb-3" />
      <p className="text-sm font-medium text-slate-900">No goals set</p>
      <p className="mt-1 text-xs text-slate-400 max-w-xs mx-auto">
        Name a target with a deadline. We will track your progress and suggest a daily pace.
      </p>
      <Button size="sm" className="mt-5" onClick={onCreate}>
        <Plus className="h-4 w-4" /> Create a goal
      </Button>
    </div>
  );
}
