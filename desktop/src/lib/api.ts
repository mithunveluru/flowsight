import { getToken, forceLogout } from "~/lib/auth-store";

// Same backend the web app uses — the backend stays the single source of truth.
export const API_BASE =
  (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly violations?: string[]
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  auth?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = true, ...rest } = options;

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: { ...headers, ...(rest.headers as Record<string, string> | undefined) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  return handle<T>(res, auth);
}

async function handle<T>(res: Response, auth: boolean): Promise<T> {
  if (!res.ok) {
    // Expired/invalid token — drop the session so the app routes back to login.
    if (res.status === 401 && auth) forceLogout();

    let errorBody: { message?: string; violations?: string[] } = {};
    try {
      errorBody = await res.json();
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(errorBody.message ?? "Request failed", res.status, errorBody.violations);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "GET" }),

  post: <T>(path: string, body: unknown, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "POST", body }),

  patch: <T>(path: string, body: unknown, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "PATCH", body }),

  delete: <T>(path: string, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "DELETE" }),

  /** Multipart upload (receipts, CSV) — sets auth but lets the browser set the boundary. */
  upload: async <T>(path: string, form: FormData): Promise<T> => {
    const token = getToken();
    const res = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    });
    return handle<T>(res, true);
  },
};
