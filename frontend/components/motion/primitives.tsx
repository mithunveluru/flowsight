"use client";

// Shared motion primitives. Entrances, reveals and draws are CSS (app/motion.css) so they run before
// hydration; framer-motion is kept for what CSS cannot do (presence, height, springs) and loads on demand.
// Only transform, opacity, clip-path and stroke offset animate (height in Grow only); under
// prefers-reduced-motion every primitive becomes a short fade with no travel.

import { AnimatePresence, LazyMotion, m, useReducedMotion, type MotionProps } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { DURATION } from "./css-motion";
import { Grow } from "./grow";
import { EASE_LOOP, EASE_STANDARD, EASE_EXIT, countUpValue, drift, enterTransition } from "./tokens";
import { cubicBezier, tween } from "./tween";


export { EASE_LOOP, EASE_STANDARD, EASE_EXIT };
// ease-out alias kept for existing call sites
export const EASE_OUT = EASE_STANDARD;
export { DURATION, DrawLine, FadeIn, Reveal, RevealOnScroll, Stagger, StaggerContainer, StaggerItem } from "./css-motion";

const loadFeatures = () => import("./dom-features").then((mod) => mod.default);

/** Loads the animation features once, after first paint; `m` components are inert until then. */
export function MotionRoot({ children }: { children: React.ReactNode }) {
  return <LazyMotion features={loadFeatures}>{children}</LazyMotion>;
}

interface CountUpProps {
  value: number;
  // format the in-flight value for display
  format?: (v: number) => string;
  // seconds; the spec window is 0.9 to 1.2
  duration?: number;
  className?: string;
}

const standard = cubicBezier(...EASE_STANDARD);

/** Counts from the displayed value to `value`; retargets mid-flight; jumps to the end under reduced motion. */
export function CountUp({
  value,
  format = (v) => v.toLocaleString("en-IN"),
  duration = DURATION.draw,
  className,
}: CountUpProps) {
  const reduced = useReducedMotion();
  const [display, setDisplay] = useState(reduced ? value : 0);
  const shown = useRef(display);

  useEffect(() => {
    if (reduced) return;
    const from = shown.current;
    return tween(duration, standard, (p) => {
      shown.current = from + (value - from) * p;
      setDisplay(Math.round(shown.current));
    });
  }, [value, duration, reduced]);

  return <span className={className}>{format(countUpValue(reduced, value, display))}</span>;
}

export const AnimatedNumber = CountUp;

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

// Collapse: disclosure body that grows open and closed (height and opacity).
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
        <Grow key="collapse" id={id} className={className}>
          {children}
        </Grow>
      )}
    </AnimatePresence>
  );
}

export { m as motion, AnimatePresence };
export type { MotionProps };
