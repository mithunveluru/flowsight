"use client";

import { m, useReducedMotion } from "framer-motion";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
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

export interface UnderlineGeometry {
  x: number;
  width: number;
}

/**
 * Measures the element marked data-active="true" inside the returned ref's container, which must
 * be positioned. Re-measures on active change and when either box resizes (fonts, breakpoints).
 */
export function useTabUnderline<T extends HTMLElement>(active: string | null) {
  const ref = useRef<T>(null);
  const [geometry, setGeometry] = useState<UnderlineGeometry | null>(null);

  const measure = useCallback(() => {
    const el = ref.current?.querySelector<HTMLElement>('[data-active="true"]');
    if (!el) return setGeometry(null);
    setGeometry((g) => (g && g.x === el.offsetLeft && g.width === el.offsetWidth ? g : { x: el.offsetLeft, width: el.offsetWidth }));
  }, []);

  useLayoutEffect(measure, [active, measure]);

  useEffect(() => {
    const root = ref.current;
    if (!root || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(root);
    const el = root.querySelector<HTMLElement>('[data-active="true"]');
    if (el) observer.observe(el);
    return () => observer.disconnect();
  }, [active, measure]);

  return { ref, geometry };
}
