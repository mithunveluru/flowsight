"use client";

import { AnimatePresence, LazyMotion, m, useReducedMotion } from "framer-motion";
import { DURATION, EASE_EXIT, EASE_STANDARD } from "@/components/motion/primitives";

// Sibling slides use layout animation, which the app-wide domAnimation bundle does not carry.
const loadLayoutFeatures = () => import("./dom-max").then((mod) => mod.default);

/**
 * Wraps the items of a list so inserts and removals animate: the item fades, and its
 * neighbours slide into the freed space with a transform (height itself is never animated).
 */
export function ListMotion({ children }: { children: React.ReactNode }) {
  return (
    <LazyMotion features={loadLayoutFeatures}>
      <AnimatePresence initial={false} mode="popLayout">
        {children}
      </AnimatePresence>
    </LazyMotion>
  );
}

const enter = { duration: DURATION.base, ease: EASE_STANDARD };
const exit = { duration: DURATION.fast, ease: EASE_EXIT };

export function ListItem({ children, className }: { children: React.ReactNode; className?: string }) {
  const reduced = useReducedMotion();
  return (
    <m.li
      layout={reduced ? false : "position"}
      className={className}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1, transition: enter }}
      exit={{ opacity: 0, transition: exit }}
      transition={{ layout: { duration: DURATION.slow, ease: EASE_STANDARD } }}
    >
      {children}
    </m.li>
  );
}

