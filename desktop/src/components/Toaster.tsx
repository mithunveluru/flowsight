import { create } from "zustand";
import { Check, AlertCircle, Info } from "lucide-react";
import { AnimatePresence, motion } from "@/components/motion/primitives";
import { drift, enterTransition } from "@/components/motion/tokens";
import { useReducedMotion } from "framer-motion";

type ToastTone = "success" | "error" | "info";
interface Toast {
  id: number;
  message: string;
  tone: ToastTone;
}

interface ToastState {
  toasts: Toast[];
  push: (message: string, tone?: ToastTone) => void;
  dismiss: (id: number) => void;
}

let seq = 0;
const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (message, tone = "info") => {
    const id = ++seq;
    set((s) => ({ toasts: [...s.toasts, { id, message, tone }] }));
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 2800);
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

// imperative helper for non-component call sites
export const toast = {
  success: (m: string) => useToastStore.getState().push(m, "success"),
  error: (m: string) => useToastStore.getState().push(m, "error"),
  info: (m: string) => useToastStore.getState().push(m, "info"),
};

const TONE_STYLE: Record<ToastTone, { className: string; Icon: typeof Check }> = {
  success: { className: "text-positive", Icon: Check },
  error: { className: "text-warning", Icon: AlertCircle },
  info: { className: "text-primary", Icon: Info },
};

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const reduced = useReducedMotion();
  return (
    <div role="status" className="pointer-events-none absolute inset-x-0 bottom-3 z-50 flex flex-col items-center gap-2 px-4">
      <AnimatePresence>
        {toasts.map((t) => {
          const { className, Icon } = TONE_STYLE[t.tone];
          return (
            <motion.div
              key={t.id}
              initial={{ opacity: 0, y: drift(reduced, 10) }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: drift(reduced, 6) }}
              transition={enterTransition(reduced, 0.2)}
              className="pointer-events-auto flex items-center gap-2 rounded-md border border-border bg-popover px-3 py-2 text-xs font-medium text-popover-foreground"
            >
              <Icon className={`h-4 w-4 shrink-0 ${className}`} strokeWidth={2} aria-hidden="true" />
              {t.message}
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
