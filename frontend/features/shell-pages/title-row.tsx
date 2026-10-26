import { cn } from "@/lib/utils";

/** Page title row: heading, one-line description and page actions, closed by a soft rule. No hero band. */
export function TitleRow({
  title,
  description,
  actions,
  className,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <header className={cn("flex flex-wrap items-end justify-between gap-x-sp-giant gap-y-sp-wide border-b border-border pb-sp-wide", className)}>
      <div className="min-w-0">
        <h1 className="text-ty-subhead sm:text-ty-section-title">{title}</h1>
        {description && <div className="mt-sp-sm text-ty-caption text-ink-meta">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-sp-sm">{actions}</div>}
    </header>
  );
}
