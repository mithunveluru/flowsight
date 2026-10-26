import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

// Hover rules skip disabled buttons; anchors rendered through asChild are never :disabled, so they still hover.
const buttonVariants = cva(
  [
    "press inline-flex items-center justify-center gap-2 rounded-md font-medium",
    "disabled:cursor-not-allowed disabled:opacity-40 aria-disabled:cursor-not-allowed aria-disabled:opacity-40",
    "[&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  ],
  {
    variants: {
      variant: {
        default:
          "bg-primary text-primary-foreground [&:not(:disabled)]:hover:bg-primary-hover [&:not(:disabled)]:active:bg-primary-pressed",
        secondary:
          "bg-secondary text-secondary-foreground [&:not(:disabled)]:hover:bg-muted [&:not(:disabled)]:active:bg-secondary",
        outline:
          "border border-input bg-background text-primary-outline [&:not(:disabled)]:hover:bg-band-blue",
        ghost: "text-foreground [&:not(:disabled)]:hover:bg-accent",
        destructive:
          "bg-destructive text-destructive-foreground [&:not(:disabled)]:hover:bg-destructive/90",
        link: "text-primary underline-offset-4 [&:not(:disabled)]:hover:underline",
      },
      size: {
        default: "h-11 px-3.5 text-ty-button",
        sm: "h-8 px-3 text-sm coarse:h-11",
        lg: "h-14 px-6 text-ty-button",
        floating: "h-14 px-5 text-ty-caption",
        icon: "h-11 w-11",
        "icon-sm": "h-8 w-8 coarse:h-11 coarse:w-11",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
