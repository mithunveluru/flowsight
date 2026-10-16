"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import { useFieldControl } from "./field";

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, ...props }, ref) => {
    const fieldProps = useFieldControl();
    return (
      <input
        type={type}
        className={cn(
          "flex h-9 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm",
          "placeholder:text-muted-foreground",
          // brand focus glow — matches .input-base in globals.css
          "focus-visible:outline-none focus-visible:border-brand",
          "focus-visible:shadow-[0_0_0_3px_hsl(var(--brand)/0.14)]",
          "aria-[invalid=true]:border-warning",
          "disabled:cursor-not-allowed disabled:opacity-50",
          "transition-[border-color,box-shadow] duration-150",
          className
        )}
        ref={ref}
        {...fieldProps}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
