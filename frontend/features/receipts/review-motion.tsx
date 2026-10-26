"use client";

import { m, useReducedMotion } from "framer-motion";
import { EASE_STANDARD, DURATION } from "@/components/motion/primitives";

// Fields fill one at a time; the confidence chips settle after the last one; flagged fields pulse once.
const FILL_STEP_S = 0.14;
const FILL_S = 0.36;
const FIELD_COUNT = 5;
const CHIPS_AT_S = FIELD_COUNT * FILL_STEP_S + FILL_S;

const CLOSED = "inset(-20% 100% -20% -2%)";
// negative insets keep the focus ring visible once the wipe has finished
const OPEN = "inset(-20% -2% -20% -2%)";

/** Wipes a field in from the left. `index` is its position in the form; `pulse` flags it once after the chips. */
export function FillIn({ index, pulse, children }: { index: number; pulse?: boolean; children: React.ReactNode }) {
  const reduced = useReducedMotion();
  if (reduced) return <>{children}</>;
  return (
    <div className="relative">
      <m.div
        initial={{ clipPath: CLOSED }}
        animate={{ clipPath: OPEN, transitionEnd: { clipPath: "none" } }}
        transition={{ duration: FILL_S, delay: index * FILL_STEP_S, ease: EASE_STANDARD }}
      >
        {children}
      </m.div>
      {pulse && (
        <m.span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 rounded-md bg-caution-soft"
          initial={{ opacity: 0 }}
          animate={{ opacity: [0, 0.55, 0] }}
          transition={{ duration: 0.9, delay: CHIPS_AT_S + DURATION.base, ease: EASE_STANDARD, times: [0, 0.35, 1] }}
        />
      )}
    </div>
  );
}

/** Confidence chip or note that fades up into place after every field has filled. */
export function ChipSettle({ children }: { children: React.ReactNode }) {
  const reduced = useReducedMotion();
  if (reduced) return <>{children}</>;
  return (
    <m.span
      className="inline-flex"
      initial={{ opacity: 0, scale: 0.94 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: DURATION.base, delay: CHIPS_AT_S, ease: EASE_STANDARD }}
    >
      {children}
    </m.span>
  );
}

/** A tick that draws itself; used where a receipt or transaction has just been saved. */
export function DrawnCheck({ className }: { className?: string }) {
  const reduced = useReducedMotion();
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={2.25} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <m.circle
        cx="12"
        cy="12"
        r="9.5"
        initial={{ pathLength: reduced ? 1 : 0 }}
        animate={{ pathLength: 1 }}
        transition={{ duration: reduced ? DURATION.fast : 0.45, ease: EASE_STANDARD }}
      />
      <m.path
        d="M7.5 12.5l3 3 6-6.5"
        initial={{ pathLength: reduced ? 1 : 0 }}
        animate={{ pathLength: 1 }}
        transition={{ duration: reduced ? DURATION.fast : 0.35, delay: reduced ? 0 : 0.4, ease: EASE_STANDARD }}
      />
    </svg>
  );
}
