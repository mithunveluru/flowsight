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
          "flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground",
          "placeholder:text-muted-foreground",
          "transition-colors duration-fast ease-standard",
          "hover:border-ink focus-visible:border-primary",
          "aria-[invalid=true]:border-warning",
          "disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-input",
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
