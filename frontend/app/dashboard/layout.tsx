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

  // wait for Zustand persist hydration before checking auth (avoids a flash-redirect)
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
      <div
        className="w-sidebar shrink-0 border-r"
        style={{
          backgroundColor: "hsl(var(--sidebar-bg))",
          borderColor: "hsl(var(--sidebar-border))",
        }}
      />
      <div className="flex flex-1 flex-col">
        <div className="h-header border-b border-border bg-background" />
        <div className="flex-1 p-8 lg:p-12">
          <div className="skeleton mb-6 h-8 w-48 rounded-md" />
          <div className="grid gap-4 md:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton h-28 rounded-xl" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
