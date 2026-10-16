"use client";

import { AnnouncerProvider } from "@/components/ui/announcer";
import { ThemeProvider } from "@/components/ui/theme";
import { TooltipProvider } from "@/components/ui/tooltip";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider>
      <TooltipProvider delayDuration={300}>
        <AnnouncerProvider>{children}</AnnouncerProvider>
      </TooltipProvider>
    </ThemeProvider>
  );
}
