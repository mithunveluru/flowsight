import { cn } from "@/lib/utils";

/** Page title row: h1, a lead line and the page's main actions, closed by a soft rule. No hero band. */
export function TitleRow({
  title,
  description,
  actions,
  rule = true,
  className,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  // the soft rule under the row; off when a band follows directly
  rule?: boolean;
  className?: string;
}) {
  return (
    <header
      className={cn(
        "flex flex-wrap items-end justify-between gap-x-sp-giant gap-y-sp-wide",
        rule && "border-b border-border pb-sp-wide",
        className
      )}
    >
      <div className="min-w-0">
        <h1 className="text-ty-subhead sm:text-ty-section-title">{title}</h1>
        {description && <div className="mt-sp-sm max-w-prose text-ty-caption text-ink-lead sm:text-ty-link">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-sp-sm">{actions}</div>}
    </header>
  );
}
