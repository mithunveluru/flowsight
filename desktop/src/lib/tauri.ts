import { invoke } from "@tauri-apps/api/core";

// Thin wrappers over the app-defined Rust commands. Kept in one place so views
// never import the Tauri API directly.

/** Hide the popup window (Raycast-style dismiss; Escape / "done" actions). */
export function hideWindow(): Promise<void> {
  return invoke("hide_window");
}

/** Open the full web workspace in the user's default browser. */
export function openWorkspace(): Promise<void> {
  return invoke("open_workspace");
}
