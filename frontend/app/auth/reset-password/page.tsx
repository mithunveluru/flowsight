"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowRight,
  CheckCircle2,
  Eye,
  EyeOff,
  Loader2,
  ShieldAlert,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/features/auth/api";
import { ApiError } from "@/lib/api";
import { FadeIn } from "@/components/motion/primitives";

const schema = z
  .object({
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .regex(/[A-Z]/, "Must contain at least one uppercase letter")
      .regex(/[a-z]/, "Must contain at least one lowercase letter")
      .regex(/\d/, "Must contain at least one digit"),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, {
    message: "Passwords do not match",
    path: ["confirm"],
  });

type FormValues = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className="h-48" />}>
      <ResetPasswordForm />
    </Suspense>
  );
}

function ResetPasswordForm() {
  const search = useSearchParams();
  const token = search.get("token") ?? "";

  const [showPassword, setShowPassword] = useState(false);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const password = watch("password", "");

  const onSubmit = async (data: FormValues) => {
    if (!token) {
      setError("root", { message: "This reset link is missing its token. Request a new one." });
      return;
    }
    try {
      await authApi.resetPassword({ token, password: data.password });
      setDone(true);
    } catch (err) {
      setError("root", {
        message:
          err instanceof ApiError
            ? err.message
            : "Something went wrong. Please try again in a moment.",
      });
    }
  };

  if (!token) {
    return <InvalidLinkState reason="This reset link is missing its token." />;
  }

  if (done) {
    return <DoneState />;
  }

  const strength = getPasswordStrength(password);

  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <header>
          <h1 className="text-ty-section-title text-ink">
            Choose a new password.
          </h1>
          <p className="mt-sp-sm text-ty-link text-ink-meta">
            For your security, this link can only be used once and expires after 30 minutes.
          </p>
        </header>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          {errors.root && (
            <div className="rounded-md bg-warning-soft px-3.5 py-2.5 text-sm text-warning">
              {errors.root.message}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="password">
              New password
            </Label>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                placeholder="At least 8 characters"
                aria-invalid={!!errors.password}
                className="pr-10"
                {...register("password")}
              />
              <button
                type="button"
                className="absolute right-0 top-0 flex h-11 w-11 items-center justify-center text-ink-meta transition-colors hover:text-ink"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>

            {password.length > 0 && (
              <div className="mt-2 space-y-1">
                <div className="flex gap-1">
                  {[1, 2, 3, 4].map((level) => (
                    <div
                      key={level}
                      className={`h-1 flex-1 rounded-sm transition-colors duration-300 ${
                        strength.score >= level ? strength.color : "bg-border"
                      }`}
                    />
                  ))}
                </div>
                <p className="text-xs text-ink-meta">{strength.label}</p>
              </div>
            )}
            {errors.password && (
              <p className="text-xs text-warning">{errors.password.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirm">
              Confirm new password
            </Label>
            <Input
              id="confirm"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder="Re-enter the password"
              aria-invalid={!!errors.confirm}
              {...register("confirm")}
            />
            {errors.confirm && (
              <p className="text-xs text-warning">{errors.confirm.message}</p>
            )}
          </div>

          <Button type="submit" className="group w-full" disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" />
                Updating password
              </>
            ) : (
              <>
                Update password
                <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
              </>
            )}
          </Button>
        </form>
      </div>
    </FadeIn>
  );
}

function DoneState() {
  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <div className="flex h-11 w-11 items-center justify-center rounded-sm bg-positive-soft text-positive">
          <CheckCircle2 className="h-5 w-5" strokeWidth={1.75} />
        </div>
        <header>
          <h1 className="text-ty-section-title text-ink">
            Password updated.
          </h1>
          <p className="mt-sp-sm text-ty-link text-ink-meta">
            Your password has been changed. Sign in to continue where you left off.
          </p>
        </header>
        <Button asChild>
          <Link href="/auth/login">
          Sign in
          <ArrowRight />
          </Link>
        </Button>
      </div>
    </FadeIn>
  );
}

function InvalidLinkState({ reason }: { reason: string }) {
  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <div className="flex h-11 w-11 items-center justify-center rounded-sm bg-caution-soft text-caution">
          <ShieldAlert className="h-5 w-5" strokeWidth={1.75} />
        </div>
        <header>
          <h1 className="text-ty-section-title text-ink">
            That link will not work.
          </h1>
          <p className="mt-sp-sm text-ty-link text-ink-meta">{reason}</p>
        </header>
        <Button asChild>
          <Link href="/auth/forgot-password">
          Request a new link
          <ArrowRight />
          </Link>
        </Button>
      </div>
    </FadeIn>
  );
}

function getPasswordStrength(password: string): {
  score: number;
  label: string;
  color: string;
} {
  if (!password) return { score: 0, label: "", color: "" };
  let score = 0;
  if (password.length >= 8) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[a-z]/.test(password) && /\d/.test(password)) score++;
  if (password.length >= 12 && /[^a-zA-Z\d]/.test(password)) score++;

  if (score <= 1) return { score: 1, label: "Weak", color: "bg-warning/80" };
  if (score === 2) return { score: 2, label: "Fair", color: "bg-caution/80" };
  if (score === 3) return { score: 3, label: "Good", color: "bg-brand/80" };
  return { score: 4, label: "Strong", color: "bg-positive" };
}
