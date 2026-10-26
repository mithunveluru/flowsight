"use client";

import { useState } from "react";
import { Check, Loader2, Wand2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAnnounce } from "@/components/ui/announcer";
import { ApiError } from "@/lib/api";
import { CATEGORY_META } from "@/features/transactions/types";
import { formatMoney } from "@/features/transactions/currency";
import { useApplyRule, usePreviewRule } from "./hooks";
import type { MerchantRuleSuggestion } from "./types";

function describeRule(s: MerchantRuleSuggestion): string {
  const parts: string[] = [];
  if (s.canonicalName) parts.push(`call it "${s.canonicalName}"`);
  if (s.category) parts.push(`file it under ${CATEGORY_META[s.category].label}`);
  return parts.join(" and ");
}

const errorText = (e: unknown) =>
  e instanceof ApiError && e.status === 429
    ? "Too many rule changes this hour. Try again later."
    : e instanceof Error && e.message
      ? e.message
      : "That did not work. Try again.";

/**
 * Shown after a correction saved a merchant rule: preview how many other transactions it
 * matches, then apply it to them. Nothing changes until "Apply" is pressed.
 */
export function RuleSuggestionCard({
  suggestion,
  onDismiss,
}: {
  suggestion: MerchantRuleSuggestion;
  onDismiss: () => void;
}) {
  const announce = useAnnounce();
  const preview = usePreviewRule();
  const apply = useApplyRule();
  const [error, setError] = useState<string | null>(null);
  const what = describeRule(suggestion);

  if (apply.data) {
    return (
      <div role="status" className="flex items-start justify-between gap-3 rounded-lg border border-positive/25 bg-positive-soft px-4 py-3 text-sm text-positive">
        <p className="flex items-center gap-2">
          <Check className="h-4 w-4 shrink-0" aria-hidden="true" />
          Updated {apply.data.updated} similar transaction{apply.data.updated === 1 ? "" : "s"}.
        </p>
        <DismissButton onClick={onDismiss} />
      </div>
    );
  }

  const runPreview = async () => {
    setError(null);
    try {
      const p = await preview.mutateAsync(suggestion.ruleId);
      announce(`${p.matchCount} similar transaction${p.matchCount === 1 ? "" : "s"} would change.`);
    } catch (e) {
      setError(errorText(e));
      announce(errorText(e), "assertive");
    }
  };

  const runApply = async () => {
    setError(null);
    try {
      const r = await apply.mutateAsync(suggestion.ruleId);
      announce(`Updated ${r.updated} similar transaction${r.updated === 1 ? "" : "s"}.`);
    } catch (e) {
      setError(errorText(e));
      announce(errorText(e), "assertive");
    }
  };

  const p = preview.data;
  return (
    <section
      aria-label="Apply this correction to similar transactions"
      className="rounded-lg border border-brand/25 bg-brand-soft px-4 py-3 text-sm"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-2">
          <Wand2 className="mt-0.5 h-4 w-4 shrink-0 text-brand" aria-hidden="true" />
          <div className="min-w-0">
            <p className="text-foreground">
              Saved a rule: from now on we will {what || "remember this"}.
            </p>
            {suggestion.similarCount > 0 ? (
              <p className="mt-0.5 text-muted-foreground">
                {suggestion.similarCount} other transaction{suggestion.similarCount === 1 ? "" : "s"} look the same.
              </p>
            ) : (
              <p className="mt-0.5 text-muted-foreground">No other transactions match it yet.</p>
            )}
          </div>
        </div>
        <DismissButton onClick={onDismiss} />
      </div>

      {p && (
        <div className="mt-3 rounded-md border border-border bg-card">
          <p className="border-b border-border px-3 py-2 text-xs font-medium text-muted-foreground">
            {p.matchCount === 0
              ? "Nothing else would change."
              : `${p.matchCount} would change${p.sample.length < p.matchCount ? `, first ${p.sample.length} shown` : ""}`}
          </p>
          {p.sample.length > 0 && (
            <ul className="max-h-48 divide-y divide-border overflow-y-auto">
              {p.sample.map((s) => (
                <li key={s.id} className="flex items-baseline justify-between gap-3 px-3 py-1.5 text-xs">
                  <span className="min-w-0 truncate text-foreground">
                    {s.merchant ?? "Unknown merchant"}
                    <span className="ml-2 text-muted-foreground">
                      {new Date(s.transactionDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}
                      {s.category && ` · ${CATEGORY_META[s.category].label}`}
                    </span>
                  </span>
                  <span className="shrink-0 tabular-nums text-muted-foreground">{formatMoney(s.amount, s.currency)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {error && (
        <p className="mt-2 text-xs font-medium text-warning">{error}</p>
      )}

      {suggestion.similarCount > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {!p ? (
            <Button size="sm" variant="outline" onClick={runPreview} disabled={preview.isPending}>
              {preview.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
              Apply to similar ({suggestion.similarCount})
            </Button>
          ) : p.matchCount > 0 ? (
            <Button size="sm" onClick={runApply} disabled={apply.isPending}>
              {apply.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
              Apply to {p.matchCount}
            </Button>
          ) : null}
          <Button size="sm" variant="ghost" onClick={onDismiss}>
            Not now
          </Button>
        </div>
      )}
    </section>
  );
}

function DismissButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Dismiss"
      className="-mr-1 -mt-1 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
    >
      <X className="h-4 w-4" aria-hidden="true" />
    </button>
  );
}
