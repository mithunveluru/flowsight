"use client";

import * as React from "react";
import { m, useReducedMotion } from "framer-motion";
import { DrawLine, DURATION } from "@/components/motion/primitives";
import { EASE_STANDARD } from "@/components/motion/tokens";
import {
  bandPath,
  linePath,
  nearestIndex,
  niceTicks,
  spreadLabels,
  yDomain,
  type MarkerShape,
} from "./chart-core";
import { Marker, TooltipCard, useMorph, useSeen, useWidth } from "./chart-parts";

export interface LineSeries {
  key: string;
  label: string;
  // any CSS colour, normally hsl(var(--chart-*))
  color: string;
  // dash pattern for the whole line, e.g. a baseline drawn against the main series
  dash?: string;
  marker: MarkerShape;
  values: (number | null)[];
  // first index that is a projection: the line is dashed from the point before it
  projectedFrom?: number;
  // shaded range around the line (for example the likely 80% band)
  lower?: (number | null)[];
  upper?: (number | null)[];
  strokeWidth?: number;
}

const DASH_PROJECTED = "5 4";
const MARGIN = { top: 12, bottom: 28, left: 56 };
const nan = (v: number | null | undefined) => (v == null ? Number.NaN : v);

/**
 * Line chart in plain SVG: lines draw in once, the shape eases to new data instead of
 * redrawing, a crosshair and tooltip follow the pointer, and each line is labelled at its end.
 */
