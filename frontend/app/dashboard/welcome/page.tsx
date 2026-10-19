"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, FlaskConical, Loader2, Receipt, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useAnnounce } from "@/components/ui/announcer";
import { useLoadSampleData, useSampleDataStatus } from "@/features/onboarding/hooks";
import { writeStored } from "@/features/today/storage";
import { useAuthStore } from "@/store/auth";

export default function WelcomePage() {
  const router = useRouter();
  const userId = useAuthStore((s) => s.user?.id);
  const firstName = useAuthStore((s) => s.user?.fullName?.split(" ")[0]);
  const sample = useSampleDataStatus();
  const loadSample = useLoadSampleData();
  const announce = useAnnounce();

  const skip = () => {
    writeStored(userId, "welcome-dismissed", true);
    router.push("/dashboard");
  };

  const exploreSample = () =>
    loadSample.mutate(undefined, {
      onSuccess: (status) => {
        announce(`Loaded ${status.transactionCount} sample transactions.`);
        router.push("/dashboard");
      },
    });

  return (
    <div className="mx-auto max-w-4xl space-y-8">
      <div>
        <p className="text-overline">Getting started</p>
        <h1 className="mt-1 text-headline sm:text-display">Welcome{firstName ? `, ${firstName}` : ""}</h1>
        <p className="mt-2 max-w-2xl text-supporting">
          FlowSight works from your transactions. Pick one way in; you can add the others later.
        </p>
      </div>

      <ul className="grid gap-4 md:grid-cols-3">
        <PathCard
          icon={<Upload />}
          title="Import a bank statement"
          badge="Recommended"
          time="About a minute"
          description="Upload your bank's CSV export. Months of history at once, so trends and drains work straight away."
          action={
            <Button asChild className="w-full">
              <Link href="/dashboard/capture/import">
                Import CSV
                <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
          }
        />
        <PathCard
          icon={<Receipt />}
          title="Scan a receipt"
          time="Under a minute"
          description="Take a photo, check the details we read, and confirm. Trends and drains need more history than one receipt."
          action={
            <Button variant="outline" asChild className="w-full">
              <Link href="/dashboard/capture/receipt">Scan a receipt</Link>
            </Button>
          }
        />
        {!sample.isError && (
          <PathCard
            icon={<FlaskConical />}
            title="Explore with sample data"
            time="A few seconds"
            description="Three months of made-up transactions so you can try every view. Remove them with one click whenever you like."
            action={
              sample.data?.present ? (
                <Button variant="outline" asChild className="w-full">
                  <Link href="/dashboard">Sample data is loaded: go to Today</Link>
                </Button>
              ) : (
                <Button
                  variant="outline"
                  className="w-full"
                  onClick={exploreSample}
                  disabled={sample.isLoading || loadSample.isPending}
                >
                  {loadSample.isPending && <Loader2 className="animate-spin" aria-hidden="true" />}
                  {loadSample.isPending ? "Loading sample data" : "Use sample data"}
                </Button>
              )
            }
            error={loadSample.isError ? "We couldn't load the sample data. Try again in a moment." : undefined}
          />
        )}
      </ul>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <Button variant="ghost" onClick={skip}>
          Skip for now
        </Button>
        <p className="text-caption">You can always add data from Capture in the sidebar.</p>
      </div>
    </div>
  );
}

function PathCard({
  icon,
  title,
  badge,
  time,
  description,
  action,
  error,
}: {
  icon: React.ReactNode;
  title: string;
  badge?: string;
  time: string;
  description: string;
  action: React.ReactNode;
  error?: string;
}) {
  return (
    <Card as="li" padding="lg" className="flex flex-col">
      <div className="flex items-start justify-between gap-3">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-muted text-foreground [&_svg]:h-4 [&_svg]:w-4"
        >
          {icon}
        </span>
        {badge && (
          <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] font-semibold text-brand">{badge}</span>
        )}
      </div>
      <h2 className="mt-4 text-title">{title}</h2>
      <p className="mt-0.5 text-caption">{time}</p>
      <p className="mt-3 flex-1 text-supporting">{description}</p>
      {error && (
        <p role="alert" className="mt-3 text-sm text-warning">
          {error}
        </p>
      )}
      <div className="mt-5">{action}</div>
    </Card>
  );
}
