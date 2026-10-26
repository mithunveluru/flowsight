"use client";

import { createElement, useRef } from "react";
import { cn } from "@/lib/utils";
import { DURATION_MS } from "./tokens";
import { useInViewOnce } from "./use-in-view";

// CSS-driven entrances (app/motion.css): no framer-motion, so a server page can use them without the cost.

const s = (ms: number) => ms / 1000;

export const DURATION = {
  fast: s(DURATION_MS.fast),
  base: s(DURATION_MS.base),
  slow: s(DURATION_MS.slow),
  hero: s(DURATION_MS.reveal),
  reveal: s(DURATION_MS.reveal),
  draw: s(DURATION_MS.draw),
} as const;

const fx = (delayS: number, durationS?: number, yPx?: number) =>
  ({
    "--fx-delay": `${Math.round(delayS * 1000)}ms`,
    ...(durationS != null && { "--fx-dur": `${Math.round(durationS * 1000)}ms` }),
    ...(yPx != null && { "--fx-y": `${yPx}px` }),
  }) as React.CSSProperties;

interface FadeInProps {
  children: React.ReactNode;
  delay?: number;
  duration?: number;
  yOffset?: number;
  className?: string;
}

export function FadeIn({ children, delay = 0, duration = DURATION.base, yOffset = 6, className }: FadeInProps) {
  return (
    <div className={cn("fx-rise", className)} style={fx(delay, duration, yOffset)}>
      {children}
    </div>
  );
}

/** Fades and rises into place the first time it scrolls into view; `wipe` clips its child in from the left instead. */
export function Reveal({
  children,
  delay = 0,
  className,
  yOffset = 8,
  as: Tag = "div",
  wipe,
  ...rest
}: {
  children: React.ReactNode;
  delay?: number;
  className?: string;
  yOffset?: number;
  as?: "div" | "li" | "p";
  wipe?: boolean;
} & Omit<React.HTMLAttributes<HTMLElement>, "children" | "className">) {
  const ref = useRef<HTMLElement>(null);
  const seen = useInViewOnce(ref);
  return createElement(
    Tag,
    {
      ...rest,
      ref,
      "data-in": seen ? "" : undefined,
      className: cn(wipe ? "fx-wipe" : "fx-reveal", className),
      style: fx(delay, undefined, yOffset),
    },
    children
  );
}

export const RevealOnScroll = Reveal;

/** Children that are StaggerItems rise one after another, 60ms apart. */
export function Stagger({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("fx-stagger", className)}>{children}</div>;
}

export const StaggerContainer = Stagger;

export function StaggerItem({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("fx-rise", className)} style={fx(0, DURATION.base, 10)}>
      {children}
    </div>
  );
}

/** Draws an SVG path from start to end. Render inside an <svg>; animates stroke offset only. */
export function DrawLine({
  d,
  className,
  stroke = "currentColor",
  strokeWidth = 2,
  duration = DURATION.draw,
  delay = 0,
  ...rest
}: {
  d: string;
  className?: string;
  stroke?: string;
  strokeWidth?: number;
  duration?: number;
  delay?: number;
} & Omit<React.SVGProps<SVGPathElement>, "d" | "ref">) {
  return (
    <path
      d={d}
      pathLength={1}
      className={cn("fx-draw", className)}
      style={fx(delay, duration)}
      stroke={stroke}
      strokeWidth={strokeWidth}
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...rest}
    />
  );
}

