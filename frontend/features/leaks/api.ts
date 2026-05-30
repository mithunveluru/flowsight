import { api } from "@/lib/api";
import type { LeakDetectionResponse } from "./types";

export const leaksApi = {
  detect: () =>
    api.get<LeakDetectionResponse>("/api/v1/leaks", { auth: true }),
};
