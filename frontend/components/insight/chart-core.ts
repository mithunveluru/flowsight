// Pure helpers for the SVG charts. No React, so node tests load this file directly.

export type MarkerShape = "circle" | "square" | "triangle" | "diamond";

/** Round tick values covering [min, max], about `count` of them. */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!Number.isFinite(min) || !Number.isFinite(max)) return [0];
  if (min === max) return [min];
  const span = max - min;
  const raw = span / Math.max(1, count);
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const norm = raw / mag;
  const step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
  const out: number[] = [];
  for (let v = Math.ceil(min / step) * step; v <= max + step * 1e-9; v += step) {
    out.push(Math.abs(v) < step * 1e-9 ? 0 : v);
  }
  return out;
}

/** Axis range that always includes zero and the data, padded a little at the top. */
export function yDomain(values: number[]): [number, number] {
  const finite = values.filter(Number.isFinite);
  if (finite.length === 0) return [0, 1];
  const lo = Math.min(0, ...finite);
  const hi = Math.max(0, ...finite);
  if (lo === hi) return [0, 1];
  const ticks = niceTicks(lo, hi, 4);
  const top = ticks[ticks.length - 1];
  const bottom = ticks[0];
  return [Math.min(lo, bottom), Math.max(hi, top)];
}

/** Path through points; a NaN y breaks the line. */
export function linePath(xs: number[], ys: number[], from = 0, to = xs.length - 1): string {
  let d = "";
  let pen = false;
  for (let i = from; i <= to; i++) {
    if (!Number.isFinite(ys[i])) {
      pen = false;
      continue;
    }
    d += `${pen ? "L" : "M"}${xs[i].toFixed(2)} ${ys[i].toFixed(2)}`;
    pen = true;
  }
  return d;
}

/** Closed polygon between an upper and a lower edge over the indices where both exist. */
export function bandPath(xs: number[], upper: number[], lower: number[]): string {
  const idx = xs.map((_, i) => i).filter((i) => Number.isFinite(upper[i]) && Number.isFinite(lower[i]));
  if (idx.length < 2) return "";
  const top = idx.map((i, k) => `${k ? "L" : "M"}${xs[i].toFixed(2)} ${upper[i].toFixed(2)}`).join("");
  const bottom = [...idx].reverse().map((i) => `L${xs[i].toFixed(2)} ${lower[i].toFixed(2)}`).join("");
  return `${top}${bottom}Z`;
}

/** Linear blend of two equally long arrays; NaN on either side snaps to the target. */
export function blend(from: number[], to: number[], t: number): number[] {
  return to.map((v, i) => {
    const a = from[i];
    return Number.isFinite(a) && Number.isFinite(v) ? a + (v - a) * t : v;
  });
}

/** Index of the category nearest to x when n categories span [left, left + width]. */
export function nearestIndex(x: number, left: number, width: number, n: number): number {
  if (n <= 1 || width <= 0) return 0;
  return Math.min(n - 1, Math.max(0, Math.round(((x - left) / width) * (n - 1))));
}

/** Pushes labels apart vertically so none sit closer than `gap`, keeping order. */
export function spreadLabels(ys: number[], gap: number, min: number, max: number): number[] {
  const order = ys.map((y, i) => ({ y, i })).sort((a, b) => a.y - b.y);
  const out = new Array<number>(ys.length);
  let prev = min - gap;
  for (const { y, i } of order) {
    const v = Math.max(y, prev + gap);
    out[i] = v;
    prev = v;
  }
  const over = (out.length ? Math.max(...out) : 0) - max;
  if (over > 0) return out.map((v) => v - over);
  return out;
}

/** Slices of a donut as arcs of a circle of circumference `c`, with `gap` between them. */
export function donutSlices(values: number[], c: number, gap: number) {
  const total = values.reduce((a, b) => a + Math.max(0, b), 0);
  let offset = 0;
  return values.map((v) => {
    const share = total > 0 ? Math.max(0, v) / total : 0;
    const len = share * c;
    const slice = { start: offset, length: Math.max(0, len - (values.length > 1 ? gap : 0)), share };
    offset += len;
    return slice;
  });
}
