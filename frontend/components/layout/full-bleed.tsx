import { cn } from "@/lib/utils";

/**
 * Runs a band edge to edge inside the page column. The shell makes its scroll area a size container,
 * so 100cqw is the visible width without the scrollbar or the assistant panel (see .full-bleed).
 * `flushTop` and `flushBottom` cancel the column's vertical padding so a band can meet the tab rail or the page end.
 */
export function FullBleed({
  children,
  flushTop,
  flushBottom,
  className,
}: {
  children: React.ReactNode;
  flushTop?: boolean;
  flushBottom?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("full-bleed", flushTop && "full-bleed-top", flushBottom && "full-bleed-bottom", className)}>
      {children}
    </div>
  );
}
