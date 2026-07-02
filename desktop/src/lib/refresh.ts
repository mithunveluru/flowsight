import { create } from "zustand";

// A monotonically increasing key bumped whenever the window regains focus (and
// on an interval while open). Views depend on it to re-fetch, giving the
// companion its "always current on show" behavior without a data-fetch library.
interface RefreshState {
  key: number;
  bump: () => void;
}

export const useRefresh = create<RefreshState>((set) => ({
  key: 0,
  bump: () => set((s) => ({ key: s.key + 1 })),
}));

export const bumpRefresh = () => useRefresh.getState().bump();
