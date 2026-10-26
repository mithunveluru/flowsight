"use client";

import { AnimatePresence } from "framer-motion";
import { Grow } from "./grow";

/** Wraps the items of a list so inserts and removals animate (see Grow). Existing items do not animate on mount. */
export function ListMotion({ children }: { children: React.ReactNode }) {
  return <AnimatePresence initial={false}>{children}</AnimatePresence>;
}

export function ListItem({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <Grow as="li" className={className}>
      {children}
    </Grow>
  );
}
