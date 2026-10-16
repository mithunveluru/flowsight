"use client";

import * as React from "react";
import { cn } from "@/lib/utils";
import { useFieldControl } from "./field";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => {
    const fieldProps = useFieldControl();
    return (
      <textarea
        ref={ref}
        className={cn(
          "flex min-h-20 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm",
          "placeholder:text-muted-foreground",
          "focus-visible:outline-none focus-visible:border-brand focus-visible:shadow-[0_0_0_3px_hsl(var(--brand)/0.14)]",
          "aria-[invalid=true]:border-warning",
          "disabled:cursor-not-allowed disabled:opacity-50",
          "transition-[border-color,box-shadow] duration-fast",
          className
        )}
        {...fieldProps}
        {...props}
      />
    );
  }
);
Textarea.displayName = "Textarea";

export { Textarea };
