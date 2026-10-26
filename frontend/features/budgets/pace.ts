import type { Budget } from "./types";

export type PaceRisk = "none" | "possible" | "likely";

export interface PaceView {
  limit: number;
  projected: number;
  lower80: number;
  upper80: number;
  risk: PaceRisk;
  // positions along the bar as shares of its full width
  scale: number;
  spentAt: number;
  lowAt: number;
  highAt: number;
  limitAt: number;
}

/** Projection band against the effective limit; null when the API gave no usable pace. */
export function paceView(b: Budget): PaceView | null {
  const pace = b.pace;
  if (!pace || pace.method === "none") return null;
  const limit = b.effectiveLimit ?? b.monthlyLimit;
  const scale = Math.max(limit, pace.upper80, b.currentSpend, 1);
  const at = (v: number) => Math.min(100, Math.max(0, (v / scale) * 100));
  return {
    limit,
    projected: b.projectedTotal,
    lower80: pace.lower80,
    upper80: pace.upper80,
    risk: pace.lower80 > limit ? "likely" : pace.upper80 > limit ? "possible" : "none",
    scale,
    spentAt: at(b.currentSpend),
    lowAt: at(pace.lower80),
    highAt: at(pace.upper80),
    limitAt: at(limit),
  };
}
