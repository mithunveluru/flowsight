"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth";
import { Shell } from "@/components/layout/shell";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isAuthenticated } = useAuthStore();
  const router = useRouter();

  // Zustand persist rehydrates from localStorage asynchronously after first render.
  // We must wait for hydration before checking auth state to avoid a flash-redirect
  // on authenticated users whose token is stored in localStorage.
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace("/auth/login");
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated) {
    return <DashboardSkeleton />;
  }

  if (!isAuthenticated) {
    return null;
  }

  return <Shell>{children}</Shell>;
}

function DashboardSkeleton() {
  return (
    <div className="flex h-screen bg-background">
      <div className="w-sidebar shrink-0 bg-slate-900" />
      <div className="flex flex-1 flex-col">
        <div className="h-header border-b border-border bg-background" />
        <div className="flex-1 bg-slate-50 p-6">
          <div className="h-8 w-48 rounded-md bg-slate-200 animate-pulse mb-4" />
          <div className="grid gap-4 md:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-28 rounded-lg border border-slate-200 bg-white animate-pulse" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
