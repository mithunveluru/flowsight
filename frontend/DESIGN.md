# FlowSight design system

Flat, banded and quiet. White canvas, full-width flat bands for separation, one blue that acts, a slate text ladder. Depth comes from band colour, not shadow. Everything below is implemented in `app/tokens.css` (values), `tailwind.theme.ts` (Tailwind names, shared with the desktop app), `components/motion` (motion) and `components/ui` + `components/layout` (components). The dev-only gallery at `/ui-kit` (run `npm run dev`) shows every primitive in every state.

## Rules

- One acting colour: `primary`. Buttons, links, the active tab, focus ring. Nothing else is blue-for-action.
- Radii are 0 (bands, bars, rail), 4 (chips, inline anchors, key hints), 8 (controls, cards, toasts), 12 (modals, media, empty states). `rounded-full` exists only for status dots and avatars, never for buttons or chips.
- No shadow and no lift on cards or buttons. The only shadow is `shadow-illustration`, for a card that depicts UI inside an image. Menus, dialogs and drawers separate with the soft rule (`border-border`) plus the dimmed overlay.
- Letter spacing is always 0. Weights are 350, 400, 500 and 700 only (`font-book`, `font-normal`, `font-medium`, `font-bold`).
- No raw hex, no arbitrary pixel sizes in components. Use the tokens.
- Status is never colour alone: pair it with an icon or text.

## Colours

Surfaces: `canvas` (white), `chrome` (global bar, neutral band), `band` (footer, quiet band), `band-blue`, `band-lavender`, `navy` and `sky` (scoped to hero and illustration surfaces; not general purpose).

