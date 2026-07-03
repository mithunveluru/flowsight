import { create } from "zustand";
import { devtools } from "zustand/middleware";

interface AppState {
}

export const useAppStore = create<AppState>()(
  devtools(
    () => ({}),
    { name: "flowsight" }
  )
);
