import { create } from "zustand";
import { AnimatePresence, motion } from "framer-motion";
import { Check, AlertCircle, Info } from "lucide-react";
import { EASE_OUT } from "@/components/motion/primitives";

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

const TONE_STYLE: Record<ToastTone, { color: string; soft: string; Icon: typeof Check }> = {
  success: { color: "var(--positive)", soft: "var(--positive-soft)", Icon: Check },
  error: { color: "var(--warning)", soft: "var(--warning-soft)", Icon: AlertCircle },
  info: { color: "var(--signal)", soft: "var(--signal-soft)", Icon: Info },
};

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-3 z-50 flex flex-col items-center gap-2 px-4">
      <AnimatePresence>
        {toasts.map((t) => {
          const { color, soft, Icon } = TONE_STYLE[t.tone];
          return (
            <motion.div
              key={t.id}
              initial={{ opacity: 0, y: 10, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 6, scale: 0.98 }}
              transition={{ duration: 0.2, ease: EASE_OUT }}
              className="pointer-events-auto flex items-center gap-2 rounded-lg bg-card px-3 py-2 text-xs font-medium shadow-card-hover"
              style={{ color: "hsl(var(--foreground))" }}
            >
              <span
                className="flex h-5 w-5 items-center justify-center rounded-full"
                style={{ backgroundColor: `hsl(${soft})`, color: `hsl(${color})` }}
              >
                <Icon className="h-3 w-3" strokeWidth={2.5} />
              </span>
              {t.message}
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
