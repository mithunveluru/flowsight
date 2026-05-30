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

export interface OcrExtractionResult {
  amount: number | null;
  date: string | null;
  merchant: string | null;
  merchantAddress: string | null;
  currency: string | null;
  successful: boolean;
  rawText: string | null;
  lineItems: ReceiptLineItem[];
  confidence: number | null;
  requiresConfirmation: boolean;
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
