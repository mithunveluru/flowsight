"use client";

import { m, useReducedMotion } from "framer-motion";
import { usePathname } from "next/navigation";
import { DURATION } from "./primitives";
import { drift, enterTransition } from "./tokens";

/** Fades and settles the page body on route change. Enter only, so navigation never waits on an exit. */
export function PageTransition({ children, className }: { children: React.ReactNode; className?: string }) {
  const pathname = usePathname();
  const reduced = useReducedMotion();
  return (
    <m.div
      key={pathname}
      className={className}
      initial={{ opacity: 0, y: drift(reduced, 8) }}
      animate={{ opacity: 1, y: 0 }}
      transition={enterTransition(reduced, DURATION.base)}
    >
      {children}
    </m.div>
  );
}
