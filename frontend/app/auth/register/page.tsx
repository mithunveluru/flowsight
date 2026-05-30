"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/features/auth/api";
import { useAuthStore } from "@/store/auth";
import { ApiError } from "@/lib/api";

const schema = z.object({
  fullName: z
    .string()
    .min(2, "Name must be at least 2 characters")
    .max(100, "Name must not exceed 100 characters"),
  email: z.string().email("Enter a valid email address"),
  password: z
    .string()
    .min(8, "Password must be at least 8 characters")
    .regex(/[A-Z]/, "Must contain at least one uppercase letter")
    .regex(/[a-z]/, "Must contain at least one lowercase letter")
    .regex(/\d/, "Must contain at least one digit"),
});

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const password = watch("password", "");

  const onSubmit = async (data: FormValues) => {
    try {
      const response = await authApi.register(data);
      setAuth(response.token, response.user);
      router.push("/dashboard");
    } catch (err) {
      if (err instanceof ApiError && err.violations?.length) {
        // Map backend validation errors to form fields
        err.violations.forEach((v) => {
          const [field, ...rest] = v.split(": ");
          setError(field as keyof FormValues, { message: rest.join(": ") });
        });
      } else {
        setError("root", {
          message:
            err instanceof ApiError
              ? err.message
              : "An unexpected error occurred. Please try again.",
        });
      }
    }
  };

  const passwordStrength = getPasswordStrength(password);

  return (
    <div className="w-full max-w-sm">
      {/* Logo */}
      <div className="mb-8 flex flex-col items-center text-center">
        <Logo />
        <span className="mt-3 text-sm font-semibold text-slate-900">FlowSight</span>
      </div>

      {/* Card */}
      <div className="rounded-lg border border-slate-200 bg-white p-8">
        <div className="mb-6">
          <h1 className="text-lg font-semibold text-slate-900">Create your account</h1>
          <p className="mt-1 text-sm text-slate-500">
            Free to start. No credit card required.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          {/* Root error */}
          {errors.root && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
              {errors.root.message}
            </div>
          )}

          {/* Full name */}
          <div className="space-y-1.5">
            <Label htmlFor="fullName">Full name</Label>
            <Input
              id="fullName"
              type="text"
              autoComplete="name"
              placeholder="Jane Smith"
              aria-invalid={!!errors.fullName}
              {...register("fullName")}
            />
            {errors.fullName && (
              <p className="text-xs text-red-600">{errors.fullName.message}</p>
            )}
          </div>

          {/* Email */}
          <div className="space-y-1.5">
            <Label htmlFor="email">Work email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="jane@company.com"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
            {errors.email && (
              <p className="text-xs text-red-600">{errors.email.message}</p>
            )}
          </div>

          {/* Password */}
          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                placeholder="••••••••"
                aria-invalid={!!errors.password}
                className="pr-9"
                {...register("password")}
              />
              <button
                type="button"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
            {/* Password strength indicator */}
            {password.length > 0 && (
              <div className="space-y-1">
                <div className="flex gap-1">
                  {[1, 2, 3, 4].map((level) => (
                    <div
                      key={level}
                      className={`h-1 flex-1 rounded-full transition-colors ${
                        passwordStrength.score >= level
                          ? passwordStrength.color
                          : "bg-slate-200"
                      }`}
                    />
                  ))}
                </div>
                <p className="text-xs text-slate-500">
                  {passwordStrength.label}
                </p>
              </div>
            )}
            {errors.password && (
              <p className="text-xs text-red-600">{errors.password.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="animate-spin" />}
            {isSubmitting ? "Creating account..." : "Create account"}
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-slate-500">
          Already have an account?{" "}
          <Link
            href="/auth/login"
            className="font-medium text-slate-900 hover:underline"
          >
            Sign in
          </Link>
        </p>
      </div>

      <p className="mt-5 text-center text-xs text-slate-400">
        By creating an account you agree to our Terms of Service and Privacy Policy.
      </p>
    </div>
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

  if (score <= 1) return { score: 1, label: "Weak", color: "bg-red-400" };
  if (score === 2) return { score: 2, label: "Fair", color: "bg-amber-400" };
  if (score === 3) return { score: 3, label: "Good", color: "bg-blue-400" };
  return { score: 4, label: "Strong", color: "bg-emerald-500" };
}

function Logo() {
  return (
    <svg width="32" height="32" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <rect width="20" height="20" rx="4" fill="#0f172a" />
      <path
        d="M5 13.5L8.5 9.5L11.5 12L15 7"
        stroke="white"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="15" cy="7" r="1.25" fill="white" />
    </svg>
  );
}
