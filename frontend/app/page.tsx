import Link from "next/link";
import {
  Activity,
  ArrowRight,
  Brain,
  CheckCircle2,
  Receipt,
  Repeat,
  Sliders,
  TrendingDown,
} from "lucide-react";
import { Band } from "@/components/ui/band";
import { Button } from "@/components/ui/button";
import { TextLink } from "@/components/ui/text-link";
import { Logo } from "@/components/layout/logo";
import { HeroArt } from "@/components/landing/hero-art";
import { Reveal } from "@/components/motion/css-motion";
import "@/components/landing/landing.css";

const navLinks = [
  { label: "Features", href: "#features" },
  { label: "How it works", href: "#how-it-works" },
  { label: "Security", href: "#security" },
];

export default function Home() {
  return (
    <div data-landing className="min-h-screen bg-canvas">
      <Band tone="navy" space="none" bleed className="lp-hero">
        <div className="mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-12">
          <header className="flex min-h-bar items-center justify-between gap-sp-giant">
            <div className="flex items-center gap-sp-max">
              <Link href="/" className="flex min-h-11 items-center gap-sp-lg text-ty-button text-on-primary">
                <Logo />
                FlowSight
              </Link>
              <nav aria-label="Primary" className="hidden items-center gap-sp-giant md:flex">
                {navLinks.map((item) => (
                  <a
                    key={item.href}
                    href={item.href}
                    className="text-ty-nav-link text-on-primary/85 hover:text-on-primary hover:underline"
                  >
                    {item.label}
                  </a>
                ))}
              </nav>
            </div>
            <div className="flex items-center gap-sp-xl">
              <Link
                href="/auth/login"
                className="flex min-h-11 items-center text-ty-nav-link text-on-primary/85 hover:text-on-primary hover:underline"
              >
                Sign in
              </Link>
              <Button asChild size="default" className="bg-on-primary text-primary-deep hover:bg-band-blue">
                <Link href="/auth/register">Get started</Link>
              </Button>
            </div>
          </header>

          <div className="pb-sp-band pt-sp-hero lg:pb-sp-page lg:pt-sp-band">
            <h1 className="text-ty-hero-title text-on-primary md:text-ty-display">
              <span className="lp-line" style={{ "--i": 0 } as React.CSSProperties}>
                <span>Understand the decisions</span>
              </span>
              <span className="lp-line" style={{ "--i": 1 } as React.CSSProperties}>
                <span>behind your finances.</span>
              </span>
            </h1>
            <div className="mt-sp-giant grid items-center gap-sp-hero lg:mt-sp-max lg:grid-cols-[1.1fr_1fr]">
              <div>
                <p
                  className="lp-rise max-w-xl text-ty-body-lg text-on-primary/90 md:text-ty-lead"
                  style={{ "--i": 0 } as React.CSSProperties}
                >
                  FlowSight turns everyday financial activity into a clearer picture: the leaks worth recovering, the
                  consequences of the next decision, and the patterns your bank statement does not show.
                </p>
                <div
                  className="lp-rise mt-sp-max flex flex-wrap items-center gap-sp-wide"
                  style={{ "--i": 1 } as React.CSSProperties}
                >
                  <Button asChild size="lg" className="bg-on-primary text-primary-deep hover:bg-band-blue">
                    <Link href="/auth/register">
                      Get started
                      <ArrowRight />
                    </Link>
                  </Button>
                  <Button
                    asChild
                    size="lg"
                    variant="outline"
                    className="border-on-primary bg-transparent text-on-primary hover:bg-on-primary/10"
                  >
                    <a href="#how-it-works">See how it works</a>
                  </Button>
                </div>
              </div>
              <HeroArt />
            </div>
          </div>
        </div>
      </Band>

      <Band tone="canvas" id="features" space="xl">
        <Reveal wipe className="max-w-2xl">
          <h2 className="text-ty-section-title text-ink">A clearer layer on top of your finances</h2>
          <p className="mt-sp-wide text-ty-body-lg text-ink-lead">
            From everyday spending to a picture you can act on, in one place.
          </p>
        </Reveal>
        <ul className="mt-sp-hero grid gap-x-sp-hero gap-y-sp-hero sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature, i) => (
            <Reveal as="li" key={feature.title} delay={(i % 3) * 0.06}>
              <feature.icon className="h-7 w-7 text-primary" strokeWidth={1.5} aria-hidden="true" />
              <h3 className="mt-sp-wide text-ty-subhead text-ink">{feature.title}</h3>
              <p className="mt-sp-sm text-ty-body text-ink-list">{feature.description}</p>
            </Reveal>
          ))}
        </ul>
      </Band>

      <Band tone="lavender" curved id="how-it-works" space="xl">
        <Reveal wipe className="max-w-2xl">
          <h2 className="text-ty-section-title text-ink">From data to decisions in minutes</h2>
          <p className="mt-sp-wide text-ty-body-lg text-ink-lead">Three steps to a clearer view of your finances.</p>
        </Reveal>
        <ol className="mt-sp-hero grid gap-sp-hero md:grid-cols-3">
          {steps.map((step, i) => (
            <Reveal as="li" key={step.title} delay={i * 0.06}>
              <span className="text-ty-section-title text-primary" aria-hidden="true">
                {i + 1}
              </span>
              <h3 className="mt-sp-sm text-ty-subhead text-ink">{step.title}</h3>
              <p className="mt-sp-sm text-ty-body text-ink-list">{step.description}</p>
            </Reveal>
          ))}
        </ol>
      </Band>

      <Band tone="canvas" id="security" space="xl">
        <div className="grid gap-sp-hero lg:grid-cols-2 lg:gap-sp-page">
          <Reveal wipe>
            <h2 className="text-ty-section-title text-ink">Built around your privacy</h2>
            <p className="mt-sp-wide text-ty-body-lg text-ink-lead">
              FlowSight is private by design. We never ask for your banking login, never connect directly to your bank,
              and never share or sell your data.
            </p>
          </Reveal>
          <ul className="grid gap-sp-wide sm:grid-cols-2">
            {trustPoints.map((point, i) => (
              <Reveal
                as="li"
                key={point}
                delay={(i % 2) * 0.06}
                className="flex items-start gap-sp-lg text-ty-body text-ink-list"
              >
                <CheckCircle2 className="mt-1 h-5 w-5 shrink-0 text-positive" strokeWidth={1.75} aria-hidden="true" />
                {point}
              </Reveal>
            ))}
          </ul>
        </div>

        <Reveal className="mt-sp-page flex flex-wrap items-center justify-between gap-sp-giant">
          <div className="max-w-xl">
            <h2 className="text-ty-section-title text-ink">Ready to see your finances clearly?</h2>
            <p className="mt-sp-sm text-ty-body-lg text-ink-lead">Start free. No credit card needed.</p>
          </div>
          <Button asChild size="lg">
            <Link href="/auth/register">
              Create your account
              <ArrowRight />
            </Link>
          </Button>
        </Reveal>
      </Band>

      <Band as="footer" tone="band" space="md">
        <div className="flex flex-col items-start justify-between gap-sp-wide sm:flex-row sm:items-center">
          <div className="flex items-center gap-sp-lg text-ty-button text-ink">
            <Logo />
            FlowSight
          </div>
          <div className="flex flex-wrap items-center gap-x-sp-giant gap-y-sp-sm text-ty-caption">
            <TextLink href="/privacy">Privacy Policy</TextLink>
            <TextLink href="/terms">Terms of Service</TextLink>
            <span className="text-ink-meta">© 2026 FlowSight</span>
          </div>
        </div>
      </Band>

    </div>
  );
}

