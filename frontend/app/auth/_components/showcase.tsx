"use client";

import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import {
  Lightbulb,
  Repeat,
  ShieldCheck,
  Sparkles,
  Telescope,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import {
  AmbientGlow,
  AnimatedSwitch,
  FadeIn,
  StaggerContainer,
  StaggerItem,
  motion,
} from "@/components/motion/primitives";

type Tone = "warn" | "neutral" | "positive";

type Insight = {
  icon: typeof TrendingUp;
  category: string;
  headline: string;
  metric: string;
  delta: { value: string; direction: "up" | "down" | "flat" };
  context: string;
  tone: Tone;
  // 24-point sparkline heights (0..1), hand-tuned
  sparkline: number[];
};

const LOGIN_INSIGHTS: Insight[] = [
  {
    icon: TrendingUp,
    category: "Food delivery",
    headline: "Food delivery is up this month",
    metric: "₹6,840",
    delta: { value: "+18%", direction: "up" },
    context: "Across 14 orders, mostly on weekends",
    tone: "warn",
    sparkline: [0.3, 0.35, 0.4, 0.32, 0.45, 0.5, 0.42, 0.55, 0.6, 0.5, 0.65, 0.7, 0.62, 0.75, 0.78, 0.7, 0.82, 0.85, 0.78, 0.9, 0.88, 0.92, 0.95, 1.0],
  },
  {
    icon: Repeat,
    category: "Subscriptions & bills",
    headline: "14% of monthly spend is recurring",
    metric: "₹11,240",
    delta: { value: "7 services", direction: "flat" },
    context: "Streaming, gym, two unused subscriptions",
    tone: "neutral",
    sparkline: [0.55, 0.58, 0.55, 0.6, 0.58, 0.62, 0.6, 0.64, 0.62, 0.66, 0.64, 0.68, 0.66, 0.7, 0.68, 0.72, 0.7, 0.74, 0.72, 0.76, 0.74, 0.78, 0.76, 0.8],
  },
  {
    icon: Telescope,
    category: "10-year projection",
    headline: "These habits could cost ₹2.8L annually",
    metric: "₹28,000",
    delta: { value: "per year", direction: "flat" },
    context: "If left unchanged at today's pace",
    tone: "neutral",
    sparkline: [0.2, 0.22, 0.25, 0.3, 0.32, 0.35, 0.4, 0.45, 0.48, 0.52, 0.55, 0.6, 0.64, 0.68, 0.72, 0.75, 0.78, 0.82, 0.85, 0.88, 0.91, 0.94, 0.97, 1.0],
  },
  {
    icon: TrendingDown,
    category: "Money worth recovering",
    headline: "₹4,200 looks recoverable in subscriptions",
    metric: "₹4,200",
    delta: { value: "-21%", direction: "down" },
    context: "Duplicate services and creeping prices",
    tone: "positive",
    sparkline: [0.95, 0.92, 0.9, 0.88, 0.85, 0.82, 0.8, 0.78, 0.75, 0.72, 0.7, 0.68, 0.66, 0.64, 0.62, 0.6, 0.58, 0.56, 0.54, 0.52, 0.5, 0.48, 0.46, 0.44],
  },
];

const SIGNUP_INSIGHTS: Insight[] = [
  {
    icon: Sparkles,
    category: "Spending patterns",
    headline: "See the patterns behind your spending",
    metric: "850+",
    delta: { value: "patterns", direction: "flat" },
    context: "Weekend overruns, lifestyle inflation, late-night habits",
    tone: "neutral",
    sparkline: [0.4, 0.45, 0.5, 0.48, 0.55, 0.6, 0.58, 0.62, 0.7, 0.68, 0.72, 0.78, 0.75, 0.82, 0.85, 0.8, 0.88, 0.92, 0.9, 0.94, 0.92, 0.96, 0.95, 1.0],
  },
  {
    icon: Repeat,
    category: "Subscriptions & bills",
    headline: "Find what you forgot you were paying for",
    metric: "₹11,240",
    delta: { value: "/ month", direction: "flat" },
    context: "Across an average of 7 active services",
    tone: "neutral",
    sparkline: [0.55, 0.58, 0.55, 0.6, 0.58, 0.62, 0.6, 0.64, 0.62, 0.66, 0.64, 0.68, 0.66, 0.7, 0.68, 0.72, 0.7, 0.74, 0.72, 0.76, 0.74, 0.78, 0.76, 0.8],
  },
  {
    icon: Lightbulb,
    category: "What-if planning",
    headline: "Model decisions before you make them",
    metric: "10y",
    delta: { value: "horizon", direction: "flat" },
    context: "Monthly hit, savings delay, breathing room",
    tone: "positive",
    sparkline: [0.3, 0.32, 0.35, 0.4, 0.42, 0.46, 0.5, 0.52, 0.55, 0.6, 0.62, 0.66, 0.7, 0.72, 0.76, 0.78, 0.82, 0.84, 0.86, 0.88, 0.9, 0.92, 0.94, 0.96],
  },
  {
    icon: TrendingDown,
    category: "Money worth recovering",
    headline: "Recover what's quietly slipping away",
    metric: "₹4,200",
    delta: { value: "found", direction: "down" },
    context: "Duplicate subs, creeping prices, small daily habits",
    tone: "positive",
    sparkline: [0.95, 0.92, 0.9, 0.88, 0.85, 0.82, 0.8, 0.78, 0.75, 0.72, 0.7, 0.68, 0.66, 0.64, 0.62, 0.6, 0.58, 0.56, 0.54, 0.52, 0.5, 0.48, 0.46, 0.44],
  },
];

const OUTCOMES = [
  "Understand your spending patterns",
  "Spot your subscriptions and bills",
  "Find money worth recovering",
  "Try a decision before you make it",
];

export function Showcase({ variant = "desktop" }: { variant?: "desktop" | "mobile" }) {
  const pathname = usePathname();
  const mode: "login" | "signup" = pathname?.includes("register") ? "signup" : "login";
  const insights = mode === "signup" ? SIGNUP_INSIGHTS : LOGIN_INSIGHTS;

  // rotate the featured insight every 4.6s
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setIdx((i) => (i + 1) % insights.length), 4600);
    return () => clearInterval(t);
  }, [insights.length]);

  if (variant === "mobile") {
    return <MobileStrip mode={mode} insight={insights[idx]} count={insights.length} activeIdx={idx} />;
  }

  return <DesktopPanel mode={mode} insight={insights[idx]} count={insights.length} activeIdx={idx} />;
}

