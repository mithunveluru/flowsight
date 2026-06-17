"use client";

// Shared motion primitives. One ease curve (EASE_OUT) and one duration
// scale (DURATION); every primitive gates on prefers-reduced-motion so
// the rest of the app does not need to think about it.

import {
  AnimatePresence,
  motion,
  useInView,
  useMotionValue,
  useReducedMotion,
  useTransform,
  animate as fmAnimate,
  type MotionProps,
  type Transition,
  type Variants,
} from "framer-motion";
import { useEffect, useRef, useState } from "react";

/** Refined ease-out-quint — looks like macOS spring without being bouncy. */
export const EASE_OUT = [0.22, 1, 0.36, 1] as const;
/** Ease-in-out for ambient looped motion (hero backgrounds). */
export const EASE_LOOP = [0.45, 0, 0.55, 1] as const;

export const DURATION = {
  fast:   0.18,
  base:   0.28,
  slow:   0.48,
  hero:   0.7,
} as const;

const baseEnter: Transition = { duration: DURATION.base, ease: EASE_OUT };

interface FadeInProps {
  children: React.ReactNode;
  delay?: number;
  duration?: number;
  yOffset?: number;
  className?: string;
}

export function FadeIn({
  children, delay = 0, duration = DURATION.base, yOffset = 6, className,
}: FadeInProps) {
  const reduced = useReducedMotion();
  if (reduced) return <div className={className}>{children}</div>;

  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: yOffset }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration, delay, ease: EASE_OUT }}
    >
      {children}
    </motion.div>
  );
}

const staggerContainerVariants: Variants = {
  hidden: { opacity: 1 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.04 },
  },
};

const staggerItemVariants: Variants = {
  hidden: { opacity: 0, y: 10 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: DURATION.base, ease: EASE_OUT },
  },
};

export function StaggerContainer({
  children, className, stagger = 0.06,
}: {
  children: React.ReactNode;
  className?: string;
  stagger?: number;
}) {
  const reduced = useReducedMotion();
  if (reduced) return <div className={className}>{children}</div>;

  return (
    <motion.div
      className={className}
      initial="hidden"
      animate="visible"
      variants={{
        hidden:  { opacity: 1 },
        visible: { opacity: 1, transition: { staggerChildren: stagger, delayChildren: 0.04 } },
      }}
    >
      {children}
    </motion.div>
  );
}

export function StaggerItem({
  children, className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const reduced = useReducedMotion();
  if (reduced) return <div className={className}>{children}</div>;

  return (
    <motion.div className={className} variants={staggerItemVariants}>
      {children}
    </motion.div>
  );
}

export function RevealOnScroll({
  children, delay = 0, className, yOffset = 14,
}: {
  children: React.ReactNode;
  delay?: number;
  className?: string;
  yOffset?: number;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const inView = useInView(ref, { once: true, amount: 0.15 });
  const reduced = useReducedMotion();

  if (reduced) return <div className={className}>{children}</div>;

  return (
    <motion.div
      ref={ref}
      className={className}
      initial={{ opacity: 0, y: yOffset }}
      animate={inView ? { opacity: 1, y: 0 } : { opacity: 0, y: yOffset }}
      transition={{ duration: DURATION.slow, delay, ease: EASE_OUT }}
    >
      {children}
    </motion.div>
  );
}

interface AnimatedNumberProps {
  value: number;
  /** Format the in-flight integer value into the displayed string. */
  format?: (v: number) => string;
  duration?: number;
  className?: string;
}

export function AnimatedNumber({
  value,
  format = (v) => v.toLocaleString("en-IN"),
  duration = 0.8,
  className,
}: AnimatedNumberProps) {
  const reduced = useReducedMotion();
  const [display, setDisplay] = useState(reduced ? value : 0);
  const motionValue = useMotionValue(reduced ? value : 0);
  const lastTarget = useRef(value);

  useEffect(() => {
    if (reduced) {
      setDisplay(value);
      return;
    }
    const controls = fmAnimate(motionValue, value, {
      duration,
      ease: EASE_OUT,
      onUpdate: (v) => setDisplay(Math.round(v)),
    });
    lastTarget.current = value;
    return () => controls.stop();
  }, [value, duration, reduced, motionValue]);

  return <span className={className}>{format(display)}</span>;
}

// -------------------------------------------------------------------------
// AmbientGlow — extremely subtle floating gradient for hero backgrounds.
//   Used sparingly — only on dashboard hero / settings hero / reports hero.
//   Two layers drift independently at very slow speeds (~22s and 28s).
// -------------------------------------------------------------------------

export function AmbientGlow({ className }: { className?: string }) {
  const reduced = useReducedMotion();

  return (
    <div className={"pointer-events-none absolute inset-0 overflow-hidden " + (className ?? "")}>
      <motion.div
        className="absolute -top-1/3 -left-1/4 h-[140%] w-[60%] rounded-full"
        style={{
          background:
            "radial-gradient(50% 50% at 50% 50%, hsl(var(--brand) / 0.08), transparent 70%)",
          filter: "blur(40px)",
        }}
        animate={reduced ? undefined : { x: [0, 24, 0], y: [0, -18, 0] }}
        transition={{ duration: 22, repeat: Infinity, ease: EASE_LOOP }}
      />
      <motion.div
        className="absolute -bottom-1/3 -right-1/4 h-[140%] w-[60%] rounded-full"
        style={{
          background:
            "radial-gradient(50% 50% at 50% 50%, hsl(var(--positive) / 0.05), transparent 70%)",
          filter: "blur(40px)",
        }}
        animate={reduced ? undefined : { x: [0, -20, 0], y: [0, 14, 0] }}
        transition={{ duration: 28, repeat: Infinity, ease: EASE_LOOP, delay: 4 }}
      />
    </div>
  );
}

// -------------------------------------------------------------------------
// AnimatedSwitch — fades+slides between content keyed by `viewKey`.
//   Useful when one panel's content fully replaces another (e.g. simulator
//   scenario type tab change, receipt review state change).
// -------------------------------------------------------------------------

export function AnimatedSwitch({
  viewKey, children, className,
}: {
  viewKey: string;
  children: React.ReactNode;
  className?: string;
}) {
  const reduced = useReducedMotion();
  if (reduced) return <div className={className}>{children}</div>;

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={viewKey}
        className={className}
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -4 }}
        transition={{ duration: DURATION.fast, ease: EASE_OUT }}
      >
        {children}
      </motion.div>
    </AnimatePresence>
  );
}

export function PulseDot({
  className,
  color = "hsl(var(--brand))",
}: { className?: string; color?: string }) {
  const reduced = useReducedMotion();
  return (
    <span className={"relative inline-flex h-2 w-2 " + (className ?? "")}>
      {!reduced && (
        <motion.span
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

export { motion, AnimatePresence };
export type { MotionProps };
