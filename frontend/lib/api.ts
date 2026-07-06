import { useAuthStore } from "@/store/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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
// rotating token is only spent once (a second exchange of the same token is
// treated as theft by the backend and revokes the session).
let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const { refreshToken, setAuth } = useAuthStore.getState();
      if (!refreshToken) return false;
      try {
        const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return false;
        const data = (await res.json()) as {
          token: string;
          refreshToken: string;
          user: Parameters<typeof setAuth>[2];
        };
        setAuth(data.token, data.refreshToken, data.user);
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

function logoutLocally() {
  useAuthStore.getState().clearAuth();
  if (
    typeof window !== "undefined" &&
    !window.location.pathname.startsWith("/auth/")
  ) {
    window.location.replace("/auth/login");
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = false, ...rest } = options;
  const res = await send(path, body, auth, rest);

  // Expired access token: rotate the refresh token once and retry the request.
  // If that fails too, the session is over — clear state and route to login.
  if (res.status === 401 && auth) {
    if (await tryRefresh()) {
      const retried = await send(path, body, auth, rest);
      return handle<T>(retried, auth);
    }
  }

  return handle<T>(res, auth);
}

async function send(
  path: string,
  body: unknown,
  auth: boolean,
  rest: Omit<RequestInit, "body">
): Promise<Response> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (auth) {
    const token = useAuthStore.getState().token;
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }
  return fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: { ...headers, ...(rest.headers as Record<string, string> | undefined) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

async function handle<T>(res: Response, auth: boolean): Promise<T> {
  if (!res.ok) {
    // still 401 after any refresh attempt: the session is over
    if (res.status === 401 && auth) {
      logoutLocally();
    }
    let errorBody: { message?: string; violations?: string[] } = {};
    try {
      errorBody = await res.json();
    } catch {
      // non-JSON error body
    }
    throw new ApiError(
      errorBody.message ?? "Request failed",
      res.status,
      errorBody.violations
    );
  }

  // Handle 204 No Content
  if (res.status === 204) return undefined as T;

  return res.json() as Promise<T>;
}

// Multipart upload with the same auth + refresh-and-retry behavior as request();
// the browser sets the multipart boundary, so no Content-Type header here.
async function upload<T>(path: string, form: FormData): Promise<T> {
  const doSend = () => {
    const token = useAuthStore.getState().token;
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
}

export const api = {
  upload,

  get: <T>(path: string, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "GET" }),

  post: <T>(path: string, body: unknown, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "POST", body }),

  put: <T>(path: string, body: unknown, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "PUT", body }),

  patch: <T>(path: string, body: unknown, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "PATCH", body }),

  delete: <T>(path: string, options?: Omit<RequestOptions, "body" | "method">) =>
    request<T>(path, { ...options, method: "DELETE" }),
};
