"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import { Field } from "@/components/ui/field";
import { useAnnounce } from "@/components/ui/announcer";
import { formatProbability } from "@/components/ui/confidence";
import { RuleSuggestionCard } from "@/features/merchants/rule-suggestion";
import { CategoryDot, CategoryPicker, CategorySignalChip } from "./category-ui";
import { formatMoney } from "./currency";
import { useUpdateTransaction } from "./hooks";
import { otherAlternatives } from "./table-logic";
import { CATEGORY_META, type MerchantRuleSuggestion, type Transaction, type TransactionCategory } from "./types";

// The tick animates after the save, so its motion code stays off the first load of the pages that show this panel.
const DrawnCheck = dynamic(() => import("@/features/receipts/review-motion").then((m) => m.DrawnCheck), {
  ssr: false,
  loading: () => <span className="inline-block h-5 w-5 shrink-0" aria-hidden="true" />,
});

/**
 * Confirmation after a transaction is saved: what it was filed under and how sure we
 * are, with one-tap fixes. A fix is a PATCH, so it is recorded as feedback and can
 * produce a merchant rule to apply to similar rows.
 */
export function SavedTransactionPanel({
  transaction,
  title = "Saved",
  children,
}: {
  transaction: Transaction;
  title?: string;
  children?: React.ReactNode;
}) {
  const announce = useAnnounce();
  const update = useUpdateTransaction();
  const [tx, setTx] = useState(transaction);
  const [suggestion, setSuggestion] = useState<MerchantRuleSuggestion | null>(null);
  const [error, setError] = useState<string | null>(null);

  const setCategory = async (category: TransactionCategory) => {
    if (category === tx.category && tx.categorySource === "USER") return;
    setError(null);
    try {
      const updated = await update.mutateAsync({ id: tx.id, patch: { category } });
      setTx(updated);
      setSuggestion(updated.ruleSuggestion ?? null);
      announce(`Filed under ${CATEGORY_META[category].label}.`);
    } catch (e) {
      const msg = e instanceof Error && e.message ? e.message : "Could not change the category.";
      setError(msg);
      announce(msg, "assertive");
    }
  };

  const alternatives = otherAlternatives(tx);
  const category = tx.category && tx.category !== "UNCATEGORIZED" ? tx.category : null;

  return (
    <div className="space-y-4">
      <div className="rounded-md border border-positive/25 bg-positive-soft p-4">
        <p className="flex items-center gap-2 text-sm font-medium text-positive">
          <DrawnCheck className="h-5 w-5 shrink-0" />
          {title}
        </p>
        <p className="mt-1 text-sm text-foreground">
          <span className="font-medium">{tx.merchant ?? tx.description}</span>
          <span className="mx-1.5 text-muted-foreground">·</span>
          <span className="tabular-nums">{formatMoney(tx.amount, tx.currency)}</span>
          <span className="mx-1.5 text-muted-foreground">·</span>
          {new Date(tx.transactionDate).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
        </p>
      </div>

      <div className="rounded-md bg-chrome p-4">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted-foreground">Filed under</span>
          {category ? (
            <span className="inline-flex items-center gap-1.5 font-medium text-foreground">
              <CategoryDot category={category} />
              {CATEGORY_META[category].label}
            </span>
          ) : (
            <span className="font-medium text-foreground">no category yet</span>
          )}
          <CategorySignalChip tx={tx} />
          {update.isPending && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-label="Saving" />}
        </div>

        {alternatives.length > 0 && (
          <div className="mt-3">
            <p className="text-xs text-muted-foreground">Not right? Other likely picks:</p>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {alternatives.map((a) => (
                <button
                  key={a.category}
                  type="button"
                  disabled={update.isPending}
                  onClick={() => setCategory(a.category)}
                  className="inline-flex min-h-9 items-center gap-1.5 rounded-md border border-input bg-background px-3 text-xs font-medium hover:bg-band-blue disabled:opacity-50 coarse:min-h-11"
                >
                  <CategoryDot category={a.category} />
                  {CATEGORY_META[a.category].label}
                  <span className="tabular-nums text-muted-foreground">{formatProbability(a.probability)}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <Field label="Change category" className="mt-3 max-w-xs" error={error ?? undefined}>
          <CategoryPicker tx={tx} onChange={setCategory} disabled={update.isPending} />
        </Field>
      </div>

      {suggestion && <RuleSuggestionCard suggestion={suggestion} onDismiss={() => setSuggestion(null)} />}

      {children}
    </div>
  );
}
