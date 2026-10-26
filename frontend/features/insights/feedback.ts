import type { InsightsResponse } from "./types";

export type FeedbackAction = "ACCEPTED" | "DISMISSED";

/** Optimistic view of the insights after the user answers a recommendation. */
export function applyFeedback(data: InsightsResponse, id: string, action: FeedbackAction): InsightsResponse {
  return {
    ...data,
    recommendations:
      action === "DISMISSED"
        ? data.recommendations.filter((r) => r.id !== id)
        : data.recommendations.map((r) => (r.id === id ? { ...r, feedback: "ACCEPTED" as const } : r)),
  };
}

/** Recommendations the page can answer: they need the stable id the endpoints take. */
export const canAnswer = (r: { id?: string }): r is { id: string } => typeof r.id === "string" && r.id.length > 0;
