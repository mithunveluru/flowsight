"use client";

import { m, useReducedMotion } from "framer-motion";
import { useState } from "react";
import { DURATION_MS, EASE_EXIT, EASE_STANDARD } from "./tokens";

const s = (ms: number) => ms / 1000;

/**
 * Enter and exit for disclosures and list rows: height and opacity together, so the content below
 * follows instead of jumping. Height is the one layout property animated, and only here. Clipping
 * applies while it runs so a settled row never cuts off a focus ring. Reduced motion: opacity only.
 */
export function Grow({
  as = "div",
  children,
  className,
  id,
}: {
  as?: "div" | "li";
  children: React.ReactNode;
  className?: string;
  id?: string;
}) {
  const reduced = useReducedMotion();
  const [clip, setClip] = useState(false);
  const Comp = m[as];
  const collapsed = reduced ? { opacity: 0 } : { opacity: 0, height: 0 };
  return (
    <Comp
      id={id}
      className={className}
      style={{ overflow: clip ? "hidden" : undefined }}
      initial={collapsed}
      animate={{
        opacity: 1,
        ...(reduced ? {} : { height: "auto" }),
        transition: { duration: s(DURATION_MS.base), ease: EASE_STANDARD },
      }}
      exit={{ ...collapsed, transition: { duration: s(DURATION_MS.fast), ease: EASE_EXIT } }}
      onAnimationStart={() => setClip(true)}
      onAnimationComplete={() => setClip(false)}
    >
      {children}
    </Comp>
  );
}
