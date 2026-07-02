import { useState } from "react";
import { Activity } from "lucide-react";
import { AmbientGlow } from "@/components/motion/primitives";
import { ApiError } from "~/lib/api";
import { authApi } from "~/api/auth";
import { useAuth } from "~/lib/auth-store";
import { Button, TextField } from "~/components/ui";

export function LoginView() {
  const setAuth = useAuth((s) => s.setAuth);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await authApi.login({ email: email.trim(), password });
      await setAuth(res.token, res.user);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? "Incorrect email or password."
          : err instanceof Error
            ? err.message
            : "Sign in failed."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex h-full flex-col overflow-hidden">
      <AmbientGlow />
      <div data-tauri-drag-region className="drag-region z-10 h-9 shrink-0" />
      <div className="relative z-10 flex flex-1 flex-col justify-center px-7 pb-10">
        <div className="mb-7 flex flex-col items-center gap-3 text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg ring-1 ring-border/60">
            <Activity className="h-5 w-5" strokeWidth={2.5} />
          </span>
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-foreground">FlowSight</h1>
            <p className="text-xs text-muted-foreground">Sign in to your companion</p>
          </div>
        </div>

        <form onSubmit={submit} className="space-y-3">
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {error && (
            <p
              className="rounded-lg px-3 py-2 text-xs"
              style={{ backgroundColor: "hsl(var(--warning-soft))", color: "hsl(var(--warning))" }}
            >
              {error}
            </p>
          )}
          <Button type="submit" loading={loading} className="w-full">
            Sign in
          </Button>
        </form>

        <p className="mt-5 text-center text-[11px] text-muted-foreground">
          Uses the same account as the FlowSight web app.
        </p>
      </div>
    </div>
  );
}
