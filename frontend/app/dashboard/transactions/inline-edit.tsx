"use client";

import { useRef, useState } from "react";
import { Loader2, Pencil } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * Text that turns into an input on click or Enter. Enter or leaving the field saves,
 * Escape cancels. A failed save keeps the field open with the message beside it.
 */
export function InlineText({
  value,
  label,
  placeholder,
  maxLength,
  onSave,
  children,
  className,
}: {
  value: string;
  // names the edited field for assistive tech, e.g. "Merchant for Zomato"
  label: string;
  placeholder?: string;
  maxLength?: number;
  onSave: (next: string) => Promise<unknown>;
  // how the value is shown when not editing
  children?: React.ReactNode;
  className?: string;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const done = useRef(false);

  const open = () => {
    done.current = false;
    setDraft(value);
    setError(null);
    setEditing(true);
  };

  const commit = async () => {
    if (done.current) return;
    const next = draft.trim();
    if (next === value.trim()) {
      done.current = true;
      setEditing(false);
      return;
    }
    done.current = true;
    setSaving(true);
    try {
      await onSave(next);
      setEditing(false);
    } catch (e) {
      done.current = false;
      setError(e instanceof Error && e.message ? e.message : "Could not save.");
    } finally {
      setSaving(false);
    }
  };

  if (!editing) {
    return (
      <button
        type="button"
        onClick={open}
        aria-label={`Edit ${label}`}
        className={cn(
          "group/edit -mx-1.5 flex max-w-full items-center gap-1.5 rounded px-1.5 py-0.5 text-left hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          className
        )}
      >
        <span className="min-w-0 truncate">{children ?? (value || <span className="text-muted-foreground">{placeholder}</span>)}</span>
        <Pencil className="h-3 w-3 shrink-0 text-muted-foreground opacity-0 group-hover/edit:opacity-100 group-focus-visible/edit:opacity-100" aria-hidden="true" />
      </button>
    );
  }

  return (
    <div className="min-w-0">
      <div className="relative">
        <Input
          autoFocus
          aria-label={label}
          aria-invalid={error ? true : undefined}
          value={draft}
          maxLength={maxLength}
          placeholder={placeholder}
          disabled={saving}
          onChange={(e) => setDraft(e.target.value)}
          onFocus={(e) => e.target.select()}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              void commit();
            } else if (e.key === "Escape") {
              done.current = true;
              setEditing(false);
            }
          }}
          className="h-8 text-sm"
        />
        {saving && <Loader2 className="absolute right-2 top-2 h-4 w-4 animate-spin text-muted-foreground" aria-hidden="true" />}
      </div>
      {error && (
        <p role="alert" className="mt-0.5 truncate text-xs font-medium text-warning">
          {error}
        </p>
      )}
    </div>
  );
}
