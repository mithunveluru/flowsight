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
          "flex min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground",
          "placeholder:text-muted-foreground",
          "transition-colors duration-fast ease-standard",
          "hover:border-ink focus-visible:border-primary",
          "aria-[invalid=true]:border-warning",
          "disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-input",
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
