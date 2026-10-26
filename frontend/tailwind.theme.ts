import type { Config } from "tailwindcss";

type Extend = NonNullable<NonNullable<Config["theme"]>["extend"]>;

const ch = (name: string) => `hsl(var(--${name}))`;

const role = (name: string, weight: string) =>
  [`var(--ty-${name})`, { lineHeight: `var(--ty-${name}-lh)`, fontWeight: weight }] as [
    string,
    { lineHeight: string; fontWeight: string },
  ];

const spacingKeys = [
  "xxs", "xs", "sm", "md", "lg", "xl", "xxl", "wide", "section",
  "giant", "max", "huge", "hero", "band", "page",
] as const;

/** Theme shared by the web app and the desktop companion. */
export const sharedTheme: { extend: Extend; borderRadius: Record<string, string> } = {
  // replaced, not extended: nothing rounder than 12px except dots and avatars
  borderRadius: {
    none: "var(--radius-none)",
    sm: "var(--radius-sm)",
    DEFAULT: "var(--radius-sm)",
    md: "var(--radius-md)",
    lg: "var(--radius-lg)",
    xl: "var(--radius-md)",
    "2xl": "var(--radius-lg)",
    "3xl": "var(--radius-lg)",
    full: "9999px",
  },
  extend: {
    colors: {
      canvas: ch("canvas"),
      chrome: ch("chrome"),
      band: { DEFAULT: ch("band"), blue: ch("band-blue"), lavender: ch("band-lavender") },
      navy: ch("navy"),
      sky: ch("sky"),
      hairline: ch("hairline"),
      "on-primary": ch("on-primary"),
      ink: {
        DEFAULT: ch("ink"),
        lead: ch("ink-lead"),
        body: ch("ink-body"),
        list: ch("ink-list"),
        meta: ch("ink-meta"),
        nav: ch("ink-nav"),
      },
      border: ch("border"),
      input: ch("input"),
      ring: ch("ring"),
      background: ch("background"),
      foreground: ch("foreground"),
      primary: {
        DEFAULT: ch("primary"),
        foreground: ch("primary-foreground"),
        deep: ch("primary-deep"),
        hover: ch("primary-hover"),
        pressed: ch("primary-pressed"),
        outline: ch("outline-foreground"),
      },
      secondary: { DEFAULT: ch("secondary"), foreground: ch("secondary-foreground") },
      destructive: { DEFAULT: ch("destructive"), foreground: ch("destructive-foreground") },
      muted: { DEFAULT: ch("muted"), foreground: ch("muted-foreground") },
      accent: { DEFAULT: ch("accent"), foreground: ch("accent-foreground") },
      popover: { DEFAULT: ch("popover"), foreground: ch("popover-foreground") },
      overlay: ch("overlay"),
      card: { DEFAULT: ch("card"), foreground: ch("card-foreground") },
      sidebar: {
        bg: ch("sidebar-bg"),
        fg: ch("sidebar-fg"),
        "fg-active": ch("sidebar-fg-active"),
        border: ch("sidebar-border"),
      },
      brand: { DEFAULT: ch("brand"), soft: ch("brand-soft"), foreground: ch("brand-foreground") },
      positive: { DEFAULT: ch("positive"), soft: ch("positive-soft") },
      caution: { DEFAULT: ch("caution"), soft: ch("caution-soft") },
      warning: { DEFAULT: ch("warning"), soft: ch("warning-soft") },
      signal: { DEFAULT: ch("signal"), soft: ch("signal-soft") },
    },
    boxShadow: {
      illustration: "var(--elev-illustration)",
      "elev-1": "var(--elev-1)",
      "elev-2": "var(--elev-2)",
      "elev-3": "var(--elev-3)",
      "card-rest": "var(--elev-card)",
      "card-hover": "var(--elev-card-hover)",
    },
    transitionDuration: {
      fast: "var(--duration-fast)",
      base: "var(--duration-base)",
      slow: "var(--duration-slow)",
      reveal: "var(--duration-reveal)",
    },
    transitionTimingFunction: {
      standard: "var(--ease-standard)",
      exit: "var(--ease-exit)",
      "out-quint": "var(--ease-out)",
      "in-out-soft": "var(--ease-in-out)",
    },
    fontSize: {
      // dense tier for tables and tool surfaces; the roles below are for headings and prose
      "2xs": ["0.6875rem", { lineHeight: "1rem" }],
      xs: ["0.75rem", { lineHeight: "1rem" }],
      sm: ["0.875rem", { lineHeight: "1.3125rem" }],
      base: ["1rem", { lineHeight: "1.5rem" }],
      "ty-display": role("display", "700"),
      "ty-hero-title": role("hero-title", "500"),
      "ty-section-title": role("section-title", "500"),
      "ty-lead": role("lead", "400"),
      "ty-subhead": role("subhead", "500"),
      "ty-body-lg": role("body-lg", "400"),
      "ty-body": role("body", "400"),
      "ty-button": role("button", "500"),
      "ty-link": role("link", "400"),
      "ty-nav-link": role("nav-link", "400"),
      "ty-list-item": role("list-item", "350"),
      "ty-caption": role("caption", "400"),
    },
    // four weights only; the other Tailwind names collapse onto them so older call sites stay on-system
    fontWeight: {
      thin: "350",
      extralight: "350",
      light: "350",
      book: "350",
      normal: "400",
      medium: "500",
      semibold: "500",
      bold: "700",
      extrabold: "700",
      black: "700",
    },
    fontFamily: {
      sans: ["var(--font-sans)", "system-ui", "sans-serif"],
      mono: ["var(--font-geist-mono)", "ui-monospace", "monospace"],
    },
    letterSpacing: {
      tighter: "0",
      tight: "0",
      normal: "0",
      wide: "0",
      wider: "0",
      widest: "0",
    },
    spacing: {
      ...Object.fromEntries(spacingKeys.map((k) => [`sp-${k}`, `var(--sp-${k})`])),
      sidebar: "248px",
      header: "var(--bar-height)",
      bar: "var(--bar-height)",
    },
    screens: { coarse: { raw: "(pointer: coarse)" } },
    minHeight: { hero: "450px" },
    keyframes: {
      "fade-in": { from: { opacity: "0" }, to: { opacity: "1" } },
      "slide-up": {
        from: { opacity: "0", transform: "translateY(6px)" },
        to: { opacity: "1", transform: "translateY(0)" },
      },
    },
    animation: {
      "fade-in": "fade-in var(--duration-base) var(--ease-standard)",
      "slide-up": "slide-up var(--duration-slow) var(--ease-standard)",
    },
  },
};
