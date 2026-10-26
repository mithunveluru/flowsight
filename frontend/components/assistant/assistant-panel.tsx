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
      <aside aria-label="Ask FlowSight" className="flex w-[380px] shrink-0 flex-col border-l border-border bg-card">
        <AssistantBody onClose={() => setOpen(false)} />
      </aside>
    );
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="right" hideClose className="flex w-full max-w-md flex-col p-0">
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
      <header className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
        <div>
          <h2 className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
            <Sparkles className="h-4 w-4 text-brand" strokeWidth={1.75} aria-hidden="true" /> Ask FlowSight
          </h2>
          <p className="mt-0.5 text-xs text-muted-foreground">Answers come from your own data. It cannot change anything.</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close assistant"
          className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </header>

      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-4">
        {exchanges.length === 0 && (
          <div>
            <p className="text-sm text-muted-foreground">Try one of these, or type your own.</p>
            <ul className="mt-3 space-y-2">
              {EXAMPLES.map((q) => (
                <li key={q}>
                  <button
                    type="button"
                    onClick={() => submit(q)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted"
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
        className="border-t border-border p-3"
      >
        <label htmlFor="assistant-question" className="sr-only">
          Your question
        </label>
        <div className="flex items-end gap-2">
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
            className="min-h-[44px] flex-1 resize-none rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
    <div className="space-y-2">
      <p className="ml-auto max-w-[85%] rounded-xl bg-muted px-3 py-2 text-sm text-foreground">{x.question}</p>
      {x.status === "pending" && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Looking at your data
        </p>
      )}
      {x.status === "error" && (
        <p className="flex items-start gap-2 text-sm text-warning">
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
    <div className={cn("rounded-xl border px-3 py-3", a.answered ? "border-border bg-background" : "border-caution/25 bg-caution-soft")}>
      <p className="mb-1.5 flex items-center gap-1.5 text-[11px] font-medium text-muted-foreground">
        <span className="rounded-full border border-border px-1.5 py-0.5">{modeLabel(a.mode)}</span>
        {!a.answered && <span className="text-caution">Not answered</span>}
      </p>
      <p className="whitespace-pre-line text-sm leading-relaxed text-foreground">
        {segments.map((s, i) =>
          s.kind === "text" ? (
            <React.Fragment key={i}>{s.text}</React.Fragment>
          ) : (
            <CitationButton key={i} n={index.get(s.id)!} citation={a.citations.find((c) => c.id === s.id)!} onOpen={onOpenCitation} />
          )
        )}
      </p>
      {uncited.length > 0 && (
        <p className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
          Based on:
          {uncited.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => onOpenCitation(c)}
              className="rounded-md border border-border px-1.5 py-0.5 text-foreground underline-offset-4 hover:bg-muted hover:underline"
            >
              {c.title}
            </button>
          ))}
        </p>
      )}
      {note && <p className="mt-2 text-xs text-muted-foreground">{note}</p>}
    </div>
  );
}

function CitationButton({ n, citation, onOpen }: { n: number; citation: AssistantCitation; onOpen: (c: AssistantCitation) => void }) {
  return (
    <button
      type="button"
      onClick={() => onOpen(citation)}
      aria-label={`Source ${n}: ${citation.title}`}
      className="mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded border border-border px-1 align-baseline text-[11px] font-medium text-brand hover:bg-muted"
    >
      {n}
    </button>
  );
}
