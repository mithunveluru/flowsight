"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { Plus, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LoadingState } from "@/components/ui/states";
import { TabPanel, Tabs } from "@/components/ui/tabs";
import { TitleRow } from "@/features/shell-pages/title-row";
const ReviewQueue = dynamic(() => import("./review-queue").then((m) => m.ReviewQueue), {
  loading: () => <LoadingState variant="list" rows={4} label="Loading transactions to check" />,
});
import { TransactionsTable } from "./transactions-table";

type View = "all" | "review";

const VIEWS: { value: View; label: string }[] = [
  { value: "all", label: "All transactions" },
  { value: "review", label: "Help categorize" },
];

export default function TransactionsPage() {
  const [view, setView] = useState<View>("all");

  return (
    <div className="space-y-sp-wide">
      <TitleRow
        title="Transactions"
        actions={
          <>
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
          </>
        }
      />

      <Tabs items={VIEWS} value={view} onValueChange={setView} label="View" idPrefix="tx-view" />

      <TabPanel idPrefix="tx-view" value="all" active={view === "all"}>
        <TransactionsTable />
      </TabPanel>
      <TabPanel idPrefix="tx-view" value="review" active={view === "review"}>
        <ReviewQueue />
      </TabPanel>
    </div>
  );
}
