import { create } from "zustand";
import { devtools } from "zustand/middleware";

interface AppState {
  // Phase 2+ adds: auth state, user profile
  // Phase 3+ adds: transaction state
  // Phase 6+ adds: behavioral insights state
}

export const useAppStore = create<AppState>()(
  devtools(
    () => ({}),
    { name: "flowsight" }
  )
);
