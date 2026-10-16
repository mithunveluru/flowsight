"use client";

import * as React from "react";
import Link from "next/link";
import { Info } from "lucide-react";
import { cn } from "@/lib/utils";
import { SourceTag, type SignalSource } from "./signals";
import { ConfidenceChip, type ConfidenceLevel, type ConfidenceThresholds } from "./confidence-chip";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "./dialog";

export interface EvidenceInput {
  label: React.ReactNode;
  value?: React.ReactNode;
  href?: string;
}

export interface Evidence {
  // plain-language description of how the figure was produced
  method: React.ReactNode;
  inputs?: EvidenceInput[];
  // how many inputs exist in total when `inputs` is a sample
  inputCount?: number;
  confidence?: number | null;
  confidenceLevel?: ConfidenceLevel;
  confidenceThresholds?: ConfidenceThresholds;
  window?: { from: string | Date; to: string | Date };
  source?: SignalSource;
  modelVersion?: string;
  computedAt?: string | Date;
  caveats?: React.ReactNode[];
}

const DATE_FORMAT: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" };

function formatDate(value: string | Date): string {
  const d = typeof value === "string" ? new Date(value) : value;
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString("en-IN", DATE_FORMAT);
}

/** The "Why?" affordance placed next to a derived number; pass a label naming that number. */
export const EvidenceTrigger = React.forwardRef<
  HTMLButtonElement,
  React.ButtonHTMLAttributes<HTMLButtonElement> & { label: string }
>(({ label, className, children, ...props }, ref) => (
  <button
    ref={ref}
    type="button"
    aria-label={`Why ${label}?`}
    className={cn(
      "inline-flex h-7 items-center gap-1 rounded-md px-1.5 text-xs font-medium text-muted-foreground transition-colors duration-fast hover:bg-muted hover:text-foreground",
      className
    )}
    {...props}
  >
    <Info className="h-3.5 w-3.5" strokeWidth={1.75} aria-hidden="true" />
    {children ?? <span aria-hidden="true">Why?</span>}
  </button>
));
EvidenceTrigger.displayName = "EvidenceTrigger";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="border-t border-border px-6 py-5">
      <h3 className="text-overline mb-2.5">{title}</h3>
      {children}
    </section>
  );
}

/**
 * Side sheet that explains a machine- or rule-derived figure: method, inputs,
 * data window, confidence and provenance. Controlled (`open`/`onOpenChange`)
 * or uncontrolled with a `trigger` element. `footer` is the place for a
 * correction action ("This is wrong").
 */
export function EvidenceDrawer({
  title,
  value,
  evidence,
  trigger,
  open,
  onOpenChange,
  footer,
  children,
}: {
  title: React.ReactNode;
  value?: React.ReactNode;
  evidence: Evidence;
  trigger?: React.ReactElement;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  footer?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const { method, inputs, inputCount, window: dataWindow, source, modelVersion, computedAt, caveats } = evidence;
  const hasConfidence = evidence.confidenceLevel != null || evidence.confidence != null;
  const contentRef = React.useRef<HTMLDivElement>(null);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      {trigger && <SheetTrigger asChild>{trigger}</SheetTrigger>}
      <SheetContent
        ref={contentRef}
        side="right"
        className="overflow-y-auto focus:outline-none"
        // a reading panel: start at the top instead of jumping to the first link or button
        onOpenAutoFocus={(e) => {
          e.preventDefault();
          contentRef.current?.focus();
        }}
      >
        <SheetHeader className="px-6 pb-5 pt-6">
          <p className="text-overline">How we got this number</p>
          <SheetTitle className="text-title text-base">{title}</SheetTitle>
          {value != null && (
            <p className="stat-value-sm">{value}</p>
          )}
          <SheetDescription asChild>
            <div className="flex flex-wrap items-center gap-2 pt-1">
              {source && <SourceTag source={source} version={modelVersion} />}
              {hasConfidence && (
                <ConfidenceChip
                  value={evidence.confidence}
                  level={evidence.confidenceLevel}
                  thresholds={evidence.confidenceThresholds}
                  showValue={evidence.confidence != null}
                />
              )}
              {dataWindow && (
                <span className="text-caption">
                  Data from {formatDate(dataWindow.from)} to {formatDate(dataWindow.to)}
                </span>
              )}
            </div>
          </SheetDescription>
        </SheetHeader>

        <Section title="Method">
          <div className="text-body">{method}</div>
        </Section>

        {inputs && inputs.length > 0 && (
          <Section title={inputCount && inputCount > inputs.length ? `Inputs (${inputs.length} of ${inputCount})` : "Inputs"}>
            <ul className="divide-y divide-border">
              {inputs.map((input, i) => (
                <li key={i} className="flex items-baseline justify-between gap-4 py-2 text-sm">
                  {input.href ? (
                    <Link href={input.href} className="text-foreground underline-offset-4 hover:underline">
                      {input.label}
                    </Link>
                  ) : (
                    <span className="text-foreground">{input.label}</span>
                  )}
                  {input.value != null && (
                    <span className="shrink-0 tabular-nums text-muted-foreground">{input.value}</span>
                  )}
                </li>
              ))}
            </ul>
          </Section>
        )}

        {caveats && caveats.length > 0 && (
          <Section title="Keep in mind">
            <ul className="list-disc space-y-1 pl-4 text-supporting">
              {caveats.map((c, i) => (
                <li key={i}>{c}</li>
              ))}
            </ul>
          </Section>
        )}

        {children}

        {(footer || computedAt) && (
          <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-border px-6 py-4">
            {computedAt ? <span className="text-caption">Computed {formatDate(computedAt)}</span> : <span />}
            {footer}
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
