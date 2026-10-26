import { api, ApiError } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { receiptMimeType } from "./capture-logic";
import type { Receipt, ReceiptConfirmPayload, ReceiptPage } from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class UploadTimeoutError extends Error {
  constructor() {
    super("Reading the receipt took too long.");
    this.name = "UploadTimeoutError";
  }
}

const bearer = (): Record<string, string> => {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// lib/api keeps its token refresh private; any authed call through it refreshes an
// expired token (or signs the user out), after which the raw request is retried once.
const refreshSession = () => api.get<ReceiptPage>("/api/v1/receipts?page=0&size=1", { auth: true });

function errorFrom(status: number, body: string): ApiError {
  let message = "Request failed";
  try {
    const parsed = JSON.parse(body) as { message?: string };
    if (parsed.message) message = parsed.message;
  } catch {
    // not JSON
  }
  return new ApiError(message, status);
}

export interface UploadOptions {
  signal?: AbortSignal;
  timeoutMs?: number;
  onProgress?: (loaded: number, total: number | null) => void;
  // the whole file has been sent; the server is reading it now
  onUploaded?: () => void;
}

function sendUpload(file: File, opts: UploadOptions): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE}/api/v1/receipts/upload`);
    for (const [k, v] of Object.entries(bearer())) xhr.setRequestHeader(k, v);
    if (opts.timeoutMs) xhr.timeout = opts.timeoutMs;
    xhr.upload.onprogress = (e) => opts.onProgress?.(e.loaded, e.lengthComputable ? e.total : null);
    xhr.upload.onload = () => opts.onUploaded?.();
    xhr.onload = () => resolve({ status: xhr.status, body: xhr.responseText });
    xhr.onerror = () => reject(new TypeError("Network error"));
    xhr.ontimeout = () => reject(new UploadTimeoutError());
    xhr.onabort = () => reject(new DOMException("Upload cancelled", "AbortError"));
    if (opts.signal) {
      if (opts.signal.aborted) return reject(new DOMException("Upload cancelled", "AbortError"));
      opts.signal.addEventListener("abort", () => xhr.abort(), { once: true });
    }
    const form = new FormData();
    const type = receiptMimeType(file);
    form.append("file", type && type !== file.type ? new File([file], file.name, { type }) : file);
    xhr.send(form);
  });
}

async function fetchImage(id: string, signal?: AbortSignal): Promise<Blob | null> {
  const get = () => fetch(`${API_BASE}/api/v1/receipts/${id}/image`, { headers: bearer(), signal, cache: "no-store" });
  let res = await get();
  if (res.status === 401) {
    await refreshSession();
    res = await get();
  }
  // expired, deleted, never kept or lost on redeploy: all the same to the person
  if (res.status === 404) return null;
  if (!res.ok) throw errorFrom(res.status, await res.text().catch(() => ""));
  return res.blob();
}

export const receiptApi = {
  /** Multipart upload with progress, cancellation and a timeout (XHR; fetch has no upload progress). */
  uploadWithProgress: async (file: File, opts: UploadOptions = {}): Promise<Receipt> => {
    let res = await sendUpload(file, opts);
    if (res.status === 401) {
      await refreshSession();
      res = await sendUpload(file, opts);
    }
    if (res.status < 200 || res.status >= 300) throw errorFrom(res.status, res.body);
    return JSON.parse(res.body) as Receipt;
  },

  /** The review copy as a Blob, or null when it is no longer available. Needs the bearer token, so no <img src>. */
  image: fetchImage,

  list: (page = 0, size = 20) =>
    api.get<ReceiptPage>(
      `/api/v1/receipts?page=${page}&size=${size}`,
      { auth: true }
    ),

  getById: (id: string) =>
    api.get<Receipt>(`/api/v1/receipts/${id}`, { auth: true }),

  confirm: (id: string, payload: ReceiptConfirmPayload) =>
    api.post<Receipt>(`/api/v1/receipts/${id}/confirm`, payload, { auth: true }),

  delete: (id: string) =>
    api.delete<void>(`/api/v1/receipts/${id}`, { auth: true }),
};
