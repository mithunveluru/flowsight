// Client-side checks and helpers for receipt capture. No imports so the node test
// runner can load it.

// Mirrors ReceiptService: 5 MB and these types. The backend checks again.
export const MAX_RECEIPT_BYTES = 5 * 1024 * 1024;
export const RECEIPT_TYPES = ["image/jpeg", "image/png", "image/webp", "image/tiff", "image/bmp"] as const;
export const RECEIPT_ACCEPT = RECEIPT_TYPES.join(",");

const BY_EXTENSION: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  tif: "image/tiff",
  tiff: "image/tiff",
  bmp: "image/bmp",
};

// Generous: the backend runs up to three OCR passes and optional model calls.
export const UPLOAD_TIMEOUT_MS = 120_000;

/** The type the file will be sent as; some pickers report "" and only the name says what it is. */
export function receiptMimeType(file: { type: string; name: string }): string | null {
  if (file.type) return (RECEIPT_TYPES as readonly string[]).includes(file.type) ? file.type : null;
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return BY_EXTENSION[ext] ?? null;
}

/** A sentence for the person, or null when the file can be sent. */
export function validateReceiptFile(file: { type: string; name: string; size: number }): string | null {
  if (file.size === 0) return "That file is empty. Choose the photo again.";
  if (!receiptMimeType(file)) {
    const heic = /heic|heif/i.test(file.type) || /\.(heic|heif)$/i.test(file.name);
    return heic
      ? "HEIC photos are not supported yet. Take the photo from this page, or export it as JPEG."
      : "Choose a JPEG, PNG, WebP, TIFF or BMP image.";
  }
  if (file.size > MAX_RECEIPT_BYTES) {
    return `This image is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is 5 MB.`;
  }
  return null;
}

/** Whole percent for the progress bar; null while the total is unknown. */
export function progressPercent(loaded: number, total: number | null | undefined): number | null {
  if (!total || total <= 0) return null;
  return Math.max(0, Math.min(100, Math.round((loaded / total) * 100)));
}

/**
 * Owns at most one object URL at a time. Setting a new blob revokes the previous URL and
 * dispose() revokes the last one, so previews and fetched images never leak.
 */
export function createObjectUrlSlot(
  create: (blob: Blob) => string = (b) => URL.createObjectURL(b),
  revoke: (url: string) => void = (u) => URL.revokeObjectURL(u)
) {
  let current: string | null = null;
  return {
    set(blob: Blob | null): string | null {
      if (current) revoke(current);
      current = blob ? create(blob) : null;
      return current;
    },
    get: () => current,
    dispose() {
      if (current) revoke(current);
      current = null;
    },
  };
}
