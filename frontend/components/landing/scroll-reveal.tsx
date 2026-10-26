"use client";

import { useEffect } from "react";

/** Marks [data-reveal] elements as visible the first time they enter the viewport. */
export default function ScrollReveal() {
  useEffect(() => {
    const root = document.querySelector("[data-landing]");
    if (!root) return;
    const targets = Array.from(root.querySelectorAll<HTMLElement>("[data-reveal]"));
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          // elements scrolled past before the observer ran (restored scroll) would otherwise stay hidden
          if (entry.isIntersecting || entry.boundingClientRect.top < 0) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.15, rootMargin: "0px 0px -6% 0px" }
    );
    targets.forEach((t) => io.observe(t));
    root.setAttribute("data-reveal-ready", "");
    return () => {
      io.disconnect();
      root.removeAttribute("data-reveal-ready");
    };
  }, []);
  return null;
}
