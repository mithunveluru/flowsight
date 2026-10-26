import type { Transaction, TransactionCategory } from "@/features/transactions/types";

export interface ReceiptConfirmPayload {
  merchant: string;
  amount: number;
  date: string;           // ISO yyyy-MM-dd
  category?: TransactionCategory;
  notes?: string;
  currency?: string;
}

export type ReceiptStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface ReceiptLineItem {
  itemName: string | null;
  itemQuantity: string | null;
  itemPrice: number | null;
}

// What the review screen does with a field: prefill, prefill and flag, or ask.
export type ReceiptDecision = "ACCEPT" | "ACCEPT_WITH_HIGHLIGHT" | "ASK_USER";

export type ReceiptField = "merchant" | "amount" | "date" | "currency";

export interface FieldAssessment {
  confidence: number | null;
  decision: ReceiptDecision | null;
  // machine-readable reason, e.g. "arithmetic+total-label", "conflict:...", "day-month-order-from-USD"
  basis: string | null;
}

export interface ReceiptAmountOption {
  amount: number;
  label: string | null;
  basis: string | null;
}

export interface ReceiptChargeLine {
  label: string | null;
  amount: number | null;
}

export interface OcrExtractionResult {
  amount: number | null;
  date: string | null;
  merchant: string | null;
  merchantRaw?: string | null;
  merchantAddress: string | null;
  currency: string | null;
  successful: boolean;
  rawText: string | null;
  lineItems: ReceiptLineItem[];
  confidence: number | null;
  requiresConfirmation: boolean;
  subtotal?: number | null;
  taxes?: ReceiptChargeLine[] | null;
  discounts?: ReceiptChargeLine[] | null;
  tip?: number | null;
  roundOff?: number | null;
  itemsReconciled?: boolean | null;
  // totals read from the receipt, best first
  amountCandidates?: ReceiptAmountOption[] | null;
  fields?: Partial<Record<ReceiptField, FieldAssessment>> | null;
  decision?: ReceiptDecision | null;
  // engines tried in order, e.g. "ML_SERVICE: ERROR", "TESSERACT: ok"
  engineAttempts?: string[] | null;
}

export interface Receipt {
  id: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  status: ReceiptStatus;
  ocrText: string | null;
  errorMessage: string | null;
  extraction: OcrExtractionResult | null;
  transaction: Transaction | null;
  lineItems: ReceiptLineItem[] | null;
  ocrProvider: string | null;
  confidence: number | null;
  requiresConfirmation: boolean;
  // confirmed currency once confirmed, else the detected one
  currency?: string | null;
  decision?: ReceiptDecision | null;
  // GET /receipts/{id}/image serves the review copy while this is true (it can still 404)
  imageAvailable?: boolean;
  imageExpiresAt?: string | null;
  createdAt: string;
}

export interface ReceiptPage {
  content: Receipt[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
