"use client";

// Shared motion primitives. Only transform, opacity, clip-path and stroke offset animate;
// under prefers-reduced-motion every primitive becomes a short fade with no travel.

import {
  AnimatePresence,
  LazyMotion,
  domAnimation,
  m,
  useInView,
  useMotionValue,
  useReducedMotion,
  animate as fmAnimate,
  type MotionProps,
  type Variants,
} from "framer-motion";
import { useEffect, useRef, useState } from "react";
import {
  DURATION_MS,
  EASE_LOOP,
  EASE_STANDARD,
  EASE_EXIT,
  countUpValue,
  drawStart,
  drift,
  enterTransition,
} from "./tokens";

export { EASE_LOOP, EASE_STANDARD, EASE_EXIT };
// ease-out alias kept for existing call sites
export const EASE_OUT = EASE_STANDARD;

const s = (ms: number) => ms / 1000;

export const DURATION = {
  fast: s(DURATION_MS.fast),
  base: s(DURATION_MS.base),
  slow: s(DURATION_MS.slow),
  hero: s(DURATION_MS.reveal),
  reveal: s(DURATION_MS.reveal),
  draw: s(DURATION_MS.draw),
} as const;

/** Loads the animation features once for the whole tree; `m` components are inert without it. */
export function MotionRoot({ children }: { children: React.ReactNode }) {
  return <LazyMotion features={domAnimation}>{children}</LazyMotion>;
}

interface FadeInProps {
  children: React.ReactNode;
  delay?: number;
  duration?: number;
  yOffset?: number;
  className?: string;
}

export function FadeIn({ children, delay = 0, duration = DURATION.base, yOffset = 6, className }: FadeInProps) {
  const reduced = useReducedMotion();
  return (
    <m.div
      className={className}
      initial={{ opacity: 0, y: drift(reduced, yOffset) }}
      animate={{ opacity: 1, y: 0 }}
      transition={enterTransition(reduced, duration, delay)}
    >
      {children}
    </m.div>
  );
}

/** Fades and rises into place the first time it scrolls into view. */
export function Reveal({
  children,
  delay = 0,
  className,
  yOffset = 8,
}: {
  children: React.ReactNode;
  delay?: number;
  className?: string;
  yOffset?: number;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const inView = useInView(ref, { once: true, amount: 0.15 });
  const reduced = useReducedMotion();
  const hidden = { opacity: 0, y: drift(reduced, yOffset) };

  return (
    <m.div
      ref={ref}
      className={className}
      initial={hidden}
      animate={inView ? { opacity: 1, y: 0 } : hidden}
      transition={enterTransition(reduced, DURATION.reveal, delay)}
    >
      {children}
    </m.div>
  );
}

export const RevealOnScroll = Reveal;

const STAGGER_STEP_S = 0.06;

export function Stagger({
  children,
  className,
  stagger = STAGGER_STEP_S,
}: {
  children: React.ReactNode;
  className?: string;
  stagger?: number;
}) {
  const reduced = useReducedMotion();
  const variants: Variants = {
    hidden: { opacity: 1 },
    visible: { opacity: 1, transition: { staggerChildren: reduced ? 0 : stagger, delayChildren: reduced ? 0 : 0.04 } },
  };
  return (
    <m.div className={className} initial="hidden" animate="visible" variants={variants}>
      {children}
    </m.div>
  );
}

export const StaggerContainer = Stagger;

export function StaggerItem({ children, className }: { children: React.ReactNode; className?: string }) {
  const reduced = useReducedMotion();
  const variants: Variants = {
    hidden: { opacity: 0, y: drift(reduced, 10) },
    visible: { opacity: 1, y: 0, transition: enterTransition(reduced, DURATION.base) },
  };
  return (
    <m.div className={className} variants={variants}>
      {children}
    </m.div>
  );
}

interface CountUpProps {
  value: number;
  // format the in-flight value for display
  format?: (v: number) => string;
  // seconds; the spec window is 0.9 to 1.2
  duration?: number;
  className?: string;
}

/** Counts from the displayed value to `value`; retargets mid-flight; jumps to the end under reduced motion. */
export function CountUp({
  value,
  format = (v) => v.toLocaleString("en-IN"),
  duration = DURATION.draw,
  className,
}: CountUpProps) {
  const reduced = useReducedMotion();
  const [display, setDisplay] = useState(reduced ? value : 0);
  const motionValue = useMotionValue(reduced ? value : 0);

  useEffect(() => {
    if (reduced) return;
    const controls = fmAnimate(motionValue, value, {
      duration,
      ease: EASE_STANDARD,
      onUpdate: (v) => setDisplay(Math.round(v)),
    });
    return () => controls.stop();
  }, [value, duration, reduced, motionValue]);

  return <span className={className}>{format(countUpValue(reduced, value, display))}</span>;
}

export const AnimatedNumber = CountUp;

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
} & Omit<React.SVGProps<SVGPathElement>, "d" | "ref" | "onDrag" | "onDragStart" | "onDragEnd" | "onAnimationStart">) {
  const reduced = useReducedMotion();
  return (
    <m.path
      d={d}
      className={className}
      stroke={stroke}
      strokeWidth={strokeWidth}
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
      initial={{ pathLength: drawStart(reduced), opacity: reduced ? 0 : 1 }}
      animate={{ pathLength: 1, opacity: 1 }}
      transition={reduced ? enterTransition(true, duration) : { duration, delay, ease: EASE_STANDARD }}
      {...rest}
    />
  );
}

