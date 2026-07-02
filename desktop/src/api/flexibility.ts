import { api } from "~/lib/api";
import type { FlexibilityScore } from "@/features/simulation/types";

// Backed by GET /api/v1/simulate/flexibility (added for the companion): the
// current score with no scenario applied. Reuses the same backend engines as
// the web simulator, so the number matches.
export const flexibilityApi = {
  current: () => api.get<FlexibilityScore>("/api/v1/simulate/flexibility"),
};
