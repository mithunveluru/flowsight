"use client";

import { useEffect, useState, type RefObject } from "react";

/**
 * True once the element has been on screen. The bottom margin keeps it from firing for an element
 * that only just touches the edge; a ratio threshold would never fire for blocks taller than the window.
 */
export function useInViewOnce(ref: RefObject<Element | null>, margin = "-8%") {
  const [seen, setSeen] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || seen) return;
    if (typeof IntersectionObserver === "undefined") {
      setSeen(true);
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        // an element scrolled past before the observer ran (restored scroll) would otherwise stay hidden
        if (entries.some((e) => e.isIntersecting || e.boundingClientRect.top < 0)) {
          setSeen(true);
          io.disconnect();
        }
      },
      { rootMargin: `0px 0px ${margin} 0px` }
    );
    io.observe(el);
    return () => io.disconnect();
  }, [ref, seen, margin]);
  return seen;
}