Action: `primary` (#0057d2), `primary-deep` (#0040b0, hero band and outline label), `on-primary` (white).

Text ladder: `ink` (headings), `ink-lead`, `ink-body` (default text, also `text-foreground`), `ink-list`, `ink-meta` (captions, `text-muted-foreground`), and `ink-nav` for chrome only. Never set body copy in `ink-nav`.

Lines: `border` is the soft row and card rule, `hairline` (#475e75) is the full-strength rule used on inputs and outline buttons (`border-input`).

Existing names keep working and now resolve to this palette: `background`, `foreground`, `card`, `popover`, `muted`, `secondary`, `accent`, `brand`, `brand-soft`, `signal`, `severity-*`. The `--accent-*` section colours all resolve to `primary`.

## Dark theme (derived)

The spec is light only. Dark is derived so the same roles hold:

- Canvas is a near-black navy (222 hue, 8% lightness). `chrome`, `band`, `band-blue`, `band-lavender` step up from it by 3 to 9 points of lightness, keeping the blue and violet tints.
- The slate ladder is inverted: `ink` is white and `ink-body` to `ink-meta` descend from 84% to 70% lightness with the same slate hue. Every step holds at least 6.9:1 on every dark band.
- `primary` is lightened to 70% lightness so links hold 4.5:1 or better on every dark band (6.4 to 8.0 measured). Because the fill is now light, `primary-foreground` flips to the canvas colour. `primary-deep` is a darker navy-blue (218 hue, 80%, 24% lightness) so a hero band sits on the dark canvas instead of glowing; white text on it is 12.6:1. The outline label uses `outline-foreground`, a lighter blue.
- Hairlines are low-contrast: `border` is a navy-grey close to white at 14% over the canvas; `input` stays a full-strength rule (4.9:1 against the canvas).

## States

| State | Light | Dark |
|---|---|---|
| Primary hover | `primary-hover` (= `primary-deep`), 120ms | `primary-hover`, a lighter blue |
| Primary pressed | `primary-pressed` and a 1px push (`translateY(1px)`) | same |
| Outline hover | fill `band-blue` | same |
| Row hover | `chrome` | `chrome` |
| Link hover | underline | underline |
| Focus | 2px `ring` (primary) outline, 2px offset, on every interactive element | same |
| Disabled | 40% opacity, `cursor-not-allowed`, no hover or press | same |

Hover never changes layout and never adds a shadow.

## Status colours

A small desaturated set, for alerts and chips only (`positive`, `caution`, `warning`/`destructive`, each with a `-soft` surface). Use them for over-budget, error and success. Do not use them for charts or decoration. Measured text contrast on the soft surface: 6.2 to 6.8 in light, 6.0 to 7.2 in dark.

## Charts

The ramp is blues first, then slates: `chart-1` primary, `chart-2` navy, `chart-3` sky, `chart-4` slate, `chart-5` primary-deep, then lighter and darker tints through `chart-15`. `chart-spend` is primary, `chart-income` is dark slate, `chart-grid` is the soft rule, `chart-axis` is `ink-meta`.

Colour never carries a series alone. Give each series a second cue: dash pattern (solid, dashed, dotted), a marker shape (circle, square, triangle, diamond), a direct label, or a pattern fill. `ChartLegend` draws the dash pattern. Sky and the lightest slates are for fills and secondary series; they fall under 3:1 on white as lines.

## Typography

Inter Variable through `next/font`, full weight axis. Roles (size/line height/weight), use as `text-ty-*`:

| Class | Size / line height | Weight |
|---|---|---|
| `text-ty-display` | 57 / 62.7 | 700 |
| `text-ty-hero-title` | 36 / 39.6 | 500 |
| `text-ty-section-title` | 32 / 35.2 | 500 |
| `text-ty-lead` | 28 / 30.8 | 400 |
| `text-ty-subhead` | 20 / 22 | 500 |
| `text-ty-body-lg` | 20 / 30 | 400 |
| `text-ty-body` | 18 / 27 | 400 |
| `text-ty-button` | 16 / 17.6 | 500 |
| `text-ty-link` | 16 / 24 | 400 |
| `text-ty-nav-link` | 15 / 21 | 400 |
| `text-ty-list-item` | 15 / 21 | 350 |
| `text-ty-caption` | 14 / 21 | 400 |

Headings are `ink`, set tight; everything under them is slate. Marketing sizes are for heroes and section titles. Dashboards use the dense tier: `text-sm` (14), `text-xs` (12), `text-2xs` (11), with table headers at 14/500 over 14/400 cells. The legacy helpers (`text-title`, `text-body`, `text-supporting`, `text-caption`, `stat-value`) map onto this.

## Spacing

8px base. Spec steps are available as `p-sp-giant`, `gap-sp-wide` and so on (`sp-xxs` 2, `xs` 3, `sm` 8, `md` 9, `lg` 10, `xl` 12, `xxl` 15, `wide` 16, `section` 20, `giant` 24, `max` 32, `huge` 40, `hero` 48, `band` 56, `page` 72). Ordinary Tailwind steps stay available.

## Motion

Durations 120 / 200 / 320 / 560 ms and 900 to 1200 ms for draws and count-ups. Standard easing `cubic-bezier(0.2, 0, 0, 1)`, exit `cubic-bezier(0.4, 0, 1, 1)`, one critically damped spring for the tab underline. Only transform, opacity, clip-path, the individual `scale` property and stroke-dashoffset are animated; the one exception is `height` in `Grow` (disclosures and list inserts), so the content below follows instead of jumping. With `prefers-reduced-motion` everything becomes an opacity fade at most 120ms, count-ups and draws jump to their final value.

Entrances, scroll reveals, stagger, line draws and dialog pops are plain CSS (`app/motion.css`: `fx-rise`, `fx-reveal`, `fx-wipe`, `fx-stagger`, `fx-draw`, `fx-pop`) so they run on server-rendered markup before hydration. framer-motion loads on demand through `MotionRoot` (`domAnimation` only) and is used for presence, `Grow` and the tab spring. Use `components/motion/css-motion` from server pages; it carries no framer-motion.

## Layout helpers

`TitleRow` (`components/layout/title-row`) is the page heading with a lead and actions; `FullBleed` (`components/layout/full-bleed`) runs a band edge to edge inside the page column (the shell scroll area is a size container, so no viewport-width hacks). `tabClass(active, true)` is the dense equal-width tab strip for tool surfaces. `toast` lives in `ui/toast-store` (no Radix) so a route can show one without loading the toaster.
