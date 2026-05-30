import Link from "next/link";
import {
  Activity,
  ArrowRight,
  BarChart3,
  Brain,
  Receipt,
  Shield,
  Sliders,
  MessageSquare,
  TrendingDown,
  CheckCircle2,
} from "lucide-react";

export default function Home() {
  return (
    <div className="min-h-screen bg-white">
      {/* Navigation */}
      <header className="sticky top-0 z-50 border-b border-slate-200 bg-white/95 backdrop-blur-sm">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-6">
          <div className="flex items-center gap-8">
            <Link href="/" className="flex items-center gap-2.5">
              <Logo />
              <span className="text-sm font-semibold tracking-tight text-slate-900">
                FlowSight
              </span>
            </Link>
            <nav className="hidden items-center gap-6 md:flex">
              {[
                { label: "Features", href: "#features" },
                { label: "How it works", href: "#how-it-works" },
                { label: "Security", href: "#security" },
              ].map((item) => (
                <a
                  key={item.label}
                  href={item.href}
                  className="text-sm text-slate-500 transition-colors hover:text-slate-900"
                >
                  {item.label}
                </a>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <Link
              href="/auth/login"
              className="text-sm text-slate-600 transition-colors hover:text-slate-900"
            >
              Sign in
            </Link>
            <Link
              href="/auth/register"
              className="inline-flex h-8 items-center rounded-md bg-slate-900 px-4 text-sm font-medium text-white transition-colors hover:bg-slate-700"
            >
              Get started
            </Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="mx-auto max-w-7xl px-6 pb-20 pt-16">
        <div className="max-w-2xl">
          <div className="mb-5 inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-3 py-1">
            <span className="h-1.5 w-1.5 rounded-full bg-blue-600" />
            <span className="text-xs font-medium text-blue-700">
              Financial intelligence, for individuals
            </span>
          </div>
          <h1 className="mb-5 text-[2.75rem] font-semibold leading-[1.15] tracking-tight text-slate-900">
            Understand the decisions
            <br />
            behind your finances.
          </h1>
          <p className="mb-8 text-lg leading-relaxed text-slate-500">
            FlowSight turns everyday financial activity into a clearer picture: the leaks worth recovering, the consequences of the next decision, and the patterns your bank statement does not show.
          </p>
          <div className="flex items-center gap-3">
            <Link
              href="/auth/register"
              className="inline-flex h-10 items-center gap-2 rounded-md bg-slate-900 px-5 text-sm font-medium text-white transition-colors hover:bg-slate-700"
            >
              Get started
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
            <a
              href="#how-it-works"
              className="inline-flex h-10 items-center rounded-md border border-slate-200 bg-white px-5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              See how it works
            </a>
          </div>
        </div>
      </section>

      {/* Stats strip */}
      <section className="border-y border-slate-200 bg-slate-50">
        <div className="mx-auto max-w-7xl px-6 py-10">
          <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
            {stats.map((stat) => (
              <div key={stat.label}>
                <div className="text-2xl font-semibold tabular-nums text-slate-900">
                  {stat.value}
                </div>
                <div className="mt-0.5 text-sm text-slate-500">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="mx-auto max-w-7xl px-6 py-20">
        <div className="mb-10">
          <h2 className="text-2xl font-semibold tracking-tight text-slate-900">
            A clearer layer on top of your finances
          </h2>
          <p className="mt-2 text-slate-500">
            From raw transactions to meaningful observation, in one place.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {features.map((feature) => (
            <div
              key={feature.title}
              className="rounded-lg border border-slate-200 bg-white p-6"
            >
              <div className="mb-4 flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-slate-50 text-slate-600">
                <feature.icon className="h-4 w-4" />
              </div>
              <h3 className="mb-1.5 text-sm font-semibold text-slate-900">
                {feature.title}
              </h3>
              <p className="text-sm leading-relaxed text-slate-500">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="border-y border-slate-200 bg-slate-50">
        <div className="mx-auto max-w-7xl px-6 py-20">
          <div className="mb-10">
            <h2 className="text-2xl font-semibold tracking-tight text-slate-900">
              From data to decisions in minutes
            </h2>
            <p className="mt-2 text-slate-500">
              Three steps to a clearer view of your finances.
            </p>
          </div>
          <div className="grid gap-8 md:grid-cols-3">
            {steps.map((step, i) => (
              <div key={step.title} className="relative">
                <div className="mb-4 flex h-8 w-8 items-center justify-center rounded-full border border-slate-300 bg-white text-xs font-semibold text-slate-700">
                  {i + 1}
                </div>
                {i < steps.length - 1 && (
                  <div className="absolute left-4 top-4 hidden h-px w-full -translate-y-0.5 bg-slate-200 md:block" />
                )}
                <h3 className="mb-2 text-sm font-semibold text-slate-900">
                  {step.title}
                </h3>
                <p className="text-sm leading-relaxed text-slate-500">
                  {step.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Trust / Security */}
      <section id="security" className="mx-auto max-w-7xl px-6 py-20">
        <div className="flex flex-col gap-10 md:flex-row md:items-start md:gap-20">
          <div className="md:w-1/2">
            <h2 className="mb-3 text-2xl font-semibold tracking-tight text-slate-900">
              Built around your privacy
            </h2>
            <p className="text-slate-500 leading-relaxed">
              FlowSight is privacy-first by design. We do not store banking credentials, connect directly to financial institutions, or expose raw entity data through the API.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-3 md:w-1/2 sm:grid-cols-2">
            {trustPoints.map((point) => (
              <div key={point} className="flex items-start gap-2.5 text-sm">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                <span className="text-slate-700">{point}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA section */}
      <section className="bg-slate-900">
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="max-w-xl">
            <h2 className="mb-3 text-2xl font-semibold tracking-tight text-white">
              Ready to see your finances clearly?
            </h2>
            <p className="mb-8 text-slate-400">
              Start free. No credit card required. Full access to every product feature.
            </p>
            <Link
              href="/auth/register"
              className="inline-flex h-10 items-center gap-2 rounded-md bg-white px-5 text-sm font-medium text-slate-900 transition-colors hover:bg-slate-100"
            >
              Create your account
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-6 py-8">
          <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
            <div className="flex items-center gap-2.5">
              <Logo />
              <span className="text-sm font-semibold text-slate-900">FlowSight</span>
            </div>
            <div className="flex items-center gap-6 text-sm text-slate-400">
              <span>Privacy Policy</span>
              <span>Terms of Service</span>
              <span>© 2026 FlowSight</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* ─── Data ─────────────────────────────────────────────────── */

const stats = [
  { value: "94.6%", label: "Categorization accuracy" },
  { value: "850+", label: "Patterns detected" },
  { value: "2,400+", label: "People tracking with FlowSight" },
  { value: "<2s", label: "Per-transaction analysis" },
];

const features = [
  {
    icon: Activity,
    title: "Behavioral patterns",
    description:
      "Late-night spending, weekend overruns, and lifestyle inflation, surfaced before they compound.",
  },
  {
    icon: TrendingDown,
    title: "Recoverable spending",
    description:
      "Duplicate subscriptions, price creep, silent drains, and bank fees, each with a confidence score.",
  },
  {
    icon: Sliders,
    title: "Decision simulation",
    description:
      "The real cost of a purchase, modeled out: EMI impact, savings delay, and how much flexibility you would give up.",
  },
  {
    icon: Receipt,
    title: "Receipt analysis",
    description:
      "Capture merchant, amount, and date from a photo. Review before saving, then categorized into your ledger.",
  },
  {
    icon: MessageSquare,
    title: "Bank SMS parsing",
    description:
      "Reconstructs a complete financial timeline from transactional SMS, with merchant resolution and confidence scoring.",
  },
  {
    icon: Brain,
    title: "Tailored recommendations",
    description:
      "Optimization suggestions grounded in your actual spending patterns, behavioral triggers, and goals.",
  },
];

const steps = [
  {
    title: "Bring your data in",
    description:
      "Upload a CSV, scan a receipt, or import bank SMS. FlowSight normalizes the rest.",
  },
  {
    title: "We do the analysis",
    description:
      "Patterns, anomalies, and confidence scores get applied across every transaction.",
  },
  {
    title: "Act on what matters",
    description:
      "Review what is recoverable, simulate decisions before you make them, and follow recommendations ranked by impact.",
  },
];

const trustPoints = [
  "Modern session handling, never long-lived tokens in storage",
  "Passwords salted and hashed, never stored in the clear",
  "No direct connection to your bank or financial institutions",
  "Privacy-first API surface, no raw records in responses",
  "Validated input at every request boundary",
  "Strict separation between storage and what is returned",
];

/* ─── Logo mark ─────────────────────────────────────────────── */

function Logo() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
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
