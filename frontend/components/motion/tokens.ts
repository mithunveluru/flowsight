// The one definition of motion timing. app/tokens.css mirrors these values for CSS transitions;
// __tests__/tokens.test.mjs fails if the two drift apart.

export const DURATION_MS = {
  fast: 120,
  base: 200,
  slow: 320,
  reveal: 560,
  draw: 1000,
} as const;

// Draws and count-ups may run anywhere in this window.
export const DRAW_RANGE_MS = [900, 1200] as const;

export const EASE_STANDARD = [0.2, 0, 0, 1] as const;
export const EASE_EXIT = [0.4, 0, 1, 1] as const;
// ease-in-out for ambient loops
export const EASE_LOOP = [0.45, 0, 0.55, 1] as const;

// Critically damped: damping = 2 * sqrt(stiffness * mass), so the underline settles without overshoot.
const STIFFNESS = 400;
const MASS = 1;
export const TAB_SPRING = {
  type: "spring",
  stiffness: STIFFNESS,
  damping: 2 * Math.sqrt(STIFFNESS * MASS),
  mass: MASS,
} as const;

const REDUCED_MAX_S = DURATION_MS.fast / 1000;

/** Enter transition in seconds; under reduced motion it is a short fade with no delay. */
export function enterTransition(reduced: boolean | null, durationS: number, delayS = 0) {
  return reduced
    ? { duration: Math.min(durationS, REDUCED_MAX_S), ease: EASE_STANDARD }
    : { duration: durationS, delay: delayS, ease: EASE_STANDARD };
}

/** Distance an element travels while entering; none under reduced motion. */
export function drift(reduced: boolean | null, px: number): number {
  return reduced ? 0 : px;
}

/** What a count-up shows: the target immediately under reduced motion, else the in-flight value. */
export function countUpValue(reduced: boolean | null, target: number, inFlight: number): number {
  return reduced ? target : inFlight;
}

/** Stroke progress a line draw starts from: already complete under reduced motion. */
export function drawStart(reduced: boolean | null): number {
  return reduced ? 1 : 0;
}
