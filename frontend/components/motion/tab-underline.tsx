"use client";

import { m, useReducedMotion } from "framer-motion";
import { DURATION_MS, EASE_STANDARD, TAB_SPRING } from "./tokens";

/**
 * The selected-tab underline. Parents measure the active tab (offset and width inside a
 * positioned container) and mount this once they have numbers; later changes spring to the
 * new tab. Moves with transform only, so it never triggers layout.
 */
export function TabUnderline({ x, width, className }: { x: number; width: number; className?: string }) {
  const reduced = useReducedMotion();
  return (
    <m.span
      aria-hidden="true"
      data-tab-underline=""
      className={"pointer-events-none absolute bottom-0 left-0 h-0.5 origin-left bg-primary " + (className ?? "")}
      style={{ width: 1 }}
      initial={false}
      animate={{ x, scaleX: width }}
      transition={reduced ? { duration: DURATION_MS.fast / 1000, ease: EASE_STANDARD } : TAB_SPRING}
    />
  );
}
