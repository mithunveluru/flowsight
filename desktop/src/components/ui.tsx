import * as React from "react";
import { Loader2 } from "lucide-react";
import { cn } from "~/lib/utils";

// Small local token-styled primitives; keeps the desktop bundle lean vs the web app's Radix ui/*.

type ButtonVariant = "primary" | "secondary" | "ghost";

export function Button({
  variant = "primary",
  loading,
  className,
  children,
  disabled,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  loading?: boolean;
}) {
  const base =
    "no-drag inline-flex items-center justify-center gap-1.5 rounded-lg text-sm font-medium transition-all duration-150 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none";
  const variants: Record<ButtonVariant, string> = {
    primary: "bg-primary text-primary-foreground hover:opacity-90 px-4 py-2",
    secondary: "bg-muted text-foreground hover:bg-muted/70 px-4 py-2",
    ghost: "text-muted-foreground hover:bg-muted hover:text-foreground px-3 py-1.5",
  };
  return (
    <button
      className={cn(base, variants[variant], className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
      {children}
    </button>
  );
}

export function TextField({
  label,
  hint,
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label?: string; hint?: string }) {
  return (
    <label className="block space-y-1.5">
      {label && (
        <span className="text-[11px] font-medium text-muted-foreground">{label}</span>
      )}
      <input className={cn("input-base no-drag", className)} {...props} />
      {hint && <span className="text-[10px] text-muted-foreground">{hint}</span>}
    </label>
  );
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("h-4 w-4 animate-spin text-muted-foreground", className)} />;
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-md bg-muted", className)} />;
}

export function EmptyState({
  icon,
  title,
  hint,
  action,
}: {
  icon?: React.ReactNode;
  title: string;
  hint?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-10 text-center">
      {icon && <span className="text-muted-foreground/60">{icon}</span>}
      <p className="text-sm font-medium text-foreground">{title}</p>
      {hint && <p className="max-w-[260px] text-xs text-muted-foreground">{hint}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
}
