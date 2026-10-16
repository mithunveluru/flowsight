import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const cardVariants = cva("rounded-xl bg-card text-card-foreground", {
  variants: {
    variant: {
      default: "card-refined",
      interactive: "card-tactile",
      muted: "bg-muted/50",
      outline: "border border-border",
    },
    padding: {
      none: "",
      sm: "p-4",
      md: "p-5",
      lg: "p-6",
      xl: "p-8",
    },
  },
  defaultVariants: { variant: "default", padding: "lg" },
});

type CardElement = "div" | "section" | "article" | "aside" | "figure" | "li";

export interface CardProps
  extends React.HTMLAttributes<HTMLElement>,
    VariantProps<typeof cardVariants> {
  as?: CardElement;
}

const Card = React.forwardRef<HTMLElement, CardProps>(
  ({ as: Comp = "div", variant, padding, className, ...props }, ref) => (
    <Comp
      ref={ref as React.Ref<never>}
      className={cn(cardVariants({ variant, padding }), className)}
      {...props}
    />
  )
);
Card.displayName = "Card";

type HeadingLevel = "h2" | "h3" | "h4";

interface CardHeaderProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  title: React.ReactNode;
  description?: React.ReactNode;
  icon?: React.ReactNode;
  // right-aligned slot for a button, link or figure
  action?: React.ReactNode;
  as?: HeadingLevel;
  titleId?: string;
}

function CardHeader({
  title,
  description,
  icon,
  action,
  as: Heading = "h3",
  titleId,
  className,
  ...props
}: CardHeaderProps) {
  return (
    <div className={cn("flex items-start justify-between gap-4", className)} {...props}>
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span
            aria-hidden="true"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground [&_svg]:h-4 [&_svg]:w-4"
          >
            {icon}
          </span>
        )}
        <div className="min-w-0">
          <Heading id={titleId} className="text-title leading-snug">
            {title}
          </Heading>
          {description && <p className="mt-0.5 text-caption">{description}</p>}
        </div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

/** A block inside a card; `divided` draws a hairline above it. */
function CardSection({
  divided,
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { divided?: boolean }) {
  return <div className={cn("mt-5", divided && "border-t border-border pt-5", className)} {...props} />;
}

function CardFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("mt-5 flex items-center justify-end gap-2", className)} {...props} />;
}

export { Card, CardHeader, CardSection, CardFooter, cardVariants };
