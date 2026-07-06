import Link from "next/link";
import {
  Activity,
  ArrowRight,
  BarChart3,
  Brain,
  Receipt,
  Shield,
  Sliders,
  Repeat,
  TrendingDown,
  CheckCircle2,
} from "lucide-react";
import { FadeIn, RevealOnScroll } from "@/components/motion/primitives";

export default function Home() {
  return (
    <div className="min-h-screen bg-card">
      {/* Navigation */}
      <header className="sticky top-0 z-50 border-b border-border bg-card/95 backdrop-blur-sm">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-6">
          <div className="flex items-center gap-8">
            <Link href="/" className="flex items-center gap-2.5">
              <Logo />
              <span className="text-sm font-semibold tracking-tight text-foreground">
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
                  className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                >
                  {item.label}
                </a>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <Link
              href="/auth/login"
              className="text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              Sign in
            </Link>
            <Link
              href="/auth/register"
              className="inline-flex h-8 items-center rounded-md bg-primary px-4 text-sm font-medium text-white transition-colors hover:bg-primary/85"
            >
              Get started
            </Link>
          </div>
        </div>
      </header>

      {/* Hero — layered depth: faint dot grid + one soft brand tint, masked
          toward the copy so the texture never fights the type. */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage:
              "radial-gradient(hsl(var(--border)) 1px, transparent 1px)",
            backgroundSize: "22px 22px",
            maskImage:
              "radial-gradient(65% 90% at 70% 10%, black 30%, transparent 75%)",
            WebkitMaskImage:
              "radial-gradient(65% 90% at 70% 10%, black 30%, transparent 75%)",
          }}
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-40 -top-40 h-[480px] w-[480px] rounded-full"
          style={{
            background:
              "radial-gradient(50% 50% at 50% 50%, hsl(var(--brand) / 0.07), transparent 70%)",
          }}
        />
        <div className="relative mx-auto max-w-7xl px-6 pb-20 pt-16">
          <div className="max-w-2xl">
            <FadeIn>
              <h1 className="mb-5 text-[2.75rem] font-semibold leading-[1.15] tracking-tight text-foreground">
                Understand the decisions
                <br />
                behind your finances.
              </h1>
            </FadeIn>
            <FadeIn delay={0.08}>
              <p className="mb-8 text-lg leading-relaxed text-muted-foreground">
                FlowSight turns everyday financial activity into a clearer picture: the leaks worth recovering, the consequences of the next decision, and the patterns your bank statement does not show.
              </p>
            </FadeIn>
            <FadeIn delay={0.16}>
              <div className="flex items-center gap-3">
                <Link
                  href="/auth/register"
                  className="group inline-flex h-10 items-center gap-2 rounded-md bg-primary px-5 text-sm font-medium text-white transition-colors hover:bg-primary/85"
                >
                  Get started
                  <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                </Link>
                <a
                  href="#how-it-works"
                  className="inline-flex h-10 items-center rounded-md border border-border bg-card px-5 text-sm font-medium text-foreground/80 transition-colors hover:bg-muted/50"
                >
                  See how it works
                </a>
              </div>
            </FadeIn>
          </div>
        </div>
      </section>

      {/* Stats strip */}
      <section className="border-y border-border bg-muted/50">
        <div className="mx-auto max-w-7xl px-6 py-10">
          <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
            {stats.map((stat) => (
              <div key={stat.label}>
                <div className="text-2xl font-semibold tabular-nums text-foreground">
                  {stat.value}
                </div>
                <div className="mt-0.5 text-sm text-muted-foreground">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="mx-auto max-w-7xl px-6 py-20">
        <div className="mb-10">
          <h2 className="text-2xl font-semibold tracking-tight text-foreground">
            A clearer layer on top of your finances
          </h2>
          <p className="mt-2 text-muted-foreground">
            From everyday spending to a picture you can actually act on, in one place.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {features.map((feature, i) => (
            <RevealOnScroll key={feature.title} delay={(i % 3) * 0.06}>
              <div className="card-tactile h-full p-6">
                <div className="mb-4 flex h-8 w-8 items-center justify-center rounded-md border border-border bg-muted/50 text-muted-foreground">
                  <feature.icon className="h-4 w-4" />
                </div>
                <h3 className="mb-1.5 text-sm font-semibold text-foreground">
                  {feature.title}
                </h3>
                <p className="text-sm leading-relaxed text-muted-foreground">
                  {feature.description}
                </p>
              </div>
            </RevealOnScroll>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="border-y border-border bg-muted/50">
        <div className="mx-auto max-w-7xl px-6 py-20">
          <div className="mb-10">
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">
              From data to decisions in minutes
            </h2>
            <p className="mt-2 text-muted-foreground">
              Three steps to a clearer view of your finances.
            </p>
          </div>
          <div className="grid gap-8 md:grid-cols-3">
            {steps.map((step, i) => (
              <RevealOnScroll key={step.title} delay={i * 0.08}>
                <div className="relative">
                  <div className="mb-4 flex h-8 w-8 items-center justify-center rounded-full border border-muted-foreground/30 bg-card text-xs font-semibold text-foreground/80">
                    {i + 1}
                  </div>
                  {i < steps.length - 1 && (
                    <div className="absolute left-4 top-4 hidden h-px w-full -translate-y-0.5 bg-border md:block" />
                  )}
                  <h3 className="mb-2 text-sm font-semibold text-foreground">
                    {step.title}
                  </h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {step.description}
                  </p>
                </div>
              </RevealOnScroll>
            ))}
          </div>
        </div>
      </section>

      {/* Trust / Security */}
      <section id="security" className="mx-auto max-w-7xl px-6 py-20">
        <div className="flex flex-col gap-10 md:flex-row md:items-start md:gap-20">
          <div className="md:w-1/2">
            <h2 className="mb-3 text-2xl font-semibold tracking-tight text-foreground">
              Built around your privacy
            </h2>
            <p className="text-muted-foreground leading-relaxed">
              FlowSight is private by design. We never ask for your banking login, never connect directly to your bank, and never share or sell your data.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-3 md:w-1/2 sm:grid-cols-2">
            {trustPoints.map((point) => (
              <div key={point} className="flex items-start gap-2.5 text-sm">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-positive" />
                <span className="text-foreground/80">{point}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA section */}
      <section className="bg-primary">
        <div className="mx-auto max-w-7xl px-6 py-16">
          <div className="max-w-xl">
            <h2 className="mb-3 text-2xl font-semibold tracking-tight text-white">
              Ready to see your finances clearly?
            </h2>
            <p className="mb-8 text-white/60">
              Start free. No credit card needed. Everything FlowSight does, included.
            </p>
            <Link
              href="/auth/register"
              className="inline-flex h-10 items-center gap-2 rounded-md bg-card px-5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              Create your account
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border bg-card">
        <div className="mx-auto max-w-7xl px-6 py-8">
          <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
            <div className="flex items-center gap-2.5">
              <Logo />
              <span className="text-sm font-semibold text-foreground">FlowSight</span>
            </div>
            <div className="flex items-center gap-6 text-sm text-muted-foreground/70">
              <Link href="/privacy" className="transition-colors hover:text-foreground">
                Privacy Policy
              </Link>
              <Link href="/terms" className="transition-colors hover:text-foreground">
                Terms of Service
              </Link>
              <span>© 2026 FlowSight</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

// Data

const stats = [
  { value: "94.6%", label: "Categorization accuracy" },
  { value: "850+", label: "Patterns detected" },
  { value: "2,400+", label: "People tracking with FlowSight" },
  { value: "<2s", label: "Per-transaction analysis" },
];

const features = [
  {
    icon: Activity,
    title: "Spending patterns",
    description:
      "Late-night splurges, weekend overruns, and lifestyle creep, spotted before they add up.",
  },
  {
    icon: TrendingDown,
    title: "Money worth recovering",
    description:
      "Duplicate subscriptions, creeping prices, small daily habits, and fees, with how much you'd save by fixing each.",
  },
  {
    icon: Sliders,
    title: "Decisions, before you make them",
    description:
      "See the real cost of a big purchase: the monthly hit, how much it delays your savings, and how much breathing room you'd give up.",
  },
  {
    icon: Receipt,
    title: "Receipts in a tap",
    description:
      "Snap a photo and we pull out the merchant, amount, and date. Check it over, then save it to your records.",
  },
  {
    icon: Repeat,
    title: "Subscriptions & bills",
    description:
      "Every recurring charge in one place, so price hikes and forgotten free trials never slip past you.",
  },
  {
    icon: Brain,
    title: "Suggestions made for you",
    description:
      "Practical tips grounded in how you actually spend and what you're saving toward, never generic advice.",
  },
];

const steps = [
  {
    title: "Add your spending",
    description:
      "Upload a bank statement, type in a transaction, or snap a receipt. We tidy up the rest.",
  },
  {
    title: "We make sense of it",
    description:
      "We sort every transaction, spot your patterns, and find the money worth recovering.",
  },
  {
    title: "Act on what matters",
    description:
      "See what you can save, try out a decision before you commit, and follow tips ranked by impact.",
  },
];

const trustPoints = [
  "Your sign-in stays protected with short-lived, secure sessions",
  "Passwords are encrypted, never stored as plain text",
  "We never connect directly to your bank",
  "Your data is never shared or sold",
  "Everything you enter is checked before it's saved",
  "You only ever see your own information",
];

// Logo mark

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