function DesktopPanel({
  mode,
  insight,
  count,
  activeIdx,
}: {
  mode: "login" | "signup";
  insight: Insight;
  count: number;
  activeIdx: number;
}) {
  const hero =
    mode === "signup"
      ? {
          eyebrow: "Welcome to FlowSight",
          title: "A clearer view of where your money goes.",
          sub: "Sign up to see your spending patterns, the subscriptions you're paying for, and what a big decision would really cost, all on your own data.",
        }
      : {
          eyebrow: "Your money, made clear",
          title: "Understand the decisions behind your finances.",
          sub: "Welcome back. Your patterns, your trends, and what they add up to are right where you left them.",
        };

  return (
    <div className="relative flex h-full min-h-screen w-full flex-col overflow-hidden bg-[#f7f6f1] text-foreground">
      <BackgroundLayers />

      <div className="relative z-10 flex h-full flex-col px-12 py-12 xl:px-16 xl:py-14 2xl:px-20">
        <FadeIn>
          <Brand />
        </FadeIn>

        <div className="mt-16 max-w-[28rem]">
          <FadeIn delay={0.05}>
            <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
              {hero.eyebrow}
            </p>
          </FadeIn>
          <FadeIn delay={0.12}>
            <h2 className="mt-4 text-[2.5rem] font-semibold leading-[1.1] tracking-tight text-foreground xl:text-[2.75rem]">
              {hero.title}
            </h2>
          </FadeIn>
          <FadeIn delay={0.2}>
            <p className="mt-5 text-[15px] leading-relaxed text-muted-foreground">{hero.sub}</p>
          </FadeIn>
        </div>

        <FadeIn delay={0.3} className="mt-12">
          <FeaturedInsight insight={insight} />
          <InsightProgress count={count} activeIdx={activeIdx} />
        </FadeIn>

        <div className="mt-auto pt-10">
          <StaggerContainer className="grid grid-cols-2 gap-x-6 gap-y-3 max-w-[28rem]" stagger={0.05}>
            {OUTCOMES.map((label) => (
              <StaggerItem key={label}>
                <OutcomeRow label={label} />
              </StaggerItem>
            ))}
          </StaggerContainer>

          <FadeIn delay={0.5}>
            <div className="mt-10 flex items-center gap-2 text-[11px] text-muted-foreground/70">
              <ShieldCheck className="h-3.5 w-3.5" strokeWidth={1.75} />
              <span>
                FlowSight never connects to your bank. Your data is yours alone.
              </span>
            </div>
          </FadeIn>
        </div>
      </div>
    </div>
  );
}

