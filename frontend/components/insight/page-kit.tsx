"use client";

import * as React from "react";
import { AlertTriangle } from "lucide-react";
import { Band } from "@/components/ui/band";
import { cn } from "@/lib/utils";

/** Title row for pages without a hero: h1, a lead line and the page's main actions. */
export function PageTitle({
  title,
  lead,
  actions,
  className,
}: {
  title: React.ReactNode;
  lead?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-end justify-between gap-sp-wide", className)}>
      <div className="min-w-0">
        <h1 className="text-ty-section-title text-ink">{title}</h1>
        {lead && <p className="mt-sp-sm max-w-prose text-ty-link text-ink-lead">{lead}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-sp-xl">{actions}</div>}
    </div>
  );
}

/**
 * Breaks out of the shell's centred column so a band runs edge to edge. The shell column is
 * narrower than the window above 1152px, so the width is the window and the offset is half of
 * the difference; the clip keeps the scrollbar gutter from adding a horizontal scroll.
 */
export function FullBleed({
  children,
  flushTop,
  flushBottom,
  className,
}: {
  children: React.ReactNode;
  flushTop?: boolean;
  flushBottom?: boolean;
  className?: string;
}) {
  return (
    <div
      className={cn(
        flushTop && "-mt-6 sm:-mt-8 lg:-mt-12",
        flushBottom && "-mb-6 sm:-mb-8 lg:-mb-12",
        className
      )}
      style={{ marginLeft: "calc(50% - 50vw)", marginRight: "calc(50% - 50vw)", width: "100vw" }}
    >
      <style>{"#main-content{overflow-x:clip}"}</style>
      {children}
    </div>
  );
}

/** A tinted band across the window with the shell's content column inside; the canvas shows between bands. */
export function ContentBand({
  tone = "blue",
  space = "md",
  children,
  ...props
}: Omit<React.ComponentProps<typeof Band>, "bleed" | "tone"> & { tone?: "blue" | "lavender" | "band" | "chrome" | "deep" }) {
  return (
    <FullBleed>
      <Band tone={tone} space={space} {...props}>
        {children}
      </Band>
    </FullBleed>
  );
}

/** Flat filled tile: depth comes from the fill, not from a rule or shadow. */
export function Surface({
  as: Comp = "div",
  tone = "chrome",
  className,
  ...props
}: React.HTMLAttributes<HTMLElement> & { as?: "div" | "section" | "article" | "li"; tone?: "chrome" | "canvas" | "blue" }) {
  return (
    <Comp
      className={cn(
        "rounded-md p-sp-giant",
        tone === "chrome" && "bg-chrome",
        tone === "canvas" && "bg-canvas",
        tone === "blue" && "bg-band-blue",
        className
      )}
      {...props}
    />
  );
}

export function SectionHead({
  title,
  lead,
  action,
  count,
  as: Heading = "h2",
  className,
}: {
  title: React.ReactNode;
  lead?: React.ReactNode;
  action?: React.ReactNode;
  count?: number;
  as?: "h2" | "h3";
  className?: string;
}) {
  return (
    <div className={cn("mb-sp-giant flex flex-wrap items-end justify-between gap-sp-xl", className)}>
      <div className="min-w-0">
        <Heading className="text-ty-subhead text-ink">
          {title}
          {count != null && <span className="ml-sp-sm text-ty-link font-normal text-ink-meta">{count}</span>}
        </Heading>
        {lead && <p className="mt-sp-xs max-w-prose text-sm text-ink-meta">{lead}</p>}
      </div>
      {action}
    </div>
  );
}

/** Label over a figure. The figure is the only large type on a tile. */
export function Stat({
  label,
  children,
  hint,
  large,
  className,
}: {
  label: React.ReactNode;
  children: React.ReactNode;
  hint?: React.ReactNode;
  large?: boolean;
  className?: string;
}) {
  return (
    <div className={cn("min-w-0", className)}>
      <p className="text-sm text-ink-meta">{label}</p>
      <p className={cn("mt-sp-xs tabular-nums text-ink", large ? "text-ty-section-title" : "text-ty-lead")}>{children}</p>
      {hint && <p className="mt-sp-xs text-xs text-ink-meta">{hint}</p>}
    </div>
  );
}

/** Inline alert: icon plus text, never colour alone. */
export function Alert({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <p
      role="alert"
      className={cn("flex items-start gap-sp-sm rounded-md bg-warning-soft p-sp-xl text-sm text-warning", className)}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}

export const rupeeFull = (v: number) => `₹${Math.round(Number(v)).toLocaleString("en-IN")}`;
