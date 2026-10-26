"use client";

import { useState } from "react";
import Link from "next/link";
import { Plus, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ReviewQueue } from "./review-queue";
import { TransactionsTable } from "./transactions-table";

type View = "all" | "review";

const VIEWS: { id: View; label: string }[] = [
  { id: "all", label: "All transactions" },
  { id: "review", label: "Help categorize" },
];

export default function TransactionsPage() {
  const [view, setView] = useState<View>("all");

  return (
    <div className="animate-fade-in space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-headline">Transactions</h1>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href="/dashboard/capture/import">
              <Upload aria-hidden="true" />
              Import statement
            </Link>
          </Button>
          <Button size="sm" asChild>
            <Link href="/dashboard/capture/manual">
              <Plus aria-hidden="true" />
              Add transaction
            </Link>
          </Button>
        </div>
      </div>

      <div role="group" aria-label="View" className="inline-flex rounded-lg border border-border bg-muted/50 p-0.5">
        {VIEWS.map((v) => (
          <button
            key={v.id}
            type="button"
            aria-pressed={view === v.id}
            onClick={() => setView(v.id)}
            className={cn(
              "min-h-8 rounded-md px-3 text-sm font-medium transition-colors",
              view === v.id ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
            )}
          >
            {v.label}
          </button>
        ))}
      </div>

      {view === "all" ? <TransactionsTable /> : <ReviewQueue />}
    </div>
  );
}
