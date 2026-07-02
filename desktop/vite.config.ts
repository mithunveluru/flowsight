import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Tauri expects a fixed dev port and no auto-clearing of the terminal.
// `@`  -> the existing Next.js frontend, so its shared ui/* + signals + feature
//         types (which import via "@/...") resolve unchanged.
// `~`  -> this desktop app's own source.
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../frontend"),
      "~": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 1420,
    strictPort: true,
    watch: { ignored: ["**/src-tauri/**"] },
    // Allow serving the shared components imported from ../frontend (the dev
    // server otherwise restricts file access to this project's root).
    fs: { allow: [path.resolve(__dirname, "..")] },
  },
  // Produce a static bundle Tauri loads from ../dist in release builds.
  build: {
    outDir: "dist",
    target: "esnext",
    sourcemap: false,
  },
});
