import type { Evidence } from "@/components/ui/evidence-drawer";
import { humanizeKey, rupees } from "../insights/format.ts";
import type { AssistantAnswer, AssistantCitation } from "@/features/insights/types";

export const MAX_QUESTION_LENGTH = 500;

export type Segment = { kind: "text"; text: string } | { kind: "cite"; id: string };

/** Splits "spent 45,230 [t1]." into text and citation markers; markers without a citation stay text. */
export function splitAnswer(text: string, citations: AssistantCitation[]): Segment[] {
  const known = new Set(citations.map((c) => c.id));
  const out: Segment[] = [];
  let last = 0;
  for (const m of text.matchAll(/\[(t\d+)\]/g)) {
    if (!known.has(m[1])) continue;
    const at = m.index ?? 0;
    if (at > last) out.push({ kind: "text", text: text.slice(last, at) });
    out.push({ kind: "cite", id: m[1] });
    last = at + m[0].length;
  }
  if (last < text.length) out.push({ kind: "text", text: text.slice(last) });
  return out;
}

const MONEY_KEY = /spend|income|amount|cashflow|saving|impact|limit|budget|target|remaining|total|cost|price|balance/i;
const NOT_MONEY_KEY = /count|percent|days|months|rate|number|share/i;

export function formatFact(key: string, value: string | number): string {
  if (typeof value !== "number") return String(value);
  if (MONEY_KEY.test(key) && !NOT_MONEY_KEY.test(key)) return rupees(value);
  return value.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

/** The cited tool result as it was returned: one row per fact. */
export function citationEvidence(c: AssistantCitation): Evidence {
  return {
    method: `Read-only lookup: ${c.title}. These are the figures the answer was written from.`,
    inputs: Object.entries(c.facts).map(([k, v]) => ({ label: humanizeKey(k), value: formatFact(k, v) })),
    source: "rules",
  };
}

const FALLBACK_REASON: Record<string, string> = {
  rejected_ungrounded_numbers: "the written answer used numbers that were not in your data",
  llm_timeout: "the language model took too long",
};

export function fallbackNote(a: AssistantAnswer): string | null {
  if (!a.fallbackReason) return null;
  const why = FALLBACK_REASON[a.fallbackReason] ?? humanizeKey(a.fallbackReason).toLowerCase();
  return `Answered by rules instead of the language model because ${why}.`;
}

export function modeLabel(mode: AssistantAnswer["mode"]): string {
  return mode === "LLM" ? "Language model" : "Rules";
}

export function announcement(a: AssistantAnswer): string {
  return a.answered ? `Answer: ${a.answer.replace(/\s*\[t\d+\]/g, "")}` : `Could not answer. ${a.answer}`;
}

export function askErrorMessage(e: unknown): string {
  const status = (e as { status?: unknown } | null)?.status;
  if (status === 429) return "You have asked a lot of questions this hour. Try again a little later.";
  if (status === 400) return "That question could not be sent. Keep it under 500 characters.";
  return "The assistant could not be reached. Try again in a moment.";
}