const features = [
  {
    icon: Activity,
    title: "Spending patterns",
    description: "Late-night splurges, weekend overruns, and lifestyle creep, spotted before they add up.",
  },
  {
    icon: TrendingDown,
    title: "Money worth recovering",
    description:
      "Duplicate subscriptions, creeping prices, small daily habits, and fees, with how much you would save by fixing each.",
  },
  {
    icon: Sliders,
    title: "Decisions, before you make them",
    description:
      "See the real cost of a big purchase: the monthly hit, how much it delays your savings, and how much breathing room you would give up.",
  },
  {
    icon: Receipt,
    title: "Receipts in a tap",
    description:
      "Snap a photo and we pull out the merchant, amount, and date. Check it over, then save it to your records.",
  },
  {
    icon: Repeat,
    title: "Subscriptions and bills",
    description: "Every recurring charge in one place, so price hikes and forgotten free trials never slip past you.",
  },
  {
    icon: Brain,
    title: "Suggestions made for you",
    description:
      "Practical tips grounded in how you actually spend and what you are saving toward, never generic advice.",
  },
];

const steps = [
  {
    title: "Add your spending",
    description: "Upload a bank statement, type in a transaction, or snap a receipt. We tidy up the rest.",
  },
  {
    title: "We make sense of it",
    description: "We sort every transaction, spot your patterns, and find the money worth recovering.",
  },
  {
    title: "Act on what matters",
    description: "See what you can save, try out a decision before you commit, and follow tips ranked by impact.",
  },
];

const trustPoints = [
  "Your sign-in stays protected with short-lived, secure sessions",
  "Passwords are encrypted, never stored as plain text",
  "We never connect directly to your bank",
  "Your data is never shared or sold",
  "Everything you enter is checked before it is saved",
  "You only ever see your own information",
];
