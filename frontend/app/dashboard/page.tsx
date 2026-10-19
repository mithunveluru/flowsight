"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Info } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { CaptureMenu } from "@/components/layout/capture-menu";
import { LoadingState, Skeleton } from "@/components/ui/states";
import { useActivityBounds } from "@/features/analytics/hooks";
import { useSampleDataStatus } from "@/features/onboarding/hooks";
import { PARTIAL_HISTORY_DAYS, historyDays, shouldShowWelcome } from "@/features/today/logic";
import { readStored } from "@/features/today/storage";
import {
  NeedsAttention,
  RecentActivity,
  SetupChecklist,
  ThisMonth,
  WhatChanged,
  YouCouldRecover,
} from "@/features/today/sections";

function greeting(hour: number): string {
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

const formatDay = (iso: string) =>
  new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });

export default function TodayPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const bounds = useActivityBounds();
  const [hello, setHello] = useState<string | null>(null);

  // greeting depends on the viewer's clock, so it is set after mount
  useEffect(() => setHello(greeting(new Date().getHours())), []);

  // decide on fresh data only: cached counts can predate an import or sample load
  const settled = bounds.isSuccess && !bounds.isFetching;
  const redirecting =
    settled && shouldShowWelcome(bounds.data.totalTransactionCount, readStored<boolean>(user?.id, "welcome-dismissed") === true);
  useEffect(() => {
    if (redirecting) router.replace("/dashboard/welcome");
  }, [redirecting, router]);

  const firstName = user?.fullName?.split(" ")[0];

  if (redirecting) {
    return <LoadingState variant="card" rows={2} label="Opening the welcome screen" />;
  }

  return (
    <div className="space-y-10">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-display">Today</h1>
          <p className="mt-1.5 text-supporting">
            {hello ? `${hello}${firstName ? `, ${firstName}` : ""}. ` : ""}
            Here&apos;s what changed and what needs you.
          </p>
          <Freshness bounds={bounds} />
        </div>
        <CaptureMenu />
      </header>

      <PartialHistoryNote earliest={bounds.data?.earliestTransactionDate} count={bounds.data?.totalTransactionCount} />

      <div className="grid gap-10 lg:grid-cols-3">
        <div className="space-y-10 lg:col-span-2">
          <WhatChanged userId={user?.id} />
          <NeedsAttention />
          <YouCouldRecover userId={user?.id} />
        </div>
        <div className="space-y-10">
          <SetupChecklist userId={user?.id} />
          <ThisMonth earliest={bounds.data?.earliestTransactionDate} />
        </div>
      </div>

      <RecentActivity />
    </div>
  );
}

function Freshness({ bounds }: { bounds: ReturnType<typeof useActivityBounds> }) {
  if (bounds.isPending) return <Skeleton className="mt-2 h-3.5 w-56" />;
  if (bounds.isError) return null;
  const { totalTransactionCount: count, latestTransactionDate: latest } = bounds.data;
  if (count === 0) return null;
  return (
    <p className="mt-1 text-caption">
      {count.toLocaleString("en-IN")} {count === 1 ? "transaction" : "transactions"}
      {latest && ` · latest dated ${formatDay(latest)}`}
    </p>
  );
}

function PartialHistoryNote({ earliest, count }: { earliest: string | null | undefined; count: number | undefined }) {
  const sample = useSampleDataStatus();
  const days = historyDays(earliest, new Date());
  if (!count || days == null || days >= PARTIAL_HISTORY_DAYS) return null;

  const offerSample = sample.isSuccess && !sample.data.present;
  return (
    <div role="note" className="flex items-start gap-3 rounded-xl border border-border bg-muted/50 px-4 py-3 text-sm">
      <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
      <p className="text-foreground">
        Trends, habits and money drains need about a month of history; you have {days} {days === 1 ? "day" : "days"}.{" "}
        <Link href="/dashboard/capture/import" className="font-medium underline underline-offset-4">
          Import a statement
        </Link>
        {offerSample && (
          <>
            {" "}or{" "}
            <Link href="/dashboard/welcome" className="font-medium underline underline-offset-4">
              explore with sample data
            </Link>
          </>
        )}
        .
      </p>
    </div>
  );
}
