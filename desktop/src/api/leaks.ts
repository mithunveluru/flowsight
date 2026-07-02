import { api } from "~/lib/api";
import type { LeakDetectionResponse } from "@/features/leaks/types";

export const leaksApi = {
  detect: () => api.get<LeakDetectionResponse>("/api/v1/leaks"),
};
