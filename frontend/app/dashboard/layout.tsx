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
    <div className="flex h-screen flex-col bg-background">
      <div className="h-bar bg-chrome" />
      <div className="h-10" />
      <div className="h-11 border-b border-border" />
      <div className="flex-1 p-8 lg:p-12">
        <div className="skeleton mb-6 h-8 w-48 rounded-md" />
        <div className="grid gap-4 md:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="skeleton h-28 rounded-md" />
          ))}
        </div>
      </div>
    </div>
  );
}
