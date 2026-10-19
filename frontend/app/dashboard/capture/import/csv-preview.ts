// Client-side look at a statement before upload. Header aliases mirror the
// backend's StatementFormatDetector so the preview predicts what the import sees.

export const MAX_CSV_BYTES = 5 * 1024 * 1024;
export const PREVIEW_ROWS = 5;

export type ColumnRole = "date" | "description" | "amount" | "debit" | "credit" | "balance" | "type";

const ALIASES: Record<ColumnRole, string[]> = {
  date: ["date", "txn date", "transaction date", "value date", "value dt", "posting date", "trans date"],
  description: ["description", "narration", "details", "particulars", "remarks", "memo", "transaction details"],
  amount: ["amount", "amt", "transaction amount", "txn amount", "transaction amt"],
  debit: ["debit", "debit amount", "withdrawal", "withdrawal amount", "withdrawal amt", "withdrawals", "dr", "dr amount", "money out", "paid out"],
  credit: ["credit", "credit amount", "deposit", "deposit amount", "deposit amt", "deposits", "cr", "cr amount", "money in", "paid in"],
  balance: ["balance", "running balance", "closing balance", "available balance", "ledger balance"],
  type: ["type", "txn type", "transaction type", "cr/dr", "dr/cr", "credit/debit", "debit/credit"],
};

export const ROLE_LABEL: Record<ColumnRole, string> = {
  date: "Date",
  description: "Description",
  amount: "Amount",
  debit: "Money out",
  credit: "Money in",
  balance: "Balance",
  type: "Debit/credit",
};

/** Splits CSV text into rows, honouring quoted fields with commas, quotes and newlines. */
export function parseCsv(text: string, maxRows = Infinity): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  const src = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;

  for (let i = 0; i < src.length && rows.length < maxRows; i++) {
    const c = src[i];
    if (quoted) {
      if (c === '"' && src[i + 1] === '"') {
        field += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        field += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ",") {
      row.push(field);
      field = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && src[i + 1] === "\n") i++;
      row.push(field);
      if (row.some((f) => f.trim() !== "")) rows.push(row);
      row = [];
      field = "";
    } else {
      field += c;
    }
  }
  if (rows.length < maxRows && (field !== "" || row.length > 0)) {
    row.push(field);
    if (row.some((f) => f.trim() !== "")) rows.push(row);
  }
  return rows;
}

export function detectColumns(headers: string[]): Partial<Record<ColumnRole, number>> {
  const found: Partial<Record<ColumnRole, number>> = {};
  (Object.keys(ALIASES) as ColumnRole[]).forEach((role) => {
    const index = headers.findIndex((h) => ALIASES[role].includes(h.trim().toLowerCase()));
    if (index >= 0) found[role] = index;
  });
  return found;
}

export interface CsvPreview {
  headers: string[];
  rows: string[][];
  columns: Partial<Record<ColumnRole, number>>;
  // null when the import should be able to read the file
  problem: string | null;
}

export function previewCsv(text: string, rowLimit = PREVIEW_ROWS): CsvPreview {
  const [headers = [], ...rows] = parseCsv(text, rowLimit + 1);
  const columns = detectColumns(headers);
  const hasMoney = columns.amount != null || columns.debit != null || columns.credit != null;
  const hasBalanceOnly = !hasMoney && columns.balance != null && columns.date != null && columns.description != null;

  let problem: string | null = null;
  if (headers.length === 0) problem = "This file is empty.";
  else if (rows.length === 0) problem = "This file has a header row but no transactions.";
  else if (columns.date == null) problem = "We couldn't find a date column. Name it Date, Txn Date or Value Date.";
  else if (columns.description == null) problem = "We couldn't find a description column. Name it Description, Narration or Details.";
  else if (!hasMoney && !hasBalanceOnly) problem = "We couldn't find an amount column. Use Amount, or Debit and Credit.";

  return { headers, rows, columns, problem };
}

export function validateCsvFile(file: { name: string; size: number }): string | null {
  if (!file.name.toLowerCase().endsWith(".csv")) return "Choose a .csv file exported from your bank.";
  if (file.size > MAX_CSV_BYTES) return "This file is larger than 5 MB. Export a shorter date range.";
  if (file.size === 0) return "This file is empty.";
  return null;
}