function MobileStrip({
  mode,
  insight,
  count,
  activeIdx,
}: {
  mode: "login" | "signup";
  insight: Insight;
  count: number;
  activeIdx: number;
}) {
  const tagline =
    mode === "signup"
      ? "A clearer view of where your money goes."
      : "Understand the decisions behind your finances.";

  return (
    <div className="relative overflow-hidden bg-[#f7f6f1] px-6 pb-8 pt-8 text-foreground">
      <BackgroundLayers mobile />
      <div className="relative z-10">
        <FadeIn>
          <Brand compact />
        </FadeIn>
        <FadeIn delay={0.08}>
          <h2 className="mt-5 text-[1.5rem] font-semibold leading-[1.15] tracking-tight">
            {tagline}
          </h2>
        </FadeIn>
        <FadeIn delay={0.16} className="mt-5">
          <FeaturedInsight insight={insight} compact />
          <InsightProgress count={count} activeIdx={activeIdx} compact />
        </FadeIn>
      </div>
    </div>
  );
}

function Brand({ compact }: { compact?: boolean } = {}) {
  return (
    <div className="flex items-center gap-2.5">
      <Logo />
      <span className={compact ? "text-sm font-semibold tracking-tight text-foreground" : "text-[15px] font-semibold tracking-tight text-foreground"}>
        FlowSight
      </span>
    </div>
  );
}

