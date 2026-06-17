"use client";

import { useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, ArrowRight, CheckCircle2, Loader2, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/features/auth/api";
import { ApiError } from "@/lib/api";
import { FadeIn } from "@/components/motion/primitives";

const schema = z.object({
  email: z.string().email("Enter a valid email address"),
});

type FormValues = z.infer<typeof schema>;

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormValues) => {
    try {
      await authApi.forgotPassword(data.email);
      setSubmitted(data.email);
    } catch (err) {
      setError("root", {
        message:
          err instanceof ApiError
            ? err.message
            : "Something went wrong. Please try again in a moment.",
      });
    }
  };

  if (submitted) {
    return <SuccessState email={submitted} />;
  }

  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <header>
          <h1 className="text-[1.625rem] font-semibold tracking-tight text-slate-900">
            Forgot your password?
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Enter the email you signed up with and we will send you a link to set a new password.
          </p>
        </header>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          {errors.root && (
            <div className="rounded-lg border border-red-200 bg-red-50/70 px-3.5 py-2.5 text-sm text-red-700">
              {errors.root.message}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="email" className="text-[13px] font-medium text-slate-700">
              Email
            </Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@company.com"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
            {errors.email && <p className="text-xs text-red-600">{errors.email.message}</p>}
          </div>

          <Button type="submit" className="group w-full" disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" />
                Sending link
              </>
            ) : (
              <>
                Send reset link
                <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
              </>
            )}
          </Button>
        </form>

        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1.5 text-sm text-slate-500 underline-offset-4 transition-colors hover:text-slate-900 hover:underline"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to sign in
        </Link>
      </div>
    </FadeIn>
  );
}

function SuccessState({ email }: { email: string }) {
  return (
    <FadeIn delay={0.05}>
      <div className="space-y-8">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-emerald-50 text-emerald-700">
          <Mail className="h-5 w-5" strokeWidth={1.75} />
        </div>

        <header>
          <h1 className="text-[1.625rem] font-semibold tracking-tight text-slate-900">
            Check your inbox.
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-500">
            If an account exists for <span className="font-medium text-slate-700">{email}</span>,
            we have sent a link to reset your password. The link will be valid for 30 minutes and
            can be used once.
          </p>
        </header>

        <div className="rounded-lg border border-slate-200/80 bg-slate-50/60 px-4 py-3 text-xs leading-relaxed text-slate-500">
          <p className="flex items-start gap-2">
            <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" strokeWidth={2} />
            <span>
              No email? Check your spam folder, or wait a minute and request a new link.
            </span>
          </p>
        </div>

        <div className="flex items-center gap-3 text-sm">
          <Link
            href="/auth/login"
            className="text-slate-500 underline-offset-4 transition-colors hover:text-slate-900 hover:underline"
          >
            Back to sign in
          </Link>
          <span className="text-slate-300">·</span>
          <Link
            href="/auth/forgot-password"
            className="text-slate-500 underline-offset-4 transition-colors hover:text-slate-900 hover:underline"
          >
            Try a different email
          </Link>
        </div>
      </div>
    </FadeIn>
  );
}
