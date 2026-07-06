import { getToken, getRefreshToken, setTokens, forceLogout } from "~/lib/auth-store";

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

// Single-flight refresh: concurrent 401s share one /auth/refresh call so the
// rotating token is only spent once (reusing a spent token revokes the session).
let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = getRefreshToken();
      if (!refreshToken) return false;
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return false;
        const data = (await res.json()) as { token: string; refreshToken: string };
        await setTokens(data.token, data.refreshToken);
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = true, ...rest } = options;

  const doSend = () => {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (auth) {
      const token = getToken();
      if (token) headers["Authorization"] = `Bearer ${token}`;
    }
    return fetch(`${API_BASE}${path}`, {
      ...rest,
      headers: { ...headers, ...(rest.headers as Record<string, string> | undefined) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  let res = await doSend();
  // expired access token: rotate the refresh token once and retry
  if (res.status === 401 && auth && (await tryRefresh())) {
    res = await doSend();
  }
  return handle<T>(res, auth);
}

async function handle<T>(res: Response, auth: boolean): Promise<T> {
  if (!res.ok) {
    // still 401 after any refresh attempt: drop the session, route to login
    if (res.status === 401 && auth) forceLogout();

    let errorBody: { message?: string; violations?: string[] } = {};
    try {
      errorBody = await res.json();
    } catch {
      // non-JSON error body
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

  // multipart upload; sets auth but lets the browser set the boundary
  upload: async <T>(path: string, form: FormData): Promise<T> => {
    const doSend = () => {
      const token = getToken();
      return fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: form,
      });
    };
    let res = await doSend();
    if (res.status === 401 && (await tryRefresh())) {
      res = await doSend();
    }
    return handle<T>(res, true);
  },
};
