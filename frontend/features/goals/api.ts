import { api } from "@/lib/api";
import type { Goal, GoalContributionPayload, GoalPayload } from "./types";

export const goalsApi = {
  list: () =>
    api.get<Goal[]>("/api/v1/goals", { auth: true }),

  getById: (id: string) =>
    api.get<Goal>(`/api/v1/goals/${id}`, { auth: true }),

  create: (payload: GoalPayload) =>
    api.post<Goal>("/api/v1/goals", payload, { auth: true }),

  update: (id: string, payload: GoalPayload) =>
    api.put<Goal>(`/api/v1/goals/${id}`, payload, { auth: true }),

  contribute: (id: string, payload: GoalContributionPayload) =>
    api.post<Goal>(`/api/v1/goals/${id}/contribute`, payload, { auth: true }),

  markComplete: (id: string) =>
    api.patch<Goal>(`/api/v1/goals/${id}/complete`, {}, { auth: true }),

  abandon: (id: string) =>
    api.patch<Goal>(`/api/v1/goals/${id}/abandon`, {}, { auth: true }),

  delete: (id: string) =>
    api.delete<void>(`/api/v1/goals/${id}`, { auth: true }),
};
