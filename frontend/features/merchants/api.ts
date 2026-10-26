import { api } from "@/lib/api";
import type { RuleApplyResult, RulePreview } from "./types";

// Preview and apply share a rate limit of 30 per hour per user.
export const merchantRuleApi = {
  preview: (ruleId: string) =>
    api.post<RulePreview>(`/api/v1/merchant-rules/${ruleId}/preview`, undefined, { auth: true }),

  apply: (ruleId: string) =>
    api.post<RuleApplyResult>(`/api/v1/merchant-rules/${ruleId}/apply`, undefined, { auth: true }),
};
