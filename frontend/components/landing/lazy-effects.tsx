"use client";

import dynamic from "next/dynamic";

const ScrollReveal = dynamic(() => import("./scroll-reveal"), { ssr: false });

export function LazyEffects() {
  return <ScrollReveal />;
}