function Logo() {
  return (
    <svg width="22" height="22" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <rect width="20" height="20" rx="5" fill="#0f172a" />
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

function FeaturedInsight({ insight, compact }: { insight: Insight; compact?: boolean }) {
  return (
    <AnimatedSwitch viewKey={insight.headline} className={compact ? undefined : "max-w-[28rem]"}>
      <InsightCard insight={insight} compact={compact} />
    </AnimatedSwitch>
  );
}

function InsightCard({ insight, compact }: { insight: Insight; compact?: boolean }) {
  const Icon = insight.icon;
  const toneAccent = useMemo(() => toneToAccent(insight.tone), [insight.tone]);
  const deltaTone = useMemo(() => toneToDelta(insight.tone, insight.delta.direction), [insight.tone, insight.delta.direction]);

  return (
    <motion.div
      className="relative overflow-hidden rounded-2xl border border-border/70 bg-card/80 p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04),0_8px_30px_-12px_rgba(15,23,42,0.08)] backdrop-blur-sm"
      whileHover={{ y: -2 }}
      transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
    >
      <div className="flex items-start gap-4">
        <div
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
          style={{ backgroundColor: toneAccent.bg }}
        >
          <Icon className="h-4 w-4" strokeWidth={1.75} style={{ color: toneAccent.fg }} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground/70">
            {insight.category}
          </p>
          <p className={compact ? "mt-0.5 text-sm font-medium leading-snug text-foreground" : "mt-0.5 text-[15px] font-medium leading-snug text-foreground"}>
            {insight.headline}
          </p>
        </div>
      </div>

      <div className="mt-5 flex items-end justify-between">
        <div>
          <p className="text-[1.75rem] font-semibold leading-none tracking-tight tabular-nums text-foreground">
            {insight.metric}
          </p>
          <p className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
            <span
              className="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[11px] font-medium tabular-nums"
              style={{ backgroundColor: deltaTone.bg, color: deltaTone.fg }}
            >
              {insight.delta.value}
            </span>
            <span className="text-muted-foreground/70">·</span>
            <span className="truncate">{insight.context}</span>
          </p>
        </div>

        {!compact && <Sparkline points={insight.sparkline} accent={toneAccent.fg} />}
      </div>

      {compact && (
        <div className="mt-4">
          <Sparkline points={insight.sparkline} accent={toneAccent.fg} compact />
        </div>
      )}
    </motion.div>
  );
}

function InsightProgress({
  count,
  activeIdx,
  compact,
}: {
  count: number;
  activeIdx: number;
  compact?: boolean;
}) {
  return (
    <div className={compact ? "mt-3 flex items-center gap-1.5" : "mt-4 flex items-center gap-1.5"}>
      {Array.from({ length: count }, (_, i) => (
        <span
          key={i}
          className="h-0.5 rounded-full transition-all duration-500"
          style={{
            width: i === activeIdx ? 18 : 8,
            backgroundColor: i === activeIdx ? "#0f172a" : "rgba(15,23,42,0.18)",
          }}
        />
      ))}
    </div>
  );
}

function Sparkline({
  points,
  accent,
  compact,
}: {
  points: number[];
  accent: string;
  compact?: boolean;
}) {
  const W = compact ? 240 : 150;
  const H = compact ? 36 : 44;
  const padding = 2;
  const innerW = W - padding * 2;
  const innerH = H - padding * 2;

  const stepX = innerW / (points.length - 1);
  const path = points
    .map((p, i) => {
      const x = padding + i * stepX;
      const y = padding + innerH - p * innerH;
      return `${i === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");

  // Area path: same as line plus close-back-to-baseline
  const areaPath = `${path} L ${(padding + innerW).toFixed(2)} ${(padding + innerH).toFixed(2)} L ${padding.toFixed(2)} ${(padding + innerH).toFixed(2)} Z`;

  const gradientId = useMemo(() => `sl-grad-${Math.random().toString(36).slice(2, 8)}`, []);

  return (
    <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} className="shrink-0">
      <defs>
        <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={accent} stopOpacity="0.18" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </linearGradient>
      </defs>
      <motion.path
        d={areaPath}
        fill={`url(#${gradientId})`}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1], delay: 0.1 }}
      />
      <motion.path
        d={path}
        fill="none"
        stroke={accent}
        strokeWidth={1.6}
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={{ pathLength: 0, opacity: 0 }}
        animate={{ pathLength: 1, opacity: 1 }}
        transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
      />
    </svg>
  );
}

function OutcomeRow({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2.5 text-[13px] text-muted-foreground">
      <span className="inline-flex h-1 w-1 shrink-0 rounded-full bg-muted-foreground/70" />
      <span>{label}</span>
    </div>
  );
}

function BackgroundLayers({ mobile }: { mobile?: boolean } = {}) {
  return (
    <>
      {/* Dot grid */}
      <div
        className="absolute inset-0 opacity-[0.35] [mask-image:radial-gradient(ellipse_at_top_right,black_30%,transparent_70%)]"
        style={{
          backgroundImage:
            "radial-gradient(rgba(15,23,42,0.12) 1px, transparent 1px)",
          backgroundSize: "22px 22px",
        }}
      />

      {/* Soft drifting glow */}
      <AmbientGlow />

      {/* Slow horizontal flow line (decorative, very subtle) */}
      {!mobile && <FlowLine />}
    </>
  );
}

function FlowLine() {
  return (
    <svg
      className="pointer-events-none absolute -left-20 top-1/2 h-[60%] w-[140%] opacity-[0.22]"
      viewBox="0 0 1200 600"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="flow-line-grad" x1="0" x2="1" y1="0" y2="0">
          <stop offset="0%" stopColor="#0f172a" stopOpacity="0" />
          <stop offset="50%" stopColor="#0f172a" stopOpacity="0.35" />
          <stop offset="100%" stopColor="#0f172a" stopOpacity="0" />
        </linearGradient>
      </defs>
      <motion.path
        d="M 0 350 C 200 280, 400 420, 600 320 S 1000 380, 1200 300"
        stroke="url(#flow-line-grad)"
        strokeWidth="1"
        fill="none"
        animate={{ x: [-12, 12, -12] }}
        transition={{ duration: 26, repeat: Infinity, ease: [0.45, 0, 0.55, 1] }}
      />
      <motion.path
        d="M 0 420 C 240 380, 460 460, 700 400 S 1020 460, 1200 420"
        stroke="url(#flow-line-grad)"
        strokeWidth="1"
        fill="none"
        animate={{ x: [10, -10, 10] }}
        transition={{ duration: 34, repeat: Infinity, ease: [0.45, 0, 0.55, 1] }}
      />
    </svg>
  );
}

function toneToAccent(tone: Tone): { fg: string; bg: string } {
  switch (tone) {
    case "warn":
      return { fg: "#b45309", bg: "rgba(245, 158, 11, 0.10)" };
    case "positive":
      return { fg: "#047857", bg: "rgba(16, 185, 129, 0.10)" };
    case "neutral":
    default:
      return { fg: "#0f172a", bg: "rgba(15, 23, 42, 0.06)" };
  }
}

function toneToDelta(tone: Tone, dir: "up" | "down" | "flat"): { fg: string; bg: string } {
  if (dir === "flat") return { fg: "#475569", bg: "rgba(15,23,42,0.05)" };
  if (tone === "warn") return { fg: "#b45309", bg: "rgba(245, 158, 11, 0.12)" };
  if (tone === "positive") return { fg: "#047857", bg: "rgba(16, 185, 129, 0.12)" };
  return { fg: "#475569", bg: "rgba(15,23,42,0.05)" };
}
