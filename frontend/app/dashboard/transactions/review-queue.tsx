"use client";

import { useState } from "react";
import { Loader2, PartyPopper } from "lucide-react";
import { useAnnounce } from "@/components/ui/announcer";
import { formatProbability } from "@/components/ui/confidence";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { ListItem, ListMotion } from "@/components/motion/list";
import { RuleSuggestionCard } from "@/features/merchants/rule-suggestion";
import { CategoryDot, CategorySignalChip } from "@/features/transactions/category-ui";
import { formatMoney } from "@/features/transactions/currency";
import { useReviewQueue, useUpdateTransaction } from "@/features/transactions/hooks";
import { collectSuggestions, reviewChoices } from "@/features/transactions/table-logic";
import { CATEGORY_META, type MerchantRuleSuggestion, type Transaction, type TransactionCategory } from "@/features/transactions/types";
import { CategoryPicker } from "@/features/transactions/category-ui";

/**
 * "Help categorize": the rows the categorizer is least sure about, least sure first.
 * Each answer is a category correction, so it also teaches the personal layer.
 */
export function ReviewQueue() {
  const announce = useAnnounce();
  const queue = useReviewQueue(20);
  const update = useUpdateTransaction();
  const [answered, setAnswered] = useState<Set<string>>(new Set());
  const [suggestions, setSuggestions] = useState<MerchantRuleSuggestion[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<string | null>(null);

  const answer = async (tx: Transaction, category: TransactionCategory) => {
    setPending(tx.id);
    setErrors(({ [tx.id]: _gone, ...rest }) => rest);
    try {
      const updated = await update.mutateAsync({ id: tx.id, patch: { category } });
      setAnswered((s) => new Set(s).add(tx.id));
      setSuggestions((cur) => collectSuggestions(cur, [updated.ruleSuggestion]));
      announce(`Filed under ${CATEGORY_META[category].label}.`);
    } catch (e) {
      const msg = e instanceof Error && e.message ? e.message : "Could not save that answer.";
      setErrors((cur) => ({ ...cur, [tx.id]: msg }));
      announce(msg, "assertive");
    } finally {
      setPending(null);
    }
  };

  if (queue.isPending) return <LoadingState variant="list" rows={4} label="Loading transactions to check" />;
  if (queue.error) return <ErrorState title="The list did not load" error={queue.error} onRetry={() => queue.refetch()} />;

  const open = queue.data.filter((t) => !answered.has(t.id));
  const dismissSuggestion = (id: string) => setSuggestions((cur) => cur.filter((s) => s.ruleId !== id));

  return (
    <div className="space-y-4">
      <p className="text-supporting max-w-2xl">
        These are the transactions we are least sure about. Tap the right category; each answer helps with your next ones.
      </p>

      {suggestions.map((s) => (
        <RuleSuggestionCard key={s.ruleId} suggestion={s} onDismiss={() => dismissSuggestion(s.ruleId)} />
      ))}

      {open.length === 0 ? (
        <EmptyState
          icon={<PartyPopper />}
          title={answered.size > 0 ? "All done for now" : "Nothing to check"}
          description={
            answered.size > 0
              ? `You answered ${answered.size}. Reload later for more.`
              : "Every categorized transaction is confirmed or confidently filed."
          }
          action={
            answered.size > 0 ? (
              <button type="button" className="text-sm font-medium text-primary hover:underline" onClick={() => { setAnswered(new Set()); void queue.refetch(); }}>
                Check for more
              </button>
            ) : undefined
          }
        />
      ) : (
        <ul className="relative grid gap-3 md:grid-cols-2">
          <ListMotion>
          {open.map((tx) => (
            <ListItem key={tx.id} className="space-y-3 rounded-md bg-chrome p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-medium text-ink">{tx.merchant ?? tx.description}</p>
                  <p className="truncate text-xs text-muted-foreground" title={tx.description}>
                    {new Date(tx.transactionDate).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
                    {tx.merchant && tx.description !== tx.merchant ? ` · ${tx.description}` : ""}
                  </p>
                </div>
                <p className="shrink-0 font-medium tabular-nums">{formatMoney(tx.amount, tx.currency)}</p>
              </div>

              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span>Our guess:</span>
                <span className="font-medium text-foreground">
                  {tx.category && tx.category !== "UNCATEGORIZED" ? CATEGORY_META[tx.category].label : "none"}
                </span>
                <CategorySignalChip tx={tx} />
              </div>

              <div className="flex flex-wrap gap-2" role="group" aria-label={`Choose a category for ${tx.merchant ?? tx.description}`}>
                {reviewChoices(tx).map((c) => (
                  <button
                    key={c.category}
                    type="button"
                    disabled={pending === tx.id}
                    onClick={() => answer(tx, c.category)}
                    className="inline-flex min-h-9 items-center gap-1.5 rounded-md border border-input bg-background px-3 text-xs font-medium hover:bg-band-blue disabled:opacity-50 coarse:min-h-11"
                  >
                    {pending === tx.id ? <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" /> : <CategoryDot category={c.category} />}
                    {CATEGORY_META[c.category].label}
                    {c.probability != null && <span className="tabular-nums text-muted-foreground">{formatProbability(c.probability)}</span>}
                  </button>
                ))}
              </div>

              <div className="max-w-52">
                <CategoryPicker
                  tx={{ category: null, categoryAlternatives: null }}
                  aria-label={`Another category for ${tx.merchant ?? tx.description}`}
                  triggerClassName="h-8 text-xs"
                  disabled={pending === tx.id}
                  onChange={(c) => answer(tx, c)}
                />
              </div>
              {errors[tx.id] && (
                <p role="alert" className="text-xs font-medium text-warning">{errors[tx.id]}</p>
              )}
            </ListItem>
          ))}
          </ListMotion>
        </ul>
      )}
    </div>
  );
}
