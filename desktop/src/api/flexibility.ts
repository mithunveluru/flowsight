import { api } from "~/lib/api";
import type { FlexibilityScore } from "@/features/simulation/types";

// GET /api/v1/simulate/flexibility: current score, no scenario; same engines as the web simulator.
export const flexibilityApi = {
  current: () => api.get<FlexibilityScore>("/api/v1/simulate/flexibility"),
};
