"use client";

import { m, useReducedMotion } from "framer-motion";
import { EASE_LOOP } from "@/components/motion/primitives";

/** A thin blue line that sweeps the image it covers while the receipt is being read. */
export function ScanLine() {
  const reduced = useReducedMotion();
  if (reduced) return null;
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      {/* a box as tall as the image whose bottom edge is the line, so translateY alone moves it */}
      <m.div
        className="absolute inset-0 border-b-2 border-primary"
        initial={{ y: "-100%" }}
        animate={{ y: ["-100%", "0%", "-100%"] }}
        transition={{ duration: 2.6, ease: EASE_LOOP, repeat: Infinity }}
      />
    </div>
  );
}
