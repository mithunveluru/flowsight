"use client";

import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { CheckCheck, Loader2, Plus, ReceiptText, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfirmButton } from "@/components/ui/confirm-button";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { Input } from "@/components/ui/input";
import {
  SelectionCheckbox,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useRowSelection,
} from "@/components/ui/data-table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAnnounce } from "@/components/ui/announcer";
import { RuleSuggestionCard } from "@/features/merchants/rule-suggestion";
import { takeNewTransaction } from "@/features/transactions/new-row";
import { toast } from "@/components/ui/toast";
import { transactionApi } from "@/features/transactions/api";
import { CategoryPicker, CategorySignalChip } from "@/features/transactions/category-ui";
import { formatMoney } from "@/features/transactions/currency";
import { TABLE_PAGE_SIZE, useTransactionCache, useTransactionPages, useUpdateTransaction } from "@/features/transactions/hooks";
import {
  NO_CLIENT_FILTERS,
  SOURCE_LABEL,
  collectSuggestions,
  filterRows,
  hasClientFilters,
  planBulk,
  runBulk,
  shouldLoadMore,
  windowRange,
  type BulkAction,
  type ClientFilters,
} from "@/features/transactions/table-logic";
import {
  CATEGORY_OPTIONS,
  type MerchantRuleSuggestion,
  type Transaction,
  type TransactionCategory,
  type TransactionSource,
  type UpdateTransactionPayload,
} from "@/features/transactions/types";
import { cn } from "@/lib/utils";
import { InlineText } from "./inline-edit";
const NewRowFlash = dynamic(() => import("./new-row-flash").then((m) => m.NewRowFlash), { ssr: false });

const ROW_HEIGHT = 68;
const ALL = "__all__";
const SOURCES: TransactionSource[] = ["MANUAL", "CSV", "OCR", "SAMPLE"];

const dateText = (iso: string) =>
  new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });

