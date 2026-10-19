"use client";

import { useState } from "react";
import { FlaskConical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAnnounce } from "@/components/ui/announcer";
import { useRemoveSampleData, useSampleDataStatus } from "@/features/onboarding/hooks";

// Shown on every dashboard page while the account holds SAMPLE transactions.
export function SampleDataBanner() {
  const status = useSampleDataStatus();
  const remove = useRemoveSampleData();
  const announce = useAnnounce();
  const [confirming, setConfirming] = useState(false);

  if (!status.data?.present) return null;

  const onRemove = () =>
    remove.mutate(undefined, {
      onSuccess: () => {
        setConfirming(false);
        announce("Sample data removed.");
      },
    });

  return (
    <div role="region" aria-label="Sample data" className="border-b border-border bg-brand-soft">
      <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-3 gap-y-2 px-4 py-2.5 text-sm sm:px-6 lg:px-12">
        <FlaskConical className="h-4 w-4 shrink-0 text-brand" strokeWidth={1.75} aria-hidden="true" />
        <p className="min-w-0 flex-1 text-foreground">
          {confirming
            ? `Remove all ${status.data.transactionCount} sample transactions? Your own data stays.`
            : "You're exploring sample data."}
        </p>
        {remove.isError && <p className="w-full text-xs text-warning sm:w-auto">That didn&apos;t work. Try again.</p>}
        {confirming ? (
          <div className="flex items-center gap-2">
            <Button size="sm" variant="destructive" onClick={onRemove} disabled={remove.isPending}>
              {remove.isPending ? "Removing" : "Remove"}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setConfirming(false)} disabled={remove.isPending}>
              Cancel
            </Button>
          </div>
        ) : (
          <Button size="sm" variant="outline" onClick={() => setConfirming(true)}>
            Remove it
          </Button>
        )}
      </div>
    </div>
  );
}
