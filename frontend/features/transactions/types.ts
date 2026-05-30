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

export const CATEGORY_META: Record<
  TransactionCategory,
  { label: string; color: string }
> = {
  FOOD_DINING:    { label: "Food & Dining",      color: "bg-orange-50 text-orange-700 border-orange-200"  },
  GROCERIES:      { label: "Groceries",           color: "bg-green-50 text-green-700 border-green-200"     },
  SHOPPING:       { label: "Shopping",            color: "bg-purple-50 text-purple-700 border-purple-200"  },
  TRANSPORTATION: { label: "Transportation",      color: "bg-blue-50 text-blue-700 border-blue-200"        },
  UTILITIES:      { label: "Utilities",           color: "bg-slate-100 text-slate-700 border-slate-200"    },
  ENTERTAINMENT:  { label: "Entertainment",       color: "bg-pink-50 text-pink-700 border-pink-200"        },
  HEALTHCARE:     { label: "Healthcare",          color: "bg-red-50 text-red-700 border-red-200"           },
  FINANCE:        { label: "Finance & Banking",   color: "bg-indigo-50 text-indigo-700 border-indigo-200"  },
  EDUCATION:      { label: "Education",           color: "bg-yellow-50 text-yellow-700 border-yellow-200"  },
  TRAVEL:         { label: "Travel",              color: "bg-teal-50 text-teal-700 border-teal-200"        },
  SUBSCRIPTIONS:  { label: "Subscriptions",       color: "bg-violet-50 text-violet-700 border-violet-200"  },
  INCOME:         { label: "Income",              color: "bg-emerald-50 text-emerald-700 border-emerald-200"},
  TRANSFER:       { label: "Transfer",            color: "bg-cyan-50 text-cyan-700 border-cyan-200"        },
  OTHER:          { label: "Other",               color: "bg-gray-50 text-gray-600 border-gray-200"        },
  UNCATEGORIZED:  { label: "Uncategorized",       color: "bg-slate-100 text-slate-400 border-slate-200"    },
};

export const CATEGORY_OPTIONS = Object.entries(CATEGORY_META)
  .filter(([k]) => k !== "UNCATEGORIZED")
  .map(([value, { label }]) => ({ value: value as TransactionCategory, label }));
