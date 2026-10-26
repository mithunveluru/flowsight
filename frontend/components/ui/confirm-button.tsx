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
        "inline-flex items-center justify-center gap-1.5 rounded-md p-1 coarse:min-h-11 coarse:min-w-11 text-xs font-medium transition-colors duration-fast disabled:cursor-not-allowed disabled:opacity-40",
        armed
          ? "bg-warning-soft px-2 text-warning"
          : "text-muted-foreground hover:text-warning",
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
