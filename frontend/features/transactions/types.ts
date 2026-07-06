export type TransactionType = "DEBIT" | "CREDIT";
export type TransactionSource = "MANUAL" | "CSV" | "SMS" | "OCR";
export type TransactionCategory =
  | "FOOD_DINING"
  | "GROCERIES"
  | "SHOPPING"
  | "TRANSPORTATION"
  | "UTILITIES"
  | "ENTERTAINMENT"
  | "HEALTHCARE"
  | "FINANCE"
  | "EDUCATION"
  | "TRAVEL"
  | "SUBSCRIPTIONS"
  | "INCOME"
  | "TRANSFER"
  | "OTHER"
  | "UNCATEGORIZED";

export interface Transaction {
  id: string;
  amount: number;
  currency: string;
  transactionDate: string;
  description: string;
  merchant: string | null;
  category: TransactionCategory | null;
  categoryDisplayName: string | null;
  type: TransactionType;
  source: TransactionSource;
  confidenceScore: number | null;
  notes: string | null;
  reviewed: boolean;
  createdAt: string;
}

export interface TransactionPage {
  content: Transaction[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface CreateTransactionPayload {
  amount: number;
  currency?: string;
  transactionDate: string;
  description: string;
  merchant?: string;
  type: TransactionType;
  category?: TransactionCategory;
  notes?: string;
}

export interface BulkImportResult {
  totalRows: number;
  imported: number;
  skipped: number;
  errors: string[];
  firstTransactionDate: string | null;
  lastTransactionDate:  string | null;
  totalAmountImported:  number | null;
}

// Category identity is carried by a small colored dot from the curated chart
// palette (globals.css) — pills themselves stay quiet and neutral so a table
// of categories reads as one calm family instead of a rainbow.
export const CATEGORY_META: Record<
  TransactionCategory,
  { label: string; dot: string }
> = {
  FOOD_DINING:    { label: "Food & Dining",     dot: "bg-[hsl(var(--chart-1))]"  },
  GROCERIES:      { label: "Groceries",          dot: "bg-[hsl(var(--chart-2))]"  },
  SHOPPING:       { label: "Shopping",           dot: "bg-[hsl(var(--chart-3))]"  },
  TRANSPORTATION: { label: "Transportation",     dot: "bg-[hsl(var(--chart-4))]"  },
  UTILITIES:      { label: "Utilities",          dot: "bg-[hsl(var(--chart-5))]"  },
  ENTERTAINMENT:  { label: "Entertainment",      dot: "bg-[hsl(var(--chart-6))]"  },
  HEALTHCARE:     { label: "Healthcare",         dot: "bg-[hsl(var(--chart-7))]"  },
  FINANCE:        { label: "Finance & Banking",  dot: "bg-[hsl(var(--chart-8))]"  },
  EDUCATION:      { label: "Education",          dot: "bg-[hsl(var(--chart-9))]"  },
  TRAVEL:         { label: "Travel",             dot: "bg-[hsl(var(--chart-13))]" },
  SUBSCRIPTIONS:  { label: "Subscriptions",      dot: "bg-[hsl(var(--chart-11))]" },
  INCOME:         { label: "Income",             dot: "bg-positive"               },
  TRANSFER:       { label: "Transfer",           dot: "bg-[hsl(var(--chart-10))]" },
  OTHER:          { label: "Other",              dot: "bg-[hsl(var(--chart-14))]" },
  UNCATEGORIZED:  { label: "Uncategorized",      dot: "bg-[hsl(var(--chart-15))]" },
};

export const CATEGORY_OPTIONS = Object.entries(CATEGORY_META)
  .filter(([k]) => k !== "UNCATEGORIZED")
  .map(([value, { label }]) => ({ value: value as TransactionCategory, label }));
