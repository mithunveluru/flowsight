"use client";

import * as React from "react";
import { CircleAlert, Loader2, Send, Sparkles, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "@/components/ui/dialog";
import { EvidenceDrawer } from "@/components/ui/evidence-drawer";
import { cn } from "@/lib/utils";
import {
  MAX_QUESTION_LENGTH,
  citationEvidence,
  fallbackNote,
  modeLabel,
  splitAnswer,
} from "@/features/assistant/logic";
import type { AssistantCitation } from "@/features/insights/types";
import { useAssistant, type Exchange } from "./assistant-context";

const EXAMPLES = [
  "How much did I spend on food this month?",
  "What are my biggest merchants this month?",
  "Which subscriptions am I paying for?",
];

const DOCK_QUERY = "(min-width: 1280px)";

function useDocked(): boolean {
  return React.useSyncExternalStore(
    (cb) => {
      const mq = window.matchMedia(DOCK_QUERY);
      mq.addEventListener("change", cb);
      return () => mq.removeEventListener("change", cb);
    },
    () => window.matchMedia(DOCK_QUERY).matches,
    () => false
  );
}

/** Docked column from xl up, a Sheet below it. Read-only: it only asks questions. */
export function AssistantPanel() {
  const { open, setOpen } = useAssistant();
  const docked = useDocked();

  if (docked) {
    if (!open) return null;
    return (
      <aside aria-label="Ask FlowSight" className="flex w-[380px] shrink-0 flex-col bg-chrome">
        <AssistantBody onClose={() => setOpen(false)} />
      </aside>
    );
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="right" hideClose className="flex w-full max-w-md flex-col bg-chrome p-0">
        <SheetTitle className="sr-only">Ask FlowSight</SheetTitle>
        <SheetDescription className="sr-only">Ask a question about your own spending. Answers are read-only.</SheetDescription>
        <AssistantBody onClose={() => setOpen(false)} />
      </SheetContent>
    </Sheet>
  );
}

function AssistantBody({ onClose }: { onClose: () => void }) {
  const { exchanges, ask, busy } = useAssistant();
  const [text, setText] = React.useState("");
  const [viewing, setViewing] = React.useState<AssistantCitation | null>(null);
  const inputRef = React.useRef<HTMLTextAreaElement>(null);
  const endRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => inputRef.current?.focus(), []);
  React.useEffect(() => endRef.current?.scrollIntoView?.({ block: "end" }), [exchanges]);

  const submit = (q: string) => {
    if (!q.trim() || busy) return;
    setText("");
    void ask(q);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex items-start justify-between gap-sp-xl px-sp-giant py-sp-xl">
        <div>
          <h2 className="flex items-center gap-sp-sm text-ty-link font-medium text-ink">
            <Sparkles className="h-4 w-4 text-primary" strokeWidth={1.75} aria-hidden="true" /> Ask FlowSight
          </h2>
          <p className="mt-sp-xs text-xs text-ink-meta">Answers come from your own data. It cannot change anything.</p>
        </div>
        <Button type="button" variant="ghost" size="icon" onClick={onClose} aria-label="Close assistant" className="shrink-0 coarse:h-11 coarse:w-11">
          <X className="h-4 w-4" aria-hidden="true" />
        </Button>
      </header>

      <div className="min-h-0 flex-1 space-y-sp-giant overflow-y-auto px-sp-giant py-sp-xl">
        {exchanges.length === 0 && (
          <div>
            <p className="text-sm text-ink-meta">Try one of these, or type your own.</p>
            <ul className="mt-sp-xl space-y-sp-sm">
              {EXAMPLES.map((q) => (
                <li key={q}>
                  <button
                    type="button"
                    onClick={() => submit(q)}
                    className="press min-h-11 w-full rounded-md bg-canvas px-sp-xl py-sp-sm text-left text-sm text-ink transition-colors duration-fast hover:bg-band"
                  >
                    {q}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {exchanges.map((x) => (
          <ExchangeView key={x.id} exchange={x} onOpenCitation={setViewing} />
        ))}
        <div ref={endRef} />
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(text);
        }}
        className="bg-canvas p-sp-xl"
      >
        <label htmlFor="assistant-question" className="sr-only">
          Your question
        </label>
        <div className="flex items-end gap-sp-sm">
          <textarea
            id="assistant-question"
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                submit(text);
              }
            }}
            rows={2}
            maxLength={MAX_QUESTION_LENGTH}
            placeholder="Ask about your spending"
            className="min-h-11 flex-1 resize-none rounded-md bg-chrome px-sp-xl py-sp-sm text-sm text-ink placeholder:text-ink-meta focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
          <Button type="submit" size="sm" disabled={busy || !text.trim()} aria-label="Send question" className="h-11">
            {busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Send className="h-4 w-4" aria-hidden="true" />}
          </Button>
        </div>
      </form>

      {viewing && (
        <EvidenceDrawer
          open
          onOpenChange={(o) => !o && setViewing(null)}
          title={viewing.title}
          evidence={citationEvidence(viewing)}
        />
      )}
    </div>
  );
}

