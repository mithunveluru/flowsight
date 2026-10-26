"use client";

import * as React from "react";
import * as ToastPrimitive from "@radix-ui/react-toast";
import { AlertCircle, Check, Info, X } from "lucide-react";
import { cn } from "@/lib/utils";

type Tone = "info" | "success" | "error";

interface ToastItem {
  id: number;
  title: string;
  description?: string;
  tone: Tone;
}

let items: ToastItem[] = [];
let nextId = 0;
const listeners = new Set<() => void>();

function emit(next: ToastItem[]) {
  items = next;
  listeners.forEach((l) => l());
}

/** Show a toast from anywhere; <Toaster /> must be mounted once. */
export function toast(input: { title: string; description?: string; tone?: Tone }) {
  emit([...items, { id: ++nextId, tone: "info", ...input }]);
}

const TONE: Record<Tone, { icon: typeof Info; className: string }> = {
  info: { icon: Info, className: "text-primary" },
  success: { icon: Check, className: "text-positive" },
  error: { icon: AlertCircle, className: "text-warning" },
};

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export function Toaster() {
  const current = React.useSyncExternalStore(subscribe, () => items, () => items);
  const remove = (id: number) => emit(items.filter((t) => t.id !== id));

  return (
    <ToastPrimitive.Provider swipeDirection="right" duration={5000}>
      {current.map((t) => {
        const { icon: Icon, className } = TONE[t.tone];
        return (
          <ToastPrimitive.Root
            key={t.id}
            type={t.tone === "error" ? "foreground" : "background"}
            onOpenChange={(open) => !open && remove(t.id)}
            className={cn(
              "flex items-start gap-3 rounded-md border border-border bg-popover px-sp-wide py-sp-xl text-popover-foreground",
              "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:slide-in-from-right-8 data-[state=open]:slide-in-from-bottom-4 data-[state=open]:duration-slow",
              "data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:slide-out-to-right-8 data-[state=closed]:duration-fast",
              "data-[swipe=move]:translate-x-[var(--radix-toast-swipe-move-x)] data-[swipe=end]:translate-x-[var(--radix-toast-swipe-end-x)]",
              "motion-reduce:data-[state=open]:slide-in-from-right-0 motion-reduce:data-[state=open]:slide-in-from-bottom-0 motion-reduce:data-[state=closed]:slide-out-to-right-0"
            )}
          >
            <Icon className={cn("mt-0.5 h-4 w-4 shrink-0", className)} strokeWidth={2} aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <ToastPrimitive.Title className="text-sm font-medium text-ink">{t.title}</ToastPrimitive.Title>
              {t.description && (
                <ToastPrimitive.Description className="mt-0.5 text-xs text-muted-foreground">
                  {t.description}
                </ToastPrimitive.Description>
              )}
            </div>
            <ToastPrimitive.Close
              aria-label="Dismiss"
              className="-m-1.5 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors duration-fast hover:bg-band hover:text-ink"
            >
              <X className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        );
      })}
      <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] flex w-full max-w-sm flex-col gap-sp-sm p-4 outline-none" />
    </ToastPrimitive.Provider>
  );
}
