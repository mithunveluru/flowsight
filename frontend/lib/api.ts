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

// Reads the JWT token from the Zustand persist store without requiring a React hook.
// Zustand's getState() is safe to call outside of components.
function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("flowsight-auth");
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { state?: { token?: string } };
    return parsed.state?.token ?? null;
  } catch {
    return null;
  }
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  auth?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = false, ...rest } = options;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (auth) {
    const token = getStoredToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: { ...headers, ...(rest.headers as Record<string, string> | undefined) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    // 401 means the JWT is expired or missing — clear local auth state and
    // redirect to login so pages don't silently render "Request failed".
    if (res.status === 401 && auth && typeof window !== "undefined") {
      try { localStorage.removeItem("flowsight-auth"); } catch { /* ignore */ }
      if (!window.location.pathname.startsWith("/auth/")) {
        window.location.replace("/auth/login");
      }
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

export const api = {
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