/** Kept for call sites; flat identity draws no ambient gradients. */
export function AmbientGlow(_: { className?: string }) {
  return null;
}

// AnimatedSwitch: fades between content keyed by viewKey.
export function AnimatedSwitch({
  viewKey,
  children,
  className,
}: {
  viewKey: string;
  children: React.ReactNode;
  className?: string;
}) {
  const reduced = useReducedMotion();
  return (
    <AnimatePresence mode="wait" initial={false}>
      <m.div
        key={viewKey}
        className={className}
        initial={{ opacity: 0, y: drift(reduced, 6) }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: drift(reduced, -4), transition: { duration: DURATION.fast, ease: EASE_EXIT } }}
        transition={enterTransition(reduced, DURATION.base)}
      >
        {children}
      </m.div>
    </AnimatePresence>
  );
}

export function PulseDot({ className, color = "hsl(var(--brand))" }: { className?: string; color?: string }) {
  const reduced = useReducedMotion();
  return (
    <span className={"relative inline-flex h-2 w-2 " + (className ?? "")}>
      {!reduced && (
        <m.span
          className="absolute inset-0 rounded-full"
          style={{ backgroundColor: color }}
          animate={{ scale: [1, 2], opacity: [0.55, 0] }}
          transition={{ duration: 1.6, repeat: Infinity, ease: EASE_LOOP }}
        />
      )}
      <span className="relative inline-flex rounded-full h-2 w-2" style={{ backgroundColor: color }} />
    </span>
  );
}

// Collapse: disclosure body that fades in and out. Height is not animated; the content below jumps.
export function Collapse({
  open,
  children,
  id,
  className,
}: {
  open: boolean;
  children: React.ReactNode;
  // pair with aria-controls on the toggle button
  id?: string;
  className?: string;
}) {
  return (
    <AnimatePresence initial={false}>
      {open && (
        <m.div
          key="collapse"
          id={id}
          className={className}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1, transition: { duration: DURATION.base, ease: EASE_STANDARD } }}
          exit={{ opacity: 0, transition: { duration: DURATION.fast, ease: EASE_EXIT } }}
        >
          {children}
        </m.div>
      )}
    </AnimatePresence>
  );
}

export { m as motion, AnimatePresence };
export type { MotionProps };
