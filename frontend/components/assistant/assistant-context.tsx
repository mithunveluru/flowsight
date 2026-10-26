"use client";

import * as React from "react";
import { useAnnounce } from "@/components/ui/announcer";
import { assistantApi } from "@/features/assistant/api";
import { MAX_QUESTION_LENGTH, announcement, askErrorMessage } from "@/features/assistant/logic";
import type { AssistantAnswer } from "@/features/insights/types";

export interface Exchange {
  id: number;
  question: string;
  status: "pending" | "done" | "error";
  answer?: AssistantAnswer;
  error?: string;
}

interface AssistantState {
  open: boolean;
  setOpen: (open: boolean) => void;
  toggle: () => void;
  exchanges: Exchange[];
  busy: boolean;
  ask: (question: string) => Promise<void>;
}

const AssistantContext = React.createContext<AssistantState | null>(null);

export function AssistantProvider({ children }: { children: React.ReactNode }) {
  const announce = useAnnounce();
  const [open, setOpen] = React.useState(false);
  const [exchanges, setExchanges] = React.useState<Exchange[]>([]);
  const nextId = React.useRef(1);

  const ask = React.useCallback(
    async (raw: string) => {
      const question = raw.trim().slice(0, MAX_QUESTION_LENGTH);
      if (!question) return;
      const id = nextId.current++;
      const patch = (p: Partial<Exchange>) =>
        setExchanges((all) => all.map((x) => (x.id === id ? { ...x, ...p } : x)));
      setExchanges((all) => [...all, { id, question, status: "pending" }]);
      try {
        const answer = await assistantApi.ask(question);
        patch({ status: "done", answer });
        announce(announcement(answer));
      } catch (e) {
        const error = askErrorMessage(e);
        patch({ status: "error", error });
        announce(error, "assertive");
      }
    },
    [announce]
  );

  const value = React.useMemo<AssistantState>(
    () => ({
      open,
      setOpen,
      toggle: () => setOpen((v) => !v),
      exchanges,
      busy: exchanges.some((x) => x.status === "pending"),
      ask,
    }),
    [open, exchanges, ask]
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

export function useAssistant(): AssistantState {
  const ctx = React.useContext(AssistantContext);
  if (!ctx) throw new Error("useAssistant must be used inside AssistantProvider");
  return ctx;
}
