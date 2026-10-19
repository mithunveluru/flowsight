import { api } from "@/lib/api";
import type { SampleDataStatus } from "./types";

const PATH = "/api/v1/onboarding/sample-data";

export const onboardingApi = {
  sampleDataStatus: () => api.get<SampleDataStatus>(PATH, { auth: true }),
  loadSampleData: () => api.post<SampleDataStatus>(PATH, {}, { auth: true }),
  removeSampleData: () => api.delete<SampleDataStatus>(PATH, { auth: true }),
};
