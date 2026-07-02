import { create } from "zustand";
import { invoke } from "@tauri-apps/api/core";

// Widget   — compact always-on-top launcher (hides on blur in release).
// Expanded — a normal resizable desktop app window (taskbar, maximize, snap).
export type WindowMode = "widget" | "expanded";

interface WindowModeState {
  mode: WindowMode;
  setMode: (m: WindowMode) => void;
  toggle: () => void;
}

export const useWindowMode = create<WindowModeState>((set, get) => ({
  mode: "widget",
  setMode: (mode) => {
    set({ mode });
    void invoke("set_window_mode", { mode });
  },
  toggle: () => get().setMode(get().mode === "widget" ? "expanded" : "widget"),
}));

export const minimizeWindow = () => invoke("minimize_window");
export const toggleMaximize = () => invoke("toggle_maximize");
export const startResize = (direction: string) => invoke("start_resize", { direction });
