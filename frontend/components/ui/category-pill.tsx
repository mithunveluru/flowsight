import { CATEGORY_META, type TransactionCategory } from "@/features/transactions/types";
import { cn } from "@/lib/utils";

// Quiet category pill: neutral surface, hairline border, category hue carried
// by a small dot from the curated chart palette. Keeps dense tables calm.
export function CategoryPill({
  category,
  className,
}: {
  category: TransactionCategory | null | undefined;
  className?: string;
}) {
  const meta = category ? CATEGORY_META[category] : null;
  if (!meta) {
    return <span className="text-xs text-muted-foreground/60">—</span>;
  }
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-border/80 bg-card py-0.5 pl-2 pr-2.5 text-xs font-medium text-foreground/75",
        className
      )}
    >
      <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", meta.dot)} />
      {meta.label}
    </span>
  );
}
