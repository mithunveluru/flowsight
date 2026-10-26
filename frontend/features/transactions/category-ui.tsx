"use client";

import { Sparkles } from "lucide-react";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import { formatProbability } from "@/components/ui/confidence";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { CATEGORY_META, CATEGORY_OPTIONS, type Transaction, type TransactionCategory } from "./types";
import { CATEGORY_THRESHOLDS, categorySignal, otherAlternatives } from "./table-logic";

const SUGGESTED_LABELS = { high: "Suggested", medium: "Suggested", low: "Unsure" };

/** Where a category came from: nothing for the user's own choice, "Auto" for rules, a calibrated chip for the model. */
export function CategorySignalChip({
  tx,
  className,
}: {
  tx: Pick<Transaction, "category" | "categorySource" | "confidenceScore" | "reviewed">;
  className?: string;
}) {
  const signal = categorySignal(tx);
  if (signal.kind === "user" || signal.kind === "none") return null;
  if (signal.kind === "rules") {
    return (
      <span
        className={cn(
          "inline-flex items-center whitespace-nowrap rounded-full border border-border px-2 py-0.5 text-xs font-medium text-muted-foreground",
          className
        )}
      >
        <span className="sr-only">Category set by keyword rules: </span>
        Auto
      </span>
    );
  }
  return (
    <span className={cn("inline-flex items-center gap-1", className)}>
      <ConfidenceChip
        value={signal.confidence}
        thresholds={CATEGORY_THRESHOLDS}
        labels={SUGGESTED_LABELS}
        showValue
      />
      {signal.kind === "personal" && (
        <span className="whitespace-nowrap text-xs text-muted-foreground">learned from your edits</span>
      )}
    </span>
  );
}

export function CategoryDot({ category }: { category: TransactionCategory }) {
  return <span aria-hidden="true" className={cn("h-2 w-2 shrink-0 rounded-full", CATEGORY_META[category].dot)} />;
}

/**
 * Category select whose list starts with the model's other picks (with their
 * probability), then every category. Any pick goes through the caller's PATCH, so it is
 * recorded as a correction.
 */
export function CategoryPicker({
  tx,
  onChange,
  disabled,
  triggerClassName,
  "aria-label": ariaLabel,
}: {
  tx: Pick<Transaction, "category" | "categoryAlternatives">;
  onChange: (category: TransactionCategory) => void;
  disabled?: boolean;
  triggerClassName?: string;
  "aria-label"?: string;
}) {
  const alternatives = otherAlternatives(tx);
  const current = tx.category && tx.category !== "UNCATEGORIZED" ? tx.category : undefined;
  return (
    <Select value={current} onValueChange={(v) => onChange(v as TransactionCategory)} disabled={disabled}>
      <SelectTrigger aria-label={ariaLabel} className={triggerClassName}>
        <SelectValue placeholder="Choose a category" />
      </SelectTrigger>
      <SelectContent>
        {alternatives.length > 0 && (
          <>
            <SelectGroup>
              <SelectLabel className="flex items-center gap-1">
                <Sparkles className="h-3 w-3" aria-hidden="true" /> Other likely picks
              </SelectLabel>
              {alternatives.map((a) => (
                <SelectItem key={`alt-${a.category}`} value={a.category}>
                  <span className="flex items-center gap-2">
                    <CategoryDot category={a.category} />
                    {CATEGORY_META[a.category].label}
                    <span className="tabular-nums text-muted-foreground">{formatProbability(a.probability)}</span>
                  </span>
                </SelectItem>
              ))}
            </SelectGroup>
            <SelectSeparator />
          </>
        )}
        <SelectGroup>
          {alternatives.length > 0 && <SelectLabel>All categories</SelectLabel>}
          {CATEGORY_OPTIONS.filter((o) => !alternatives.some((a) => a.category === o.value)).map((o) => (
            <SelectItem key={o.value} value={o.value}>
              <span className="flex items-center gap-2">
                <CategoryDot category={o.value} />
                {o.label}
              </span>
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  );
}
