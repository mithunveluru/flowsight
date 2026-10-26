"use client";

import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

/** Fades and settles the page body on route change. Enter only, so navigation never waits on an exit. */
export function PageTransition({ children, className }: { children: React.ReactNode; className?: string }) {
  const pathname = usePathname();
  return (
    <div key={pathname} className={cn("fx-rise", className)}>
      {children}
    </div>
  );
}
