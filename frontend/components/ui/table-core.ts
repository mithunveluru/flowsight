export type SortDirection = "asc" | "desc";
export interface SortState<K extends string> {
  key: K;
  direction: SortDirection;
}
export type SortValue = string | number | Date | null | undefined;
export type SelectionState = "none" | "some" | "all";

// Clicking a column cycles asc -> desc -> unsorted; a new column starts at asc.
export function nextSort<K extends string>(current: SortState<K> | null, key: K): SortState<K> | null {
  if (!current || current.key !== key) return { key, direction: "asc" };
  if (current.direction === "asc") return { key, direction: "desc" };
  return null;
}

function isMissing(v: SortValue): boolean {
  return v == null || (typeof v === "number" && Number.isNaN(v)) || (v instanceof Date && Number.isNaN(v.getTime()));
}

function compareValues(a: NonNullable<SortValue>, b: NonNullable<SortValue>): number {
  const av = a instanceof Date ? a.getTime() : a;
  const bv = b instanceof Date ? b.getTime() : b;
  if (typeof av === "string" && typeof bv === "string") {
    return av.localeCompare(bv, undefined, { sensitivity: "base", numeric: true });
  }
  return Number(av) - Number(bv);
}

/** Stable sort; missing values always go last regardless of direction. */
export function sortRows<T>(rows: readonly T[], accessor: (row: T) => SortValue, direction: SortDirection): T[] {
  const sign = direction === "asc" ? 1 : -1;
  return rows
    .map((row, index) => ({ row, index, value: accessor(row) }))
    .sort((x, y) => {
      const xMissing = isMissing(x.value);
      const yMissing = isMissing(y.value);
      if (xMissing || yMissing) return xMissing === yMissing ? x.index - y.index : xMissing ? 1 : -1;
      return compareValues(x.value as NonNullable<SortValue>, y.value as NonNullable<SortValue>) * sign || x.index - y.index;
    })
    .map((entry) => entry.row);
}

export function selectionState<Id>(selected: ReadonlySet<Id>, ids: readonly Id[]): SelectionState {
  let count = 0;
  for (const id of ids) if (selected.has(id)) count++;
  if (count === 0) return "none";
  return count === ids.length ? "all" : "some";
}

export function toggleId<Id>(selected: ReadonlySet<Id>, id: Id): Set<Id> {
  const next = new Set(selected);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}