function ExchangeView({ exchange: x, onOpenCitation }: { exchange: Exchange; onOpenCitation: (c: AssistantCitation) => void }) {
  return (
    <div className="space-y-sp-sm">
      <p className="ml-auto max-w-[85%] rounded-md bg-band-blue px-sp-xl py-sp-sm text-sm text-ink">{x.question}</p>
      {x.status === "pending" && (
        <p className="flex items-center gap-sp-sm text-sm text-ink-meta">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Looking at your data
        </p>
      )}
      {x.status === "error" && (
        <p role="alert" className="flex items-start gap-sp-sm text-sm text-warning">
          <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> {x.error}
        </p>
      )}
      {x.status === "done" && x.answer && <AnswerView exchange={x} onOpenCitation={onOpenCitation} />}
    </div>
  );
}

function AnswerView({ exchange: x, onOpenCitation }: { exchange: Exchange; onOpenCitation: (c: AssistantCitation) => void }) {
  const a = x.answer!;
  const segments = splitAnswer(a.answer, a.citations);
  const note = fallbackNote(a);
  const index = new Map(a.citations.map((c, i) => [c.id, i + 1]));
  const cited = new Set(segments.flatMap((s) => (s.kind === "cite" ? [s.id] : [])));
  const uncited = a.citations.filter((c) => !cited.has(c.id));

  return (
    <div className={cn("rounded-md px-sp-xl py-sp-xl", a.answered ? "bg-canvas" : "bg-caution-soft")}>
      <p className="mb-sp-sm flex items-center gap-sp-sm text-xs text-ink-meta">
        <span className="rounded-sm bg-band px-sp-sm py-0.5">{modeLabel(a.mode)}</span>
        {!a.answered && (
          <span className="inline-flex items-center gap-sp-xs text-caution">
            <CircleAlert className="h-3 w-3" aria-hidden="true" /> Not answered
          </span>
        )}
      </p>
      <p className="whitespace-pre-line text-sm leading-relaxed text-ink-body">
        {segments.map((s, i) =>
          s.kind === "text" ? (
            <React.Fragment key={i}>{s.text}</React.Fragment>
          ) : (
            <CitationButton key={i} n={index.get(s.id)!} citation={a.citations.find((c) => c.id === s.id)!} onOpen={onOpenCitation} />
          )
        )}
      </p>
      {uncited.length > 0 && (
        <p className="mt-sp-sm flex flex-wrap items-center gap-sp-sm text-xs text-ink-meta">
          Based on:
          {uncited.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => onOpenCitation(c)}
              className="rounded-sm bg-band px-sp-sm py-0.5 text-ink underline-offset-4 hover:underline coarse:min-h-11"
            >
              {c.title}
            </button>
          ))}
        </p>
      )}
      {note && <p className="mt-sp-sm text-xs text-ink-meta">{note}</p>}
    </div>
  );
}

function CitationButton({ n, citation, onOpen }: { n: number; citation: AssistantCitation; onOpen: (c: AssistantCitation) => void }) {
  return (
    <button
      type="button"
      onClick={() => onOpen(citation)}
      aria-label={`Source ${n}: ${citation.title}`}
      className="mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-sm bg-band-blue px-1 align-baseline text-2xs font-medium text-primary hover:bg-band coarse:h-11 coarse:min-w-11"
    >
      {n}
    </button>
  );
}
