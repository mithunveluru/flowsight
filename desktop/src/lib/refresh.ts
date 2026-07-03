import { create } from "zustand";

// Monotonic key bumped on focus and on an interval; views depend on it to re-fetch.
interface RefreshState {
  key: number;
  bump: () => void;
}

export const useRefresh = create<RefreshState>((set) => ({
  key: 0,
  bump: () => set((s) => ({ key: s.key + 1 })),
}));

export const bumpRefresh = () => useRefresh.getState().bump();
