// INR-first formatting helpers, matching the web app's conventions.

export function formatINR(amount: number, opts: { compact?: boolean } = {}): string {
  if (opts.compact) {
    const abs = Math.abs(amount);
    if (abs >= 1_00_00_000) return `₹${(amount / 1_00_00_000).toFixed(1)}Cr`;
    if (abs >= 1_00_000) return `₹${(amount / 1_00_000).toFixed(1)}L`;
    if (abs >= 1_000) return `₹${(amount / 1_000).toFixed(1)}K`;
  }
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatINRPrecise(amount: number): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** "12 Jun" style short date from an ISO date string. */
export function formatShortDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

/** Today's date as yyyy-MM-dd in local time. */
export function todayISO(): string {
  const d = new Date();
  const tz = d.getTimezoneOffset() * 60_000;
  return new Date(d.getTime() - tz).toISOString().slice(0, 10);
}

/** First day of the current ISO week (Monday) as yyyy-MM-dd. */
export function weekStartISO(): string {
  const d = new Date();
  const day = (d.getDay() + 6) % 7; // 0 = Monday
  d.setDate(d.getDate() - day);
  const tz = d.getTimezoneOffset() * 60_000;
  return new Date(d.getTime() - tz).toISOString().slice(0, 10);
}

/** First day of the current month as yyyy-MM-dd. */
export function monthStartISO(): string {
  const d = new Date();
  d.setDate(1);
  const tz = d.getTimezoneOffset() * 60_000;
  return new Date(d.getTime() - tz).toISOString().slice(0, 10);
}
