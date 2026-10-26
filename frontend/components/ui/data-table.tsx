"use client";

import * as React from "react";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  nextSort,
  selectionState,
  sortRows,
  toggleId,
  type SelectionState,
  type SortDirection,
  type SortState,
  type SortValue,
} from "./table-core";

export type { SelectionState, SortDirection, SortState, SortValue };

/**
 * Semantic table in a scroll container. Rows are plain children, so a
 * virtualizer can render a window of rows (with spacer rows) inside
 * <TableBody>; `containerRef` exposes the scroll element it needs, and
 * `stickyHeader` keeps column headers visible while scrolling.
 */
const Table = React.forwardRef<
  HTMLTableElement,
  React.TableHTMLAttributes<HTMLTableElement> & {
    containerClassName?: string;
    containerRef?: React.Ref<HTMLDivElement>;
    maxHeight?: number | string;
    stickyHeader?: boolean;
  }
>(({ className, containerClassName, containerRef, maxHeight, stickyHeader, ...props }, ref) => (
  <div
    ref={containerRef}
    className={cn("relative w-full overflow-auto", containerClassName)}
    style={maxHeight != null ? { maxHeight } : undefined}
  >
    <table
      ref={ref}
      className={cn(
        "w-full caption-bottom text-sm",
        stickyHeader && "[&_thead_th]:sticky [&_thead_th]:top-0 [&_thead_th]:z-10 [&_thead_th]:bg-card",
        className
      )}
      {...props}
    />
  </div>
));
Table.displayName = "Table";

const TableHeader = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => <thead ref={ref} className={className} {...props} />
);
TableHeader.displayName = "TableHeader";

const TableBody = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => <tbody ref={ref} className={className} {...props} />
);
TableBody.displayName = "TableBody";

const TableRow = React.forwardRef<
  HTMLTableRowElement,
  React.HTMLAttributes<HTMLTableRowElement> & { selected?: boolean }
>(({ className, selected, ...props }, ref) => (
  <tr
    ref={ref}
    data-state={selected ? "selected" : undefined}
    className={cn("data-[state=selected]:bg-band-blue", className)}
    {...props}
  />
));
TableRow.displayName = "TableRow";

type Align = "left" | "right" | "center";
const ALIGN: Record<Align, string> = { left: "text-left", right: "text-right", center: "text-center" };

const ARIA_SORT: Record<SortDirection, "ascending" | "descending"> = {
  asc: "ascending",
  desc: "descending",
};

/** Column header. Passing `onSort` makes it a sort button and sets aria-sort. */
const TableHead = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement> & {
    align?: Align;
    sortDirection?: SortDirection | false;
    onSort?: () => void;
  }
>(({ className, align = "left", sortDirection, onSort, children, ...props }, ref) => {
  const sortable = !!onSort;
  const Icon = sortDirection === "asc" ? ArrowUp : sortDirection === "desc" ? ArrowDown : ChevronsUpDown;
  return (
    <th
      ref={ref}
      scope="col"
      aria-sort={sortable ? (sortDirection ? ARIA_SORT[sortDirection] : "none") : undefined}
      className={cn(ALIGN[align], className)}
      {...props}
    >
      {sortable ? (
        <button
          type="button"
          onClick={onSort}
          className={cn(
            "-mx-1.5 inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 transition-colors duration-fast hover:text-primary",
            sortDirection && "text-primary",
            align === "right" && "flex-row-reverse"
          )}
        >
          {children}
          <Icon className={cn("h-3 w-3 shrink-0", !sortDirection && "opacity-50")} aria-hidden="true" />
        </button>
      ) : (
        children
      )}
    </th>
  );
});
TableHead.displayName = "TableHead";

const TableCell = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement> & { align?: Align }
>(({ className, align = "left", ...props }, ref) => (
  <td ref={ref} className={cn(ALIGN[align], align === "right" && "tabular-nums", className)} {...props} />
));
TableCell.displayName = "TableCell";