export function LineChart({
  categories,
  series,
  height = 280,
  format,
  formatAxis,
  tooltipRows,
  endLabels = true,
  ariaLabel,
}: {
  categories: string[];
  series: LineSeries[];
  height?: number;
  format: (v: number) => string;
  formatAxis?: (v: number) => string;
  // extra tooltip lines for one category; defaults to one row per series
  tooltipRows?: (index: number, values: Record<string, number>) => React.ReactNode;
  endLabels?: boolean;
  ariaLabel?: string;
}) {
  const [wrapRef, width] = useWidth<HTMLDivElement>();
  const [seenRef, seen] = useSeen<HTMLDivElement>();
  const reduced = useReducedMotion();
  const n = categories.length;
  const stride = n;

  const flat = React.useMemo(() => {
    const pad = new Array<number>(n).fill(Number.NaN);
    return series.flatMap((s) => [...s.values.map(nan), ...(s.lower ? s.lower.map(nan) : pad), ...(s.upper ? s.upper.map(nan) : pad)]);
  }, [series, n]);
  const shown = useMorph(flat);
  const [hover, setHover] = React.useState<number | null>(null);

  const rightMargin = endLabels ? 76 : 16;
  const plotLeft = MARGIN.left;
  const plotRight = Math.max(plotLeft + 10, width - rightMargin);
  const plotW = plotRight - plotLeft;
  const plotTop = MARGIN.top;
  const plotBottom = height - MARGIN.bottom;

  const [lo, hi] = yDomain(shown);
  const ticks = niceTicks(lo, hi, 4);
  const y = (v: number) => plotBottom - ((v - lo) / (hi - lo || 1)) * (plotBottom - plotTop);
  const xs = categories.map((_, i) => (n <= 1 ? plotLeft + plotW / 2 : plotLeft + (i / (n - 1)) * plotW));
  const labelEvery = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(plotW / 64))));

  const rows = series.map((s, si) => {
    const base = si * stride * 3;
    const slice = (k: number) => shown.slice(base + k * stride, base + (k + 1) * stride);
    const vals = slice(0);
    return { s, vals, ys: vals.map((v) => (Number.isFinite(v) ? y(v) : Number.NaN)), lower: slice(1), upper: slice(2) };
  });

  const lastIdx = (vals: number[]) => {
    for (let i = vals.length - 1; i >= 0; i--) if (Number.isFinite(vals[i])) return i;
    return -1;
  };
  const labelYs = spreadLabels(
    rows.map((r) => {
      const i = lastIdx(r.vals);
      return i >= 0 ? r.ys[i] : plotTop;
    }),
    15,
    plotTop + 4,
    plotBottom
  );

  const onMove = (e: React.PointerEvent<SVGRectElement>) => {
    const box = e.currentTarget.getBoundingClientRect();
    const i = nearestIndex(e.clientX - box.left + plotLeft, plotLeft, plotW, n);
    setHover((prev) => (prev === i ? prev : i));
  };

  const hoverValues: Record<string, number> | null =
    hover == null ? null : Object.fromEntries(rows.map((r) => [r.s.key, r.vals[hover]]));

  return (
    <div
      ref={(el) => {
        (wrapRef as React.MutableRefObject<HTMLDivElement | null>).current = el;
        (seenRef as React.MutableRefObject<HTMLDivElement | null>).current = el;
      }}
      className="relative w-full"
      style={{ height }}
    >
      {width > 0 && (
        <svg width={width} height={height} role="img" aria-label={ariaLabel} className="block overflow-visible">
          {ticks.map((t) => (
            <g key={t}>
              <line
                x1={plotLeft}
                x2={plotRight}
                y1={y(t)}
                y2={y(t)}
                stroke="hsl(var(--chart-grid))"
                strokeWidth={t === 0 ? 1.5 : 1}
              />
              <text x={plotLeft - 8} y={y(t)} dy="0.32em" textAnchor="end" fontSize={11} fill="hsl(var(--chart-axis))">
                {(formatAxis ?? format)(t)}
              </text>
            </g>
          ))}
          {categories.map((c, i) =>
            i % labelEvery === 0 || i === n - 1 ? (
              <text
                key={c + i}
                x={xs[i]}
                y={plotBottom + 18}
                textAnchor={i === 0 && n > 1 ? "start" : i === n - 1 && n > 1 ? "end" : "middle"}
                fontSize={11}
                fill="hsl(var(--chart-axis))"
              >
                {c}
              </text>
            ) : null
          )}

          {rows.map(({ s, lower, upper }) => {
            const toY = (v: number) => (Number.isFinite(v) ? y(v) : Number.NaN);
            const band = s.lower && s.upper ? bandPath(xs, upper.map(toY), lower.map(toY)) : "";
            return band ? (
              <m.path
                key={`${s.key}-band`}
                data-chart-band
                d={band}
                fill={s.color}
                initial={{ opacity: 0 }}
                animate={{ opacity: seen ? 0.16 : 0 }}
                transition={{ duration: reduced ? DURATION.fast : DURATION.reveal, delay: reduced ? 0 : 0.3, ease: EASE_STANDARD }}
              />
            ) : null;
          })}

          {seen &&
            rows.map(({ s, ys }, si) => {
              const w = s.strokeWidth ?? 2.5;
              const split = s.projectedFrom != null && s.projectedFrom > 0 ? s.projectedFrom : null;
              const solidTo = split != null ? split - 1 : n - 1;
              return (
                <g key={s.key}>
                  {s.dash ? (
                    <m.path
                      d={linePath(xs, ys)}
                      fill="none"
                      stroke={s.color}
                      strokeWidth={w}
                      strokeDasharray={s.dash}
                      strokeLinecap="round"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      transition={{ duration: reduced ? DURATION.fast : DURATION.reveal, delay: reduced ? 0 : si * 0.12, ease: EASE_STANDARD }}
                    />
                  ) : (
                    <DrawLine d={linePath(xs, ys, 0, solidTo)} stroke={s.color} strokeWidth={w} delay={si * 0.12} />
                  )}
                  {split != null && !s.dash && (
                    <m.path
                      d={linePath(xs, ys, split - 1, n - 1)}
                      fill="none"
                      stroke={s.color}
                      strokeWidth={w}
                      strokeDasharray={DASH_PROJECTED}
                      strokeLinecap="round"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      transition={{ duration: reduced ? DURATION.fast : DURATION.reveal, delay: reduced ? 0 : 0.7 + si * 0.12, ease: EASE_STANDARD }}
                    />
                  )}
                </g>
              );
            })}

          {rows.map(({ s, ys }) =>
            n <= 16
              ? ys.map((yy, i) =>
                  Number.isFinite(yy) && (s.projectedFrom == null || i < s.projectedFrom) ? (
                    <m.g
                      key={`${s.key}-${i}`}
                      initial={{ opacity: 0 }}
                      animate={{ opacity: seen ? 1 : 0 }}
                      transition={{ duration: DURATION.base, delay: reduced ? 0 : 0.5 + (i / Math.max(1, n)) * 0.5 }}
                    >
                      <Marker shape={s.marker} x={xs[i]} y={yy} r={3} color={s.color} />
                    </m.g>
                  ) : null
                )
              : null
          )}

          {endLabels &&
            rows.map(({ s, vals, ys }, i) => {
              const li = lastIdx(vals);
              if (li < 0) return null;
              return (
                <m.text
                  key={`${s.key}-label`}
                  x={xs[li] + 10}
                  y={labelYs[i]}
                  dy="0.32em"
                  fontSize={12}
                  fontWeight={500}
                  fill="hsl(var(--ink))"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: seen ? 1 : 0 }}
                  transition={{ duration: DURATION.base, delay: reduced ? 0 : 0.9 }}
                >
                  {s.label.length > 12 ? `${s.label.slice(0, 11)}…` : s.label}
                </m.text>
              );
            })}

          {hover != null && (
            <g pointerEvents="none">
              <line x1={xs[hover]} x2={xs[hover]} y1={plotTop} y2={plotBottom} stroke="hsl(var(--chart-axis))" strokeWidth={1} />
              {rows.map(({ s, ys }) =>
                Number.isFinite(ys[hover]) ? <Marker key={s.key} shape={s.marker} x={xs[hover]} y={ys[hover]} r={5} color={s.color} /> : null
              )}
            </g>
          )}

          <rect
            x={plotLeft}
            y={plotTop}
            width={plotW}
            height={plotBottom - plotTop}
            fill="transparent"
            style={{ touchAction: "pan-y" }}
            onPointerMove={onMove}
            onPointerDown={onMove}
            onPointerLeave={() => setHover(null)}
          />
        </svg>
      )}

      {hover != null && hoverValues && (
        <TooltipCard x={xs[hover]} width={width} top={plotTop}>
          <p className="mb-sp-xs font-medium text-ink">{categories[hover]}</p>
          {tooltipRows ? (
            tooltipRows(hover, hoverValues)
          ) : (
            <ul className="space-y-sp-xs">
              {rows.map(({ s, vals }) =>
                Number.isFinite(vals[hover]) ? (
                  <li key={s.key} className="flex items-center justify-between gap-sp-xl">
                    <span className="flex items-center gap-sp-sm">
                      <svg width="12" height="12" aria-hidden="true">
                        <Marker shape={s.marker} x={6} y={6} r={3} color={s.color} />
                      </svg>
                      {s.label}
                    </span>
                    <span className="tabular-nums text-ink">{format(vals[hover])}</span>
                  </li>
                ) : null
              )}
            </ul>
          )}
        </TooltipCard>
      )}
    </div>
  );
}
