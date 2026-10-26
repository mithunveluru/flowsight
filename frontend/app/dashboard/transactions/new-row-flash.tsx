"use client";

import { m, useReducedMotion } from "framer-motion";
import { EASE_STANDARD } from "@/components/motion/primitives";

/** One-time wash of band-blue behind a freshly saved row; sits under the row's text. */
export function NewRowFlash() {
  const reduced = useReducedMotion();
  if (reduced) return null;
  return (
    <m.span
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 -z-10 bg-band-blue"
      initial={{ opacity: 1 }}
      animate={{ opacity: 0 }}
      transition={{ duration: 1.8, delay: 0.4, ease: EASE_STANDARD }}
    />
  );
}
