export type ToastTone = "info" | "success" | "error";

export interface ToastItem {
  id: number;
  title: string;
  description?: string;
  tone: ToastTone;
}

let items: ToastItem[] = [];
let nextId = 0;
const listeners = new Set<() => void>();

function emit(next: ToastItem[]) {
  items = next;
  listeners.forEach((l) => l());
}

/** Show a toast from anywhere; a toaster (web: ui/toast, desktop: its own) must be mounted once. */
export function toast(input: { title: string; description?: string; tone?: ToastTone }) {
  emit([...items, { id: ++nextId, tone: "info", ...input }]);
}

export const dismissToast = (id: number) => emit(items.filter((t) => t.id !== id));

export const subscribeToasts = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export const getToasts = () => items;
