// Currency codes and money formatting shared by capture, review and the table.
// No imports so the node test runner can load it.

export const DEFAULT_CURRENCY = "INR";

// The markets the OCR and categorizer are built for come first.
export const COMMON_CURRENCIES = ["INR", "USD", "AED", "SAR", "EUR", "GBP", "SGD", "AUD", "CAD", "JPY"] as const;

export function normalizeCurrency(code: string | null | undefined): string | null {
  const c = (code ?? "").trim().toUpperCase();
  return /^[A-Z]{3}$/.test(c) ? c : null;
}

/** Picker options: the common list plus any other valid code in play (e.g. one OCR detected), first. */
export function currencyOptions(...extra: (string | null | undefined)[]): string[] {
  const added = extra
    .map(normalizeCurrency)
    .filter((c): c is string => c != null && !(COMMON_CURRENCIES as readonly string[]).includes(c));
  return [...new Set(added), ...COMMON_CURRENCIES];
}

export function currencyName(code: string, locale = "en-IN"): string {
  try {
    return new Intl.DisplayNames([locale], { type: "currency" }).of(code) ?? code;
  } catch {
    return code;
  }
}

export function currencySymbol(code: string | null | undefined, locale = "en-IN"): string {
  const c = normalizeCurrency(code);
  if (!c) return "";
  try {
    const part = new Intl.NumberFormat(locale, { style: "currency", currency: c, currencyDisplay: "narrowSymbol" })
      .formatToParts(0)
      .find((p) => p.type === "currency");
    return part?.value ?? c;
  } catch {
    return c;
  }
}

/** "₹1,234.50", "$12.00", "AED 40.00"; falls back to "<code> <amount>" for codes Intl does not know. */
export function formatMoney(amount: number | null | undefined, currency: string | null | undefined, locale = "en-IN"): string {
  if (amount == null || !Number.isFinite(amount)) return "";
  const c = normalizeCurrency(currency);
  if (!c) return amount.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  try {
    return new Intl.NumberFormat(locale, { style: "currency", currency: c, currencyDisplay: "narrowSymbol" }).format(amount);
  } catch {
    return `${c} ${amount.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
}

/**
 * Parses what a person types into an amount field: "1,234.50", " 99 ", "12.5".
 * Returns null for anything that is not a positive number with at most four decimals
 * (the backend's limit), so a stray letter is an error instead of a silent zero.
 */
export function parseAmount(input: string | number | null | undefined): number | null {
  if (input == null) return null;
  if (typeof input === "number") return Number.isFinite(input) && input > 0 ? input : null;
  const s = input.replace(/[\s,]/g, "");
  if (!/^\d+(\.\d{1,4})?$/.test(s) && !/^\.\d{1,4}$/.test(s)) return null;
  const n = Number(s);
  return n > 0 ? n : null;
}
