"use client";

import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionRoot } from "@/components/motion/primitives";
import { AnnouncerProvider } from "@/components/ui/announcer";
import { Toaster } from "@/components/ui/toast";
import { ThemeProvider } from "@/components/ui/theme";
import { TooltipProvider } from "@/components/ui/tooltip";
import { STALE_TIME_MS, shouldRetryQuery } from "@/features/query-core";
import { useAuthStore } from "@/store/auth";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: STALE_TIME_MS,
            retry: shouldRetryQuery,
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  // cached data belongs to one account: drop it on sign-out or a user switch
  useEffect(
    () =>
      useAuthStore.subscribe((state, prev) => {
        if (state.user?.id !== prev.user?.id) queryClient.clear();
      }),
    [queryClient]
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider delayDuration={300}>
          <MotionRoot>
            <AnnouncerProvider>
              {children}
              <Toaster />
            </AnnouncerProvider>
          </MotionRoot>
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
