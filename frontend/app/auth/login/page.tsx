"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowRight, Eye, EyeOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/features/auth/api";
import { useAuthStore } from "@/store/auth";
import { ApiError } from "@/lib/api";
import { FadeIn } from "@/components/motion/primitives";

const schema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (data: FormValues) => {
    try {
      const response = await authApi.login(data);
      setAuth(response.token, response.user);
      router.push("/dashboard");
    } catch (err) {
      setError("root", {
        message:
          err instanceof ApiError
            ? err.message
            : "An unexpected error occurred. Please try again.",
      });
    }
  };

  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <header>
          <h1 className="text-[1.625rem] font-semibold tracking-tight text-slate-900">
            Welcome back.
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Pick up where you left off.
          </p>
        </header>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          {errors.root && (
            <div className="rounded-lg border border-red-200 bg-red-50/70 px-3.5 py-2.5 text-sm text-red-700">
              {errors.root.message}
            </div>
          )}

          <Field
            id="email"
            label="Email"
            error={errors.email?.message}
          >
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@company.com"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
          </Field>

          <Field
            id="password"
            label="Password"
            error={errors.password?.message}
            rightSlot={
              <Link
                href="/auth/forgot-password"
                className="text-xs text-slate-500 transition-colors hover:text-slate-900"
              >
                Forgot password?
              </Link>
            }
          >
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                placeholder="Enter your password"
                aria-invalid={!!errors.password}
                className="pr-10"
                {...register("password")}
              />
              <button
                type="button"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 transition-colors hover:text-slate-700"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </Field>

          <Button type="submit" className="group w-full" disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" />
                Signing in
              </>
            ) : (
              <>
                Sign in
                <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
              </>
            )}
          </Button>
        </form>

        <p className="text-sm text-slate-500">
          New to FlowSight?{" "}
          <Link
            href="/auth/register"
            className="font-medium text-slate-900 underline-offset-4 transition-colors hover:underline"
          >
            Create an account
          </Link>
        </p>
      </div>
    </FadeIn>
  );
}

function Field({
  id,
  label,
  error,
  rightSlot,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  rightSlot?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <Label htmlFor={id} className="text-[13px] font-medium text-slate-700">
          {label}
        </Label>
        {rightSlot}
      </div>
      {children}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
