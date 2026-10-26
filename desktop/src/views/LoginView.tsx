import { useState } from "react";
import { Activity, AlertCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError } from "~/lib/api";
import { authApi } from "~/api/auth";
import { useAuth } from "~/lib/auth-store";

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
      await setAuth(res.token, res.refreshToken, res.user);
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
    <div className="flex h-full flex-col overflow-hidden">
      <div data-tauri-drag-region className="drag-region shrink-0 bg-navy px-7 pb-8 pt-10 text-on-primary">
        <span className="flex h-10 w-10 items-center justify-center rounded-md bg-on-primary text-navy">
          <Activity className="h-5 w-5" strokeWidth={2.5} />
        </span>
        <h1 className="mt-4 text-ty-subhead font-medium text-on-primary">FlowSight</h1>
        <p className="mt-1 text-sm text-on-primary">Sign in to your companion</p>
      </div>
      <div className="flex flex-1 flex-col justify-center px-7 pb-8">
        <form onSubmit={submit} className="space-y-3">
          <Field label="Email">
            <Input
              type="email"
              autoComplete="username"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="no-drag"
            />
          </Field>
          <Field label="Password">
            <Input
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="no-drag"
            />
          </Field>
          {error && (
            <p role="alert" className="flex items-start gap-2 rounded-md bg-warning-soft px-3 py-2 text-xs text-warning">
              <AlertCircle className="mt-px h-4 w-4 shrink-0" aria-hidden="true" />
              {error}
            </p>
          )}
          <Button type="submit" disabled={loading} className="no-drag w-full">
            {loading && <Loader2 className="animate-spin" />}
            Sign in
          </Button>
        </form>

        <p className="mt-5 text-center text-xs text-muted-foreground">
          Uses the same account as the FlowSight web app.
        </p>
      </div>
    </div>
  );
}
