import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const DARK_TONE_HEADINGS = "[&_:is(h1,h2,h3,h4,h5,h6)]:text-on-primary";

const bandVariants = cva("w-full rounded-none", {
  variants: {
    tone: {
      canvas: "bg-canvas text-ink-body",
      chrome: "bg-chrome text-ink-body",
      band: "bg-band text-ink-body",
      blue: "bg-band-blue text-ink-body",
      lavender: "bg-band-lavender text-ink-body",
      sky: "bg-sky text-navy",
      deep: `bg-primary-deep text-on-primary ${DARK_TONE_HEADINGS}`,
      navy: `hero-navy ${DARK_TONE_HEADINGS}`,
    },
    // curved top edge: the one place a band softens; only for the lavender kind in the spec
    curved: { true: "band-curve-top", false: "" },
    space: {
      none: "",
      sm: "py-sp-giant",
      md: "py-sp-hero",
      lg: "py-sp-band",
      xl: "py-sp-page",
    },
  },
  defaultVariants: { tone: "canvas", curved: false, space: "lg" },
});

export interface BandProps
  extends Omit<React.HTMLAttributes<HTMLElement>, "color">,
    VariantProps<typeof bandVariants> {
  as?: "section" | "div" | "aside" | "footer" | "header";
  // class for the centred content column
  containerClassName?: string;
  // run children edge to edge instead of inside the content column
  bleed?: boolean;
}

/** Full-width flat zone. Bands run square and edge to edge; the content column is centred inside. */
export const Band = React.forwardRef<HTMLElement, BandProps>(
  ({ as: Comp = "section", tone, curved, space, className, containerClassName, bleed, children, ...props }, ref) => (
    <Comp ref={ref as React.Ref<never>} className={cn(bandVariants({ tone, curved, space }), className)} {...props}>
      {bleed ? children : <div className={cn("mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-12", containerClassName)}>{children}</div>}
    </Comp>
  )
);
Band.displayName = "Band";

/** Interior hero: solid deep-blue band (or the navy gradient) with title, lead and actions. */
export function HeroBand({
  title,
  lead,
  actions,
  media,
  tone = "deep",
  as: Heading = "h1",
  className,
}: {
  title: React.ReactNode;
  lead?: React.ReactNode;
  actions?: React.ReactNode;
  // right-hand image or illustration; stacks below the text on narrow screens
  media?: React.ReactNode;
  tone?: "deep" | "navy";
  as?: "h1" | "h2";
  className?: string;
}) {
  return (
    <Band tone={tone} space="xl" className={cn("flex min-h-hero items-center", className)} containerClassName="w-full">
      <div className={cn("grid items-center gap-sp-hero", media && "lg:grid-cols-2")}>
        <div>
          <Heading className="text-ty-hero-title text-on-primary">{title}</Heading>
          {lead && <p className="mt-sp-wide max-w-prose text-ty-body">{lead}</p>}
          {actions && <div className="mt-sp-giant flex flex-wrap items-center gap-sp-xl">{actions}</div>}
        </div>
        {media}
      </div>
    </Band>
  );
}
