"use client";

import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cn } from "@/lib/utils";
import { Label } from "./label";

interface FieldContextValue {
  id: string;
  describedBy?: string;
  invalid: boolean;
  required?: boolean;
}

const FieldContext = React.createContext<FieldContextValue | null>(null);

export interface FieldControlProps {
  id?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: true;
  "aria-required"?: true;
}

/**
 * Props a control needs to join the surrounding <Field>. Input, Textarea and
 * SelectTrigger call this themselves; custom controls can use it or <FieldControl>.
 * Returns {} outside a Field.
 */
export function useFieldControl(): FieldControlProps {
  const field = React.useContext(FieldContext);
  if (!field) return {};
  return {
    id: field.id,
    "aria-describedby": field.describedBy,
    "aria-invalid": field.invalid ? true : undefined,
    "aria-required": field.required ? true : undefined,
  };
}

/** Wires an arbitrary single child element (native select, custom widget) into the Field. */
export const FieldControl = React.forwardRef<HTMLElement, React.HTMLAttributes<HTMLElement>>(
  (props, ref) => {
    const fieldProps = useFieldControl();
    return <Slot ref={ref} {...fieldProps} {...props} />;
  }
);
FieldControl.displayName = "FieldControl";

/**
 * Label + control + hint + error with ids wired for assistive tech: the label
 * targets the control, hint and error are linked through aria-describedby, and
 * an error sets aria-invalid. Example:
 *   <Field label="Amount" hint="In rupees" error={errors.amount?.message}><Input /></Field>
 */
export function Field({
  label,
  hint,
  error,
  required,
  id,
  hideLabel,
  labelAction,
  className,
  children,
}: {
  label: React.ReactNode;
  hint?: React.ReactNode;
  error?: React.ReactNode;
  required?: boolean;
  id?: string;
  // keeps the label for screen readers only
  hideLabel?: boolean;
  // right-aligned slot on the label row, e.g. "Forgot password?"
  labelAction?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}) {
  const autoId = React.useId();
  const controlId = id ?? `field-${autoId}`;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(" ") || undefined;

  const value = React.useMemo(
    () => ({ id: controlId, describedBy, invalid: !!error, required }),
    [controlId, describedBy, error, required]
  );

  return (
    <div className={cn("space-y-1.5", className)}>
      <div className={cn("flex items-baseline justify-between gap-2", hideLabel && "sr-only")}>
        <Label htmlFor={controlId}>
          {label}
          {required && (
            <span aria-hidden="true" className="ml-0.5 text-warning">
              *
            </span>
          )}
        </Label>
        {labelAction}
      </div>
      <FieldContext.Provider value={value}>{children}</FieldContext.Provider>
      {error && (
        <p id={errorId} className="text-xs font-medium text-warning">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="text-caption">
          {hint}
        </p>
      )}
    </div>
  );
}
