import { useEffect, useState } from "react";
import { useRefresh } from "~/lib/refresh";

interface AsyncResult<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

// Runs fn on mount, on deps change, and on the global refresh key; keeps last data during refresh.
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []): AsyncResult<T> {
  const refreshKey = useRefresh((s) => s.key);
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fn()
      .then((res) => {
        if (cancelled) return;
        setData(res);
        setError(null);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Something went wrong");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, refreshKey]);

  return { data, error, loading };
}
