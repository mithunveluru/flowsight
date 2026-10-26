import { api } from "@/lib/api";
import type { AssistantAnswer } from "@/features/insights/types";

export const assistantApi = {
  ask: (question: string) =>
    api.post<AssistantAnswer>("/api/v1/assistant/ask", { question }, { auth: true }),
};
