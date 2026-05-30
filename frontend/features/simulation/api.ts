import { api } from "@/lib/api";
import type { ScenarioRequest, SimulationResult } from "./types";

export const simulationApi = {
  simulate: (scenario: ScenarioRequest) =>
    api.post<SimulationResult>("/api/v1/simulate", scenario, { auth: true }),
};
