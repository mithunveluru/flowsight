"use client";

import { useEffect, useState } from "react";
import { Loader2, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";

// two-step destructive action, replaces blocking window.confirm
export function ConfirmButton({
  onConfirm,
  busy,
  label = "Delete",
  confirmLabel = "Confirm",
  className,
}: {
  onConfirm: () => void;
  busy?: boolean;
  label?: string;
  confirmLabel?: string;
  className?: string;
}) {
  const [armed, setArmed] = useState(false);

  // disarm if the user walks away
  useEffect(() => {
    if (!armed) return;
    const t = setTimeout(() => setArmed(false), 3000);
    return () => clearTimeout(t);
  }, [armed]);

  return (
    <button
      type="button"
      onClick={() => (armed ? onConfirm() : setArmed(true))}
      onBlur={() => setArmed(false)}
      disabled={busy}
      aria-label={armed ? confirmLabel : label}
      title={armed ? "Click again to confirm" : label}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md p-1 text-xs font-medium transition-all disabled:opacity-50",
        armed
          ? "bg-warning-soft px-2 text-warning"
          : "text-muted-foreground/50 hover:text-warning",
        className
      )}
    >
      {busy ? (
        <Loader2 className="h-4 w-4 animate-spin" />
      ) : (
        <Trash2 className="h-4 w-4" />
      )}
      {armed && !busy && confirmLabel}
    </button>
  );
}