export function TransactionsTable() {
  const announce = useAnnounce();
  const cache = useTransactionCache();
  const [category, setCategory] = useState<TransactionCategory | undefined>();
  const [client, setClient] = useState<ClientFilters>(NO_CLIENT_FILTERS);
  const query = useTransactionPages({ category });
  const update = useUpdateTransaction();
  const [suggestions, setSuggestions] = useState<MerchantRuleSuggestion[]>([]);
  const [bulkBusy, setBulkBusy] = useState(false);

  const loaded = useMemo(() => query.data?.pages.flatMap((p) => p.content) ?? [], [query.data]);
  const total = query.data?.pages[0]?.totalElements ?? 0;
  const rows = useMemo(() => filterRows(loaded, client), [loaded, client]);
  const ids = useMemo(() => rows.map((r) => r.id), [rows]);
  const selection = useRowSelection(ids);

  const scrollRef = useRef<HTMLDivElement>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewport, setViewport] = useState(600);
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const measure = () => setViewport(el.clientHeight);
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [query.isPending]);

  const range = windowRange({ scrollTop, viewportHeight: viewport, rowHeight: ROW_HEIGHT, count: rows.length });
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;
  useEffect(() => {
    if (shouldLoadMore(range.end, rows.length, !!hasNextPage) && !isFetchingNextPage) void fetchNextPage();
  }, [range.end, rows.length, hasNextPage, isFetchingNextPage, fetchNextPage]);

  const remember = useCallback(
    (...incoming: (MerchantRuleSuggestion | null | undefined)[]) => setSuggestions((cur) => collectSuggestions(cur, incoming)),
    []
  );

  const patch = useCallback(
    async (id: string, body: UpdateTransactionPayload) => {
      const updated = await update.mutateAsync({ id, patch: body });
      remember(updated.ruleSuggestion);
      return updated;
    },
    [update, remember]
  );

  const remove = useCallback(
    async (id: string) => {
      try {
        await transactionApi.delete(id);
        cache.removed([id]);
        announce("Transaction deleted.");
        toast({ title: "Transaction removed", tone: "success" });
      } catch (e) {
        announce(e instanceof Error ? e.message : "Could not delete the transaction.", "assertive");
      }
    },
    [cache, announce]
  );

  const runAction = async (action: BulkAction) => {
    const { targets, unchanged } = planBulk(action, rows, selection.selected);
    if (targets.length === 0) {
      announce(unchanged ? "Nothing to change for the selected transactions." : "No transactions selected.");
      return;
    }
    setBulkBusy(true);
    const outcome = await runBulk<unknown>(targets, (id) =>
      action.type === "delete"
        ? transactionApi.delete(id)
        : transactionApi.update(id, action.type === "review" ? { reviewed: true } : { category: action.category })
    );
    if (action.type === "delete") cache.removed(outcome.succeeded.map((s) => s.id));
    else {
      const updated = outcome.succeeded.map((s) => s.result as Transaction);
      cache.updated(updated);
      remember(...updated.map((t) => t.ruleSuggestion));
    }
    selection.clear();
    setBulkBusy(false);
    const done = outcome.succeeded.length;
    const failed = outcome.failed.length;
    announce(
      `${action.type === "delete" ? "Deleted" : "Updated"} ${done} transaction${done === 1 ? "" : "s"}${failed ? `, ${failed} failed` : ""}.`,
      failed ? "assertive" : "polite"
    );
    toast({
      title: `${done} transaction${done === 1 ? "" : "s"} ${action.type === "delete" ? "removed" : "updated"}`,
      description: failed ? `${failed} could not be changed.` : undefined,
      tone: failed ? "error" : "success",
    });
  };

  const visible = rows.slice(range.start, range.end);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-48 flex-1 sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input
            aria-label="Search loaded transactions"
            placeholder="Search merchant, description, notes"
            value={client.query}
            onChange={(e) => setClient((c) => ({ ...c, query: e.target.value }))}
            className="h-8 pl-9 text-xs"
          />
        </div>
        <Select value={category ?? ALL} onValueChange={(v) => setCategory(v === ALL ? undefined : (v as TransactionCategory))}>
          <SelectTrigger aria-label="Category" className="h-8 w-44 text-xs">
            <SelectValue placeholder="All categories" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All categories</SelectItem>
            {CATEGORY_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={client.source ?? ALL}
          onValueChange={(v) => setClient((c) => ({ ...c, source: v === ALL ? null : (v as TransactionSource) }))}
        >
          <SelectTrigger aria-label="Source" className="h-8 w-36 text-xs">
            <SelectValue placeholder="All sources" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All sources</SelectItem>
            {SOURCES.map((s) => (
              <SelectItem key={s} value={s}>
                {SOURCE_LABEL[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <label className="flex min-h-8 items-center gap-2 text-xs text-foreground">
          <input
            type="checkbox"
            checked={client.needsReview}
            onChange={(e) => setClient((c) => ({ ...c, needsReview: e.target.checked }))}
            className="h-4 w-4 accent-primary"
          />
          Not reviewed yet
        </label>
      </div>

      {suggestions.map((s) => (
        <RuleSuggestionCard key={s.ruleId} suggestion={s} onDismiss={() => setSuggestions((cur) => cur.filter((x) => x.ruleId !== s.ruleId))} />
      ))}

      {selection.count > 0 && <BulkBar count={selection.count} busy={bulkBusy} onAction={runAction} onClear={selection.clear} />}

      {query.isPending ? (
        <LoadingState variant="table" rows={8} label="Loading transactions" />
      ) : query.error ? (
        <ErrorState title="Transactions did not load" error={query.error} onRetry={() => query.refetch()} />
      ) : rows.length === 0 ? (
        loaded.length === 0 && !category ? (
          <EmptyState
            icon={<ReceiptText />}
            title="Nothing recorded yet"
            description="Import a bank statement CSV or add your first transaction by hand."
            action={
              <Button asChild>
                <Link href="/dashboard/capture/import">Import statement</Link>
              </Button>
            }
            secondaryAction={
              <Button variant="outline" asChild>
                <Link href="/dashboard/capture/manual">
                  <Plus aria-hidden="true" />
                  Add manually
                </Link>
              </Button>
            }
          />
        ) : (
          <EmptyState
            icon={<Search />}
            title="No matching transactions"
            description={
              hasNextPage
                ? "Search and the source filters look at transactions already loaded. Scroll the list to load more, or change the category."
                : "Try a different filter."
            }
            action={
              <Button variant="outline" onClick={() => { setClient(NO_CLIENT_FILTERS); setCategory(undefined); }}>
                Clear filters
              </Button>
            }
          />
        )
      ) : (
        <div className="isolate overflow-hidden rounded-md bg-canvas">
          <ScrollBinder target={scrollRef} onScroll={setScrollTop} />
          <Table
            containerRef={scrollRef}
            containerClassName="max-h-[68dvh] min-h-48"
            stickyHeader
            className="min-w-[980px] table-fixed"
            aria-rowcount={rows.length + 1}
          >
            <caption className="sr-only">Transactions, newest first. Merchant, category and notes can be edited in place.</caption>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10 px-3 py-3">
                  <SelectionCheckbox aria-label="Select all loaded transactions" {...selection.headerCheckboxProps} />
                </TableHead>
                <TableHead className="w-28 px-3 py-3">Date</TableHead>
                <TableHead className="w-[22%] px-3 py-3">Merchant</TableHead>
                <TableHead className="w-52 px-3 py-3">Category</TableHead>
                <TableHead className="w-24 px-3 py-3">Source</TableHead>
                <TableHead className="px-3 py-3">Notes</TableHead>
                <TableHead align="right" className="w-32 px-3 py-3">Amount</TableHead>
                <TableHead className="w-10 px-3 py-3"><span className="sr-only">Delete</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {range.padTop > 0 && <tr aria-hidden="true" style={{ height: range.padTop }} />}
              {visible.map((tx, i) => (
                <Row
                  key={tx.id}
                  tx={tx}
                  index={range.start + i}
                  selected={selection.isSelected(tx.id)}
                  onToggle={selection.toggle}
                  patch={patch}
                  onDelete={remove}
                />
              ))}
              {range.padBottom > 0 && <tr aria-hidden="true" style={{ height: range.padBottom }} />}
            </TableBody>
          </Table>
          <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-2.5 text-xs text-muted-foreground tabular-nums">
            <span>
              {hasClientFilters(client)
                ? `${rows.length.toLocaleString("en-IN")} match among ${loaded.length.toLocaleString("en-IN")} loaded`
                : `${loaded.length.toLocaleString("en-IN")} of ${total.toLocaleString("en-IN")} loaded`}
            </span>
            {isFetchingNextPage ? (
              <span className="inline-flex items-center gap-1.5">
                <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" /> Loading more
              </span>
            ) : hasNextPage ? (
              <button type="button" className="font-medium text-primary hover:underline" onClick={() => void fetchNextPage()}>
                Load the next {TABLE_PAGE_SIZE}
              </button>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

/** Reports the scroll offset of the table's scroll container (the Table component owns the element). */
function ScrollBinder({ target, onScroll }: { target: React.RefObject<HTMLDivElement | null>; onScroll: (top: number) => void }) {
  useEffect(() => {
    const el = target.current;
    if (!el) return;
    let frame = 0;
    const handle = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => onScroll(el.scrollTop));
    };
    el.addEventListener("scroll", handle, { passive: true });
    return () => {
      el.removeEventListener("scroll", handle);
      cancelAnimationFrame(frame);
    };
  }, [target, onScroll]);
  return null;
}

const BulkBar = ({
  count,
  busy,
  onAction,
  onClear,
}: {
  count: number;
  busy: boolean;
  onAction: (a: BulkAction) => void;
  onClear: () => void;
}) => (
  <div
    role="region"
    aria-label="Actions for selected transactions"
    className="flex flex-wrap items-center gap-2 rounded-md bg-band-blue px-3 py-2"
  >
    <span className="text-sm font-medium tabular-nums">{count} selected</span>
    <Select value="" onValueChange={(v) => onAction({ type: "recategorize", category: v as TransactionCategory })} disabled={busy}>
      <SelectTrigger aria-label="Move selected to a category" className="h-8 w-48 bg-card text-xs">
        <SelectValue placeholder="Move to category" />
      </SelectTrigger>
      <SelectContent>
        {CATEGORY_OPTIONS.map((o) => (
          <SelectItem key={o.value} value={o.value}>
            {o.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
    <Button size="sm" variant="outline" disabled={busy} onClick={() => onAction({ type: "review" })}>
      <CheckCheck aria-hidden="true" />
      Mark reviewed
    </Button>
    <ConfirmButton
      onConfirm={() => onAction({ type: "delete" })}
      busy={busy}
      label="Delete selected"
      confirmLabel={`Delete ${count}`}
      className="h-8 border border-border bg-card px-2.5 text-muted-foreground"
    />
    <Button size="sm" variant="ghost" className="ml-auto" onClick={onClear} disabled={busy}>
      Clear selection
    </Button>
  </div>
);

const Row = memo(function Row({
  tx,
  index,
  selected,
  onToggle,
  patch,
  onDelete,
}: {
  tx: Transaction;
  index: number;
  selected: boolean;
  onToggle: (id: string) => void;
  patch: (id: string, body: UpdateTransactionPayload) => Promise<Transaction>;
  onDelete: (id: string) => void;
}) {
  const name = tx.merchant ?? tx.description;
  const debit = tx.type === "DEBIT";
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [fresh] = useState(() => takeNewTransaction(tx.id));

  const changeCategory = async (c: TransactionCategory) => {
    setSaving(true);
    setCategoryError(null);
    try {
      await patch(tx.id, { category: c });
    } catch (e) {
      setCategoryError(e instanceof Error && e.message ? e.message : "Could not save.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <TableRow
      selected={selected}
      aria-rowindex={index + 2}
      style={{ height: ROW_HEIGHT }}
      className="relative text-sm"
    >
      <TableCell className="px-3">
        {fresh && <NewRowFlash />}
        <SelectionCheckbox aria-label={`Select ${name}`} checked={selected} onChange={() => onToggle(tx.id)} />
      </TableCell>
      <TableCell className="whitespace-nowrap px-3 tabular-nums text-muted-foreground">{dateText(tx.transactionDate)}</TableCell>
      <TableCell className="px-3">
        <InlineText
          value={tx.merchant ?? ""}
          label={`merchant for ${name}`}
          placeholder={tx.description}
          maxLength={255}
          onSave={async (merchant) => {
            if (!merchant) throw new Error("Merchant cannot be empty.");
            await patch(tx.id, { merchant });
          }}
          className="font-medium text-foreground"
        >
          {name}
        </InlineText>
        {tx.merchant && tx.description !== tx.merchant && (
          <p className="truncate text-xs text-muted-foreground" title={tx.description}>
            {tx.description}
          </p>
        )}
      </TableCell>
      <TableCell className="px-3">
        <CategoryPicker
          tx={tx}
          aria-label={`Category for ${name}`}
          disabled={saving}
          onChange={changeCategory}
          triggerClassName="h-8 w-full text-xs"
        />
        {categoryError ? (
          <p role="alert" className="truncate text-xs font-medium text-warning">{categoryError}</p>
        ) : (
          <div className="mt-1 flex items-center gap-1.5">
            <CategorySignalChip tx={tx} />
          </div>
        )}
      </TableCell>
      <TableCell className="px-3">
        <span className="inline-flex whitespace-nowrap rounded-md border border-border px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
          {SOURCE_LABEL[tx.source]}
        </span>
      </TableCell>
      <TableCell className="px-3 text-xs">
        <InlineText
          value={tx.notes ?? ""}
          label={`notes for ${name}`}
          placeholder="Add a note"
          maxLength={500}
          onSave={(notes) => patch(tx.id, { notes })}
        />
      </TableCell>
      <TableCell align="right" className="whitespace-nowrap px-3">
        <span className={cn("font-medium", debit ? "text-foreground" : "text-positive")}>
          <span className="sr-only">{debit ? "Spent " : "Received "}</span>
          <span aria-hidden="true">{debit ? "−" : "+"}</span>
          {formatMoney(tx.amount, tx.currency)}
        </span>
      </TableCell>
      <TableCell className="px-3">
        <ConfirmButton onConfirm={() => onDelete(tx.id)} label={`Delete ${name}`} />
      </TableCell>
    </TableRow>
  );
});
