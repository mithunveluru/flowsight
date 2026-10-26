"use client";

import { TitleRow } from "@/components/layout/title-row";
import { useState } from "react";
import { CheckCircle2, CircleAlert, CircleCheck, CircleDot, Flag, Pencil, Plus } from "lucide-react";
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
import { Alert, ContentBand, SectionHead, Surface } from "@/components/insight/page-kit";
import { ProgressBar, type ProgressTone } from "@/components/insight/progress";
import { StatusMarker } from "@/components/ui/signals";
import { goalsApi } from "@/features/goals/api";
import { useGoals } from "@/features/goals/hooks";
import { assessReach, type ReachAssessment } from "@/features/goals/reach";
import { useForecast } from "@/features/analytics/hooks";
import { forecastEvidence } from "@/features/insights/evidence";
import type { ForecastSeries } from "@/features/analytics/types";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import type { Goal, GoalStatus, PaceStatus } from "@/features/goals/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";

function formatINR(v: number) {
  return `₹${Number(v).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

const PACE_META: Record<PaceStatus, { label: string; cls: string }> = {
  ON_PACE:   { label: "On pace",   cls: "text-primary" },
  AHEAD:     { label: "Ahead",     cls: "text-positive" },
  BEHIND:    { label: "Behind",    cls: "text-caution" },
  COMPLETED: { label: "Completed", cls: "text-positive" },
  OVERDUE:   { label: "Overdue",   cls: "text-warning" },
  ABANDONED: { label: "Abandoned", cls: "text-ink-meta" },
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
  const query = useGoals();
  const forecast = useForecast(6);
  const goals = query.data ?? [];
  const loading = query.isPending;
  const [error,    setError]    = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editing,  setEditing]  = useState<Goal | null>(null);
  const [formKey,  setFormKey]  = useState(0);
  const [contributingId, setContributingId] = useState<string | null>(null);

  const load = async () => {
    await query.refetch();
  };

  const handleDelete = async (id: string) => {
    try {
      await goalsApi.delete(id);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to delete goal");
    }
  };

  const openForm = (g: Goal | null) => { setEditing(g); setFormKey((k) => k + 1); setShowForm(true); };

  const active    = goals.filter((g) => g.status === "ACTIVE");
  const completed = goals.filter((g) => g.status === "COMPLETED");

  const totalTarget   = active.reduce((s, g) => s + Number(g.targetAmount), 0);
  const totalSaved    = active.reduce((s, g) => s + Number(g.currentAmount), 0);
  const overallPct    = totalTarget > 0 ? (totalSaved / totalTarget) * 100 : 0;

  return (
    <div className="space-y-sp-max">
      <TitleRow
        rule={false}
        title="Goals"
        description="Save toward what matters, and watch your progress over time."
        actions={
          <Button size="sm" className="coarse:h-11" onClick={() => openForm(null)}>
            <Plus className="h-4 w-4" aria-hidden="true" /> New goal
          </Button>
        }
      />

      {error && <Alert>{error}</Alert>}

      {query.isError && !query.data ? (
        <ErrorState title="Goals did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : loading ? (
        <LoadingState variant="card" rows={3} label="Loading goals" />
      ) : goals.length === 0 ? (
        <EmptyState
          icon={<Flag className="h-8 w-8 text-ink-meta" strokeWidth={1.5} aria-hidden="true" />}
          title="No goals set"
          description="Name a target with a deadline. We will track your progress and suggest a daily pace."
          action={
            <Button size="sm" className="coarse:h-11" onClick={() => openForm(null)}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Create a goal
            </Button>
          }
        />
      ) : (
        <>
          {active.length > 0 && (
            <Reveal>
              <Surface>
                <div className="flex items-start justify-between gap-sp-xl">
                  <div>
                    <p className="text-sm text-ink-meta">Overall progress</p>
                    <p className="mt-sp-xs text-ty-section-title tabular-nums text-ink">
                      <CountUp value={totalSaved} format={formatINR} />{" "}
                      <span className="text-ty-link text-ink-meta">of {formatINR(totalTarget)}</span>
                    </p>
                  </div>
                  <p className="text-ty-link tabular-nums text-ink-meta">{overallPct.toFixed(0)}%</p>
                </div>
                <ProgressBar
                  className="mt-sp-xl"
                  value={overallPct}
                  label="Overall progress across active goals"
                  valueText={`${overallPct.toFixed(0)} percent`}
                />
                <p className="mt-sp-sm text-xs text-ink-meta">
                  {active.length} active goal{active.length === 1 ? "" : "s"} · {completed.length} completed
                </p>
              </Surface>
            </Reveal>
          )}

          {active.length > 0 && (
            <section aria-labelledby="goals-active">
              <SectionHead title={<span id="goals-active">Active goals</span>} count={active.length} />
              <ul className="grid gap-sp-wide sm:grid-cols-2 lg:grid-cols-3">
                {active.map((g) => (
                  <GoalCard
                    key={g.id}
                    goal={g}
                    net={forecast.data?.net}
                    onEdit={() => openForm(g)}
                    onDelete={() => handleDelete(g.id)}
                    onContribute={() => setContributingId(g.id)}
                  />
                ))}
              </ul>
            </section>
          )}

          {completed.length > 0 && (
            <ContentBand tone="blue" space="md">
              <SectionHead title="Completed" count={completed.length} />
              <ul className="grid gap-sp-wide sm:grid-cols-2 lg:grid-cols-3">
                {completed.map((g) => (
                  <GoalCard
                    key={g.id}
                    goal={g}
                    onEdit={() => openForm(g)}
                    onDelete={() => handleDelete(g.id)}
                  />
                ))}
              </ul>
            </ContentBand>
          )}
        </>
      )}

      <GoalFormDrawer
        key={`goal-${formKey}`}
        open={showForm}
        goal={editing}
        onClose={() => { setShowForm(false); setEditing(null); }}
        onSaved={async () => { setShowForm(false); setEditing(null); await load(); }}
      />

      <ContributeDrawer
        key={`contrib-${contributingId ?? "none"}`}
        goalId={contributingId}
        onClose={() => setContributingId(null)}
        onSaved={async () => { setContributingId(null); await load(); }}
      />
    </div>
  );
}

const REACH_META: Record<ReachAssessment["reach"], { label: string; cls: string; icon: React.ElementType }> = {
  reachable:   { label: "Within reach",              cls: "text-positive", icon: CircleCheck },
  likely:      { label: "Likely reachable",          cls: "text-primary",   icon: CircleDot },
  unlikely:    { label: "Unlikely at this pace",     cls: "text-caution",  icon: CircleAlert },
  unreachable: { label: "Out of reach at this pace", cls: "text-warning",  icon: CircleAlert },
};

function ReachNote({ goal, net }: { goal: Goal; net: ForecastSeries | undefined }) {
  const a = assessReach(goal, net);
  if (!a || !net) return null;
  const meta = REACH_META[a.reach];
  const evidence = forecastEvidence(net.evidence);
  return (
    <div className="mt-sp-xl border-t border-border pt-sp-xl text-xs">
      <p className={cn("flex flex-wrap items-center gap-sp-xs font-medium", meta.cls)}>
        <meta.icon className="h-3.5 w-3.5" aria-hidden="true" /> {meta.label}
        <EvidenceDrawer
          title={`${goal.name}: can you get there?`}
          value={meta.label}
          evidence={{
            ...evidence,
            method: `Compares what this goal still needs each month with your forecast net savings over the same months. ${evidence.method}`,
            inputs: [
              { label: "Still to save", value: formatINR(goal.remaining) },
              { label: "Months to target", value: String(a.months) },
              { label: "Needed per month", value: formatINR(a.requiredPerMonth) },
              { label: "Forecast net per month", value: `${formatINR(a.expected)} (80% range ${formatINR(a.low80)} to ${formatINR(a.high80)})` },
              { label: "Forecast months used", value: `${a.forecastMonths} of ${a.months}` },
              ...(evidence.inputs ?? []),
            ],
          }}
          trigger={<EvidenceTrigger label={`whether ${goal.name} is reachable`} className="coarse:h-11 coarse:min-w-11" />}
        />
      </p>
      <p className="mt-sp-xs text-ink-meta">
        Needs {formatINR(a.requiredPerMonth)} a month; forecast net is {formatINR(a.expected)} a month
        {a.forecastMonths < a.months ? ` over the next ${a.forecastMonths} months` : ""}.
      </p>
    </div>
  );
}

function GoalCard({
  goal, net, onEdit, onDelete, onContribute,
}: {
  goal: Goal;
  net?: ForecastSeries;
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
  const tone: ProgressTone = "primary";

  return (
    <Surface as="li" tone="canvas" className="min-w-0">
      <div className="flex items-start justify-between gap-sp-xl">
        <div className="min-w-0">
          <div className="flex items-center gap-sp-sm">
            {goal.icon && <span className="text-ty-subhead" aria-hidden="true">{goal.icon}</span>}
            <p className="truncate text-ty-link font-medium text-ink">{goal.name}</p>
          </div>
          <p className="mt-sp-xs text-xs text-ink-meta">Target {targetDate}</p>
        </div>
        <StatusMarker label={paceMeta.label} className={cn("shrink-0", paceMeta.cls)} />
      </div>

      <div className="mt-sp-wide space-y-sp-sm">
        <ProgressBar
          value={pct}
          tone={tone}
          label={`${goal.name} progress`}
          valueText={`${goal.percentComplete.toFixed(0)} percent of ${formatINR(goal.targetAmount)}`}
        />
        <div className="flex items-baseline justify-between">
          <p className="text-sm text-ink">
            <span className="font-medium tabular-nums">{formatINR(goal.currentAmount)}</span>
            <span className="ml-sp-xs text-xs text-ink-meta">of {formatINR(goal.targetAmount)}</span>
          </p>
          <p className="text-xs text-ink-meta tabular-nums">{goal.percentComplete.toFixed(0)}%</p>
        </div>
        {!isCompleted && goal.daysRemaining > 0 && (
          <p className="text-xs text-ink-meta">
            {formatINR(goal.dailyPaceRequired)}/day for {goal.daysRemaining} days
          </p>
        )}
      </div>

      {!isCompleted && <ReachNote goal={goal} net={net} />}

      <div className="mt-sp-wide flex items-center justify-between gap-sp-sm">
        {!isCompleted && onContribute && (
          <Button size="sm" variant="outline" onClick={onContribute} className="flex-1 coarse:h-11">
            <Plus className="h-3 w-3" aria-hidden="true" /> Contribute
          </Button>
        )}
        <div className="flex items-center gap-sp-xs">
          <Button variant="ghost" size="icon-sm" onClick={onEdit} aria-label={`Edit ${goal.name}`} className="coarse:h-11 coarse:w-11">
            <Pencil className="h-4 w-4" aria-hidden="true" />
          </Button>
          <ConfirmButton onConfirm={onDelete} label="Delete goal" className="p-sp-sm coarse:min-h-11 coarse:min-w-11 coarse:justify-center" />
        </div>
      </div>
    </Surface>
  );
}

function GoalFormDrawer({
  open, goal, onClose, onSaved,
}: {
  open: boolean;
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
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent>
        <SheetHeader className="p-sp-giant pb-0">
          <SheetTitle>{goal ? "Edit goal" : "New goal"}</SheetTitle>
          <SheetDescription>A target amount and the date you want to reach it by.</SheetDescription>
        </SheetHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-sp-wide overflow-y-auto p-sp-giant">
            <div role="group" aria-labelledby="goal-icon-label" className="space-y-sp-sm">
              <p id="goal-icon-label" className="text-sm font-medium text-ink">Icon</p>
              <div className="grid grid-cols-6 gap-sp-sm">
                {ICON_CHOICES.map((ic) => (
                  <button
                    key={ic}
                    type="button"
                    aria-pressed={selectedIcon === ic}
                    aria-label={`Icon ${ic}`}
                    onClick={() => setSelectedIcon(ic)}
                    className={cn(
                      "flex h-11 w-11 items-center justify-center rounded-md text-ty-subhead transition-colors duration-fast",
                      selectedIcon === ic ? "bg-band-blue text-ink ring-2 ring-primary" : "bg-chrome hover:bg-band"
                    )}
                  >
                    {ic}
                  </button>
                ))}
              </div>
            </div>

            <Field label="Goal name" error={errors.name?.message}>
              <Input {...register("name")} placeholder="e.g. New laptop" />
            </Field>

            <Field label="Target amount" error={errors.targetAmount?.message}>
              <div className="flex items-center gap-sp-sm">
                <span className="text-sm text-ink-meta">INR</span>
                <Input type="number" step="0.01" min="0.01" {...register("targetAmount")} placeholder="0.00" className="flex-1" />
              </div>
            </Field>

            <Field label="Already saved (optional)">
              <div className="flex items-center gap-sp-sm">
                <span className="text-sm text-ink-meta">INR</span>
                <Input type="number" step="0.01" min="0" {...register("currentAmount")} placeholder="0.00" className="flex-1" />
              </div>
            </Field>

            <Field label="Target date" error={errors.targetDate?.message}>
              <Input type="date" min={tomorrow} {...register("targetDate")} />
            </Field>

            {serverError && <Alert>{serverError}</Alert>}
          </div>

          <SheetFooter className="mt-0 flex-row gap-sp-sm p-sp-giant pt-sp-xl">
            <Button type="submit" disabled={isSubmitting} className="flex-1 coarse:h-11">
              {goal ? "Save changes" : "Create goal"}
            </Button>
            <Button type="button" variant="outline" className="coarse:h-11" onClick={onClose}>Cancel</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}

function ContributeDrawer({
  goalId, onClose, onSaved,
}: {
  goalId: string | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [serverError, setServerError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<ContributionValues>({ resolver: zodResolver(contributionSchema) });

  const onSubmit = async (data: ContributionValues) => {
    if (!goalId) return;
    setServerError(null);
    try {
      await goalsApi.contribute(goalId, { amount: data.amount });
      onSaved();
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : "Failed to record contribution");
    }
  };

  return (
    <Sheet open={goalId !== null} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent>
        <SheetHeader className="p-sp-giant pb-0">
          <SheetTitle>Record contribution</SheetTitle>
          <SheetDescription>How much did you save toward this goal?</SheetDescription>
        </SheetHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-sp-wide overflow-y-auto p-sp-giant">
            <Field label="Amount" error={errors.amount?.message}>
              <div className="flex items-center gap-sp-sm">
                <span className="text-sm text-ink-meta">INR</span>
                <Input type="number" step="0.01" min="0.01" {...register("amount")} placeholder="0.00" className="flex-1" />
              </div>
            </Field>
            {serverError && <Alert>{serverError}</Alert>}
          </div>

          <SheetFooter className="mt-0 flex-row gap-sp-sm p-sp-giant pt-sp-xl">
            <Button type="submit" disabled={isSubmitting} className="flex-1 coarse:h-11">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> Add contribution
            </Button>
            <Button type="button" variant="outline" className="coarse:h-11" onClick={onClose}>Cancel</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  );
}
