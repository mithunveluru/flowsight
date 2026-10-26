import { useEffect, useSyncExternalStore } from "react";
import { Check, AlertCircle, Info } from "lucide-react";
import { AnimatePresence, motion } from "@/components/motion/primitives";
import { drift, enterTransition } from "@/components/motion/tokens";
import { dismissToast, getToasts, subscribeToasts, toast as push, type ToastTone } from "@/components/ui/toast-store";
import { useReducedMotion } from "framer-motion";

const SHOWN_MS = 2800;

// imperative helper for non-component call sites
export const toast = {
  success: (title: string) => push({ title, tone: "success" }),
  error: (title: string) => push({ title, tone: "error" }),
  info: (title: string) => push({ title, tone: "info" }),
};

const TONE_STYLE: Record<ToastTone, { className: string; Icon: typeof Check }> = {
  success: { className: "text-positive", Icon: Check },
  error: { className: "text-warning", Icon: AlertCircle },
  info: { className: "text-primary", Icon: Info },
};

export function Toaster() {
  const toasts = useSyncExternalStore(subscribeToasts, getToasts, getToasts);
  const reduced = useReducedMotion();

  useEffect(() => {
    const timers = toasts.map((t) => setTimeout(() => dismissToast(t.id), SHOWN_MS));
    return () => timers.forEach(clearTimeout);
  }, [toasts]);
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
              {t.title}
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