function TableCaption({
  className,
  srOnly,
  ...props
}: React.HTMLAttributes<HTMLTableCaptionElement> & { srOnly?: boolean }) {
  return <caption className={cn(srOnly ? "sr-only" : "mt-3 text-caption", className)} {...props} />;
}

/** Full-width row for empty/loading/error content inside the table body. */
function TableEmpty({ colSpan, children }: { colSpan: number; children: React.ReactNode }) {
  return (
    <tr className="hover:bg-transparent">
      <td colSpan={colSpan} className="p-0">
        {children}
      </td>
    </tr>
  );
}

/** Native checkbox with indeterminate support, for row and select-all cells. */
const SelectionCheckbox = React.forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> & { indeterminate?: boolean; "aria-label": string }
>(({ indeterminate, className, ...props }, forwardedRef) => {
  const innerRef = React.useRef<HTMLInputElement>(null);
  React.useImperativeHandle(forwardedRef, () => innerRef.current as HTMLInputElement);
  React.useEffect(() => {
    if (innerRef.current) innerRef.current.indeterminate = !!indeterminate;
  }, [indeterminate]);
  return (
    <input
      ref={innerRef}
      type="checkbox"
      className={cn("h-4 w-4 coarse:h-6 coarse:w-6 cursor-pointer rounded-sm border-input accent-[hsl(var(--primary))] disabled:cursor-not-allowed disabled:opacity-40", className)}
      {...props}
    />
  );
});
SelectionCheckbox.displayName = "SelectionCheckbox";

/**
 * Client-side sorting for a row array. `accessors` maps each sortable column
 * key to the value it sorts by; define it outside the component (or memoize
 * it) so rows are not re-sorted every render. `headProps(key)` spreads onto
 * <TableHead>.
 */
export function useTableSort<T, K extends string>(
  rows: readonly T[],
  accessors: Record<K, (row: T) => SortValue>,
  initial: SortState<K> | null = null
) {
  const [sort, setSort] = React.useState<SortState<K> | null>(initial);
  const sorted = React.useMemo(
    () => (sort ? sortRows(rows, accessors[sort.key], sort.direction) : [...rows]),
    [rows, sort, accessors]
  );

  const toggleSort = React.useCallback((key: K) => setSort((s) => nextSort(s, key)), []);

  const headProps = React.useCallback(
    (key: K) => ({
      sortDirection: sort?.key === key ? sort.direction : (false as const),
      onSort: () => toggleSort(key),
    }),
    [sort, toggleSort]
  );

  return { sorted, sort, setSort, toggleSort, headProps };
}

/**
 * Row selection keyed by id. Selection is pruned to ids still present, so
 * filtering or paging never leaves invisible rows selected.
 */
export function useRowSelection<Id extends string | number>(ids: readonly Id[]) {
  const [raw, setRaw] = React.useState<Set<Id>>(() => new Set());

  const selected = React.useMemo(() => {
    const present = new Set(ids);
    return new Set([...raw].filter((id) => present.has(id)));
  }, [raw, ids]);

  const state = selectionState(selected, ids);

  const toggle = React.useCallback((id: Id) => setRaw((s) => toggleId(s, id)), []);
  const clear = React.useCallback(() => setRaw(new Set()), []);
  const toggleAll = React.useCallback(() => {
    setRaw(state === "all" ? new Set() : new Set(ids));
  }, [state, ids]);

  return {
    selected,
    state,
    count: selected.size,
    isSelected: (id: Id) => selected.has(id),
    toggle,
    toggleAll,
    clear,
    headerCheckboxProps: {
      checked: state === "all",
      indeterminate: state === "some",
      onChange: toggleAll,
    },
    rowCheckboxProps: (id: Id) => ({ checked: selected.has(id), onChange: () => toggle(id) }),
  };
}

export {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  TableCaption,
  TableEmpty,
  SelectionCheckbox,
};
